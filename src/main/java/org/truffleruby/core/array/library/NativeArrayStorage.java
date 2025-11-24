/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array.library;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.cext.UnwrapNode;
import org.truffleruby.cext.UnwrapNode.UnwrapNativeNode;
import org.truffleruby.cext.ValueWrapper;
import org.truffleruby.cext.WrapNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.library.ArrayStoreLibrary.ArrayAllocator;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

@ExportLibrary(ArrayStoreLibrary.class)
@GenerateUncached
@ImportStatic(ArrayGuards.class)
public final class NativeArrayStorage implements ObjectGraphNode {

    private final Pointer pointer;
    /** Used to keep elements alive */
    private final Object[] markedObjects;
    public final int length;

    public NativeArrayStorage(Pointer pointer, int length) {
        this(pointer, length, new Object[length]);
    }

    private NativeArrayStorage(Pointer pointer, int length, Object[] markedObjects) {
        this.pointer = pointer;
        this.length = length;
        this.markedObjects = markedObjects;
    }

    @ExportMessage
    protected boolean acceptsValue(Object value) {
        return true;
    }

    @ExportMessage
    protected boolean acceptsAllValues(Object otherStore) {
        return true;
    }

    @ExportMessage
    protected boolean isMutable() {
        return true;
    }

    @ExportMessage
    protected boolean isNative() {
        return true;
    }

    @ExportMessage
    static String toString(NativeArrayStorage storage) {
        return "NativeArrayStorage";
    }

    @ExportMessage
    protected Object read(int index,
            @Cached @Shared UnwrapNode unwrapNode,
            @Bind Node node) {
        return unwrapNode.execute(node, readElement(index));
    }

    @ExportMessage
    protected void write(int index, Object value,
            @CachedLibrary(limit = "1") InteropLibrary wrappers,
            @Cached WrapNode wrapNode,
            @Cached InlinedConditionProfile isPointerProfile,
            @Bind Node node) {
        final ValueWrapper wrapper = wrapNode.execute(value);
        if (!isPointerProfile.profile(node, wrappers.isPointer(wrapper))) {
            wrappers.toNative(wrapper);
        }

        final long address;
        try {
            assert wrappers.isPointer(wrapper);
            address = wrappers.asPointer(wrapper);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedOperationException();
        }

        writeElement(index, address);
    }

    @ExportMessage
    protected int capacity() {
        return length;
    }

    @ExportMessage
    protected NativeArrayStorage expand(int newCapacity,
            @Bind Node node) {
        final int capacity = this.length;
        Pointer newPointer = Pointer.malloc(RubyContext.get(node), capacity);
        newPointer.writeBytes(0, pointer, 0, capacity);
        newPointer.writeBytes(capacity, newCapacity - capacity, (byte) 0);
        /* We copy the contents of the marked objects to ensure the references will be kept alive even if the old store
         * becomes unreachable. */
        Object[] newMarkedObjects = ArrayUtils.grow(markedObjects, newCapacity);
        return new NativeArrayStorage(newPointer, newCapacity, newMarkedObjects);
    }

    @ExportMessage
    protected Object[] boxedCopyOfRange(int start, int length,
            @Cached @Shared UnwrapNode unwrapNode,
            @Bind Node node) {
        Object[] newStore = new Object[length];
        for (int i = 0; i < length; i++) {
            newStore[i] = unwrapNode.execute(node, readElement(start + i));
        }
        return newStore;
    }

    @ExportMessage
    static Object makeShared(NativeArrayStorage store, int size,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        stores.shareElements(store, 0, size);
        return new SharedArrayStorage(store);
    }

    @ExportMessage
    static final class ShareElements {

        @Specialization
        static void shareElements(NativeArrayStorage store, int start, int end,
                @CachedLibrary("store") ArrayStoreLibrary arrayStoreLibrary,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @Cached WriteBarrierNode writeBarrierNode,
                @Bind Node node) {
            int i = start;
            try {
                for (; loopProfile.inject(i < end); i++) {
                    writeBarrierNode.execute(node, arrayStoreLibrary.read(store, i));
                }
            } finally {
                RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
            }
        }
    }

    @ExportMessage(limit = "storageStrategyLimit()")
    protected void copyContents(int srcStart, Object destStore, int destStart, int length,
            @Bind Node node,
            @CachedLibrary("this") ArrayStoreLibrary srcStores,
            @Cached @Exclusive LoopConditionProfile loopProfile,
            @CachedLibrary("destStore") ArrayStoreLibrary destStores) {
        int i = 0;
        try {
            for (; loopProfile.inject(i < length); i++) {
                destStores.write(destStore, destStart + i, srcStores.read(this, srcStart + i));
                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
    }

    @ExportMessage
    protected void clear(int start, int length) {
        pointer.writeBytes(start * Pointer.SIZE, length * Pointer.SIZE, (byte) 0);
    }

    @ExportMessage
    static void fill(NativeArrayStorage store, int start, int length, Object value,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        for (int i = start; i < length; ++i) {
            stores.write(store, i, value);
        }
    }

    @ExportMessage
    protected Object[] toJavaArrayCopy(int size,
            @Cached @Shared UnwrapNode unwrapNode,
            @Bind Node node) {
        Object[] newStore = new Object[size];
        assert size >= length;
        for (int i = 0; i < length; i++) {
            newStore[i] = unwrapNode.execute(node, readElement(i));
        }
        return newStore;
    }

    @ExportMessage
    static Iterable<Object> getIterable(NativeArrayStorage store, int from, int length,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        return () -> new Iterator<>() {

            private int n = from;

            @Override
            public boolean hasNext() {
                return n < from + length;
            }

            @Override
            public Object next() throws NoSuchElementException {
                if (n >= from + length) {
                    throw new NoSuchElementException();
                }

                final Object object = stores.read(store, n);
                n++;
                return object;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

        };
    }

    @ExportMessage
    protected boolean isDefaultValue(Object value) {
        return false;
    }

    @ExportMessage
    protected ArrayAllocator allocator() {
        return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    protected ArrayAllocator generalizeForValue(Object newValue) {
        return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    protected ArrayAllocator generalizeForStore(Object newStore) {
        return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    public ArrayAllocator generalizeForSharing() {
        return SharedArrayStorage.SHARED_OBJECT_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    protected Object allocateForNewValue(Object newValue, int length) {
        return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
    }

    @ExportMessage
    protected Object allocateForNewStore(Object newValue, int length) {
        return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
    }

    public long readElement(int index) {
        return pointer.readLong(index * Pointer.SIZE);
    }

    public void writeElement(int index, long value) {
        pointer.writeLong(index * Pointer.SIZE, value);
    }

    public long getAddress() {
        return pointer.getAddress();
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        for (int i = 0; i < length; i++) {
            final Object value = UnwrapNativeNode.executeUncached(readElement(i));
            if (ObjectGraph.isRubyObject(value)) {
                reachable.add(value);
            }
        }
    }

    @TruffleBoundary
    public void preserveMembers() {
        for (int i = 0; i < length; i++) {
            final Object value = UnwrapNativeNode.executeUncached(readElement(i));
            markedObjects[i] = value;
        }
    }
}
