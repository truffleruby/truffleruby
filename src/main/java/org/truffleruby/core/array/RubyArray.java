/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

import org.truffleruby.core.array.library.SharedArrayStorage;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public final class RubyArray extends RubyDynamicObject implements ObjectGraphNode {

    private Object store;
    public int size;

    public RubyArray(RubyClass rubyClass, Shape shape, Object store, int size) {
        super(rubyClass, shape);
        this.store = store;
        this.size = size;
    }

    public Object getStore() {
        return store;
    }

    public void setStore(Object store) {
        assert (store instanceof SharedArrayStorage) == (getShape().isShared());
        this.store = store;
    }

    public void setStoreAndSize(Object store, int size) {
        assert (store instanceof SharedArrayStorage) == (this.getShape().isShared());
        this.setStore(store);
        setSize(size);
    }

    /** Sets the size of the given array
     *
     * Asserts that the size is valid for the current store of the array. If setting both size and store, use
     * {@link #setStoreAndSize} or be sure to {@link #setStore} before {@link #setSize} as this assertion may fail. */
    public void setSize(int size) {
        assert ArrayOperations.getStoreCapacity(this) >= size;
        this.size = size;
    }

    @TruffleBoundary
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, store);
    }

    // region Array elements
    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public long getArraySize() {
        return size;
    }

    @ExportMessage
    public Object readArrayElement(long index,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive DispatchNode dispatch) throws InvalidArrayIndexException {
        if (inBounds(index)) {
            // FIXME (pitr 11-Feb-2020): use ArrayReadNormalizedNode
            // @Cached ArrayReadNormalizedNode readNode
            // return readNode.executeRead(this, (int) index);
            return dispatch.call(this, "[]", index);
        } else {
            errorProfile.enter();
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive DispatchNode dispatch) throws InvalidArrayIndexException {
        if (index >= 0 && RubyGuards.fitsInInteger(index)) {
            // FIXME (pitr 11-Feb-2020): use ArrayWriteNormalizedNode
            // @Cached ArrayWriteNormalizedNode writeNode
            // writeNode.executeWrite(this, (int) index, value);
            dispatch.call(this, "[]=", index, value);
        } else {
            errorProfile.enter();
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    public void removeArrayElement(long index,
            @Cached @Exclusive DispatchNode dispatch,
            @Cached @Shared BranchProfile errorProfile) throws InvalidArrayIndexException {
        if (inBounds(index)) {
            // FIXME (pitr 11-Feb-2020): use delete-at node directly
            // @Cached ArrayNodes.DeleteAtNode deleteAtNode
            // deleteAtNode.executeDeleteAt(this, (int) index);
            dispatch.call(this, "delete_at", index);
        } else {
            errorProfile.enter();
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        return inBounds(index);
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
            @Cached @Shared IsFrozenNode isFrozenNode) {
        return !isFrozenNode.execute(this) && inBounds(index);
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
            @Cached @Shared IsFrozenNode isFrozenNode) {
        return !isFrozenNode.execute(this) && inBounds(index);
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
            @Cached @Shared IsFrozenNode isFrozenNode) {
        return !isFrozenNode.execute(this) && RubyGuards.fitsInInteger(index) && index >= size;
    }
    // endregion

    // region Iterable Messages
    @ExportMessage
    public boolean hasIterator() {
        return true;
    }

    /** Override {@link RubyDynamicObject#getIterator} to avoid the extra Fiber for RubyArray */
    @ExportMessage
    public ArrayIterator getIterator() {
        return new ArrayIterator(this);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ArrayIterator implements TruffleObject {

        final RubyArray array;
        private long currentItemIndex;

        ArrayIterator(RubyArray array) {
            this.array = array;
        }

        @ExportMessage
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement(
                @CachedLibrary("this.array") InteropLibrary arrays) {
            try {
                return currentItemIndex < arrays.getArraySize(array);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        Object getIteratorNextElement(
                @CachedLibrary("this.array") InteropLibrary arrays,
                @Cached BranchProfile concurrentModification) throws StopIterationException {
            try {
                final long size = arrays.getArraySize(array);
                if (currentItemIndex >= size) {
                    throw StopIterationException.create();
                }

                final Object element = arrays.readArrayElement(array, currentItemIndex);
                currentItemIndex++;
                return element;
            } catch (InvalidArrayIndexException e) {
                concurrentModification.enter();
                throw StopIterationException.create();
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }
    // endregion

    private boolean inBounds(long index) {
        return index >= 0 && index < size;
    }

}
