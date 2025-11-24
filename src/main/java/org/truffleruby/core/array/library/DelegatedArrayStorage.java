/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array.library;

import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.library.ArrayStoreLibrary.ArrayAllocator;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(ArrayStoreLibrary.class)
@GenerateUncached
@ImportStatic(ArrayGuards.class)
public final class DelegatedArrayStorage implements ObjectGraphNode {

    public final Object storage;
    public final int offset;
    public final int length;

    public DelegatedArrayStorage(Object storage, int offset, int length) {
        assert offset >= 0;
        assert length >= 0;
        assert !(storage instanceof DelegatedArrayStorage);
        assert !(storage instanceof NativeArrayStorage);
        assert !(storage instanceof SharedArrayStorage);
        this.storage = storage;
        this.offset = offset;
        this.length = length;
    }

    @ExportMessage
    protected Object read(int index,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.read(storage, index + offset);
    }

    @ExportMessage
    protected boolean isPrimitive(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.isPrimitive(storage);
    }

    @ExportMessage
    public Object backingStore(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.backingStore(storage);
    }

    @ExportMessage
    protected Object makeShared(int size,
            @CachedLibrary("this") ArrayStoreLibrary library) {
        library.shareElements(this, 0, size);
        return new SharedArrayStorage(this);
    }

    @ExportMessage
    protected void shareElements(int start, int end,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        stores.shareElements(storage, offset + start, offset + end);
    }

    @ExportMessage
    @TruffleBoundary
    protected String toString(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return String.format("Delegate of (%s)", stores.toString(storage));
    }

    @ExportMessage
    protected int capacity() {
        return length;
    }

    @ExportMessage
    protected Object expand(int capacity) {
        return new DelegatedArrayStorage(storage, offset, capacity);
    }

    @ExportMessage
    protected Object extractRange(int start, int end) {
        return new DelegatedArrayStorage(storage, (offset + start), (end - start));
    }

    @ExportMessage
    protected Object[] boxedCopyOfRange(int start, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.boxedCopyOfRange(storage, offset + start, length);
    }

    @ExportMessage(limit = "storageStrategyLimit()")
    protected void copyContents(int srcStart, Object destStore, int destStart, int length,
            @Bind Node node,
            @Cached LoopConditionProfile loopProfile,
            @CachedLibrary("this.storage") ArrayStoreLibrary srcStores,
            @CachedLibrary("destStore") ArrayStoreLibrary destStores) {
        int i = 0;
        try {
            for (; loopProfile.inject(i < length); i++) {
                destStores.write(destStore, i + destStart, srcStores.read(storage, srcStart + offset + i));
                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
    }

    @ExportMessage
    protected void clear(int start, int length) {
    }

    @ExportMessage
    protected Object toJavaArrayCopy(int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        Object newStore = stores.unsharedAllocator(storage).allocate(length);
        stores.copyContents(storage, offset, newStore, 0, length);
        return newStore;
    }

    @ExportMessage
    protected void sort(int size) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    @ExportMessage
    protected Iterable<Object> getIterable(int from, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.getIterable(storage, from + offset, length);
    }

    @ExportMessage
    protected ArrayAllocator generalizeForValue(Object newValue,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.generalizeForValue(storage, newValue);
    }

    @ExportMessage
    protected ArrayAllocator generalizeForStore(Object newStore,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.generalizeForStore(storage, newStore);
    }

    @ExportMessage
    public ArrayAllocator generalizeForSharing(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.generalizeForSharing(storage);
    }

    @ExportMessage
    protected Object allocateForNewValue(Object newValue, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.allocateForNewValue(storage, newValue, length);
    }

    @ExportMessage
    protected Object allocateForNewStore(Object newStore, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.allocateForNewStore(storage, newStore, length);
    }

    @ExportMessage
    protected boolean isDefaultValue(Object value,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.isDefaultValue(storage, value);
    }

    @ExportMessage
    protected ArrayAllocator allocator(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.unsharedAllocator(storage);
    }

    public boolean hasObjectArrayStorage() {
        return storage.getClass() == Object[].class;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        if (hasObjectArrayStorage()) {
            final Object[] objectArray = (Object[]) storage;

            for (int i = offset; i < offset + length; i++) {
                final Object value = objectArray[i];
                if (ObjectGraph.isRubyObject(value)) {
                    reachable.add(value);
                }
            }
        }
    }

}
