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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.library.ArrayStoreLibrary.ArrayAllocator;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

@ExportLibrary(ArrayStoreLibrary.class)
@GenerateUncached
@ImportStatic(ArrayGuards.class)
public final class SharedArrayStorage implements ObjectGraphNode {

    public final Object storage;

    public SharedArrayStorage(Object storage) {
        assert !(storage instanceof SharedArrayStorage);
        this.storage = storage;
    }

    /* Method for checking that all elements in this array are correctly shared. This can only be called after the whole
     * stack of adjacent objects have been shared, which may not be true at the point the storage is converted to shared
     * storage. */
    @TruffleBoundary
    public boolean allElementsShared(int size) {
        if (storage == null || storage instanceof ZeroLengthArrayStore) {
            return true;
        }
        ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached(storage);
        var elements = stores.getIterable(storage, 0, size);
        for (var e : elements) {
            if (e == null || !(e instanceof RubyDynamicObject) || SharedObjects.isShared(e)) {
                continue;
            } else {
                assert false : String.format("Unshared element %s.%n", e);
                return false;
            }
        }
        return true;
    }

    @ExportMessage
    protected Object read(int index,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.read(storage, index);
    }

    @ExportMessage
    protected void write(int index, Object value,
            @Cached @Shared WriteBarrierNode writeBarrierNode,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores,
            @Bind Node node) {
        writeBarrierNode.execute(node, value);
        stores.write(storage, index, value);
    }

    @ExportMessage
    protected void fill(int start, int length, Object value,
            @Cached @Shared WriteBarrierNode writeBarrierNode,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores,
            @Bind Node node) {
        writeBarrierNode.execute(node, value);
        stores.fill(storage, start, length, value);
    }

    @ExportMessage
    protected boolean acceptsValue(Object value,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.acceptsValue(storage, value);
    }

    @ExportMessage
    protected boolean acceptsAllValues(Object otherStore,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.acceptsAllValues(storage, otherStore);
    }

    @ExportMessage
    protected boolean isMutable(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.isMutable(storage);
    }

    @ExportMessage
    protected boolean isNative(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.isNative(storage);
    }

    @ExportMessage
    protected boolean isPrimitive(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.isPrimitive(storage);
    }

    @ExportMessage
    public boolean isShared() {
        return true;
    }

    @ExportMessage
    public Object initialStore() {
        return ArrayStoreLibrary.initialStorage(true);
    }

    @ExportMessage
    public Object backingStore(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.backingStore(storage);
    }

    @ExportMessage
    public Object makeShared(int size) {
        return this;
    }

    @ExportMessage
    @TruffleBoundary
    protected String toString(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return String.format("Shared storage of (%s)", stores.toString(storage));
    }

    @ExportMessage
    protected int capacity(
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.capacity(storage);
    }

    @ExportMessage
    protected Object expand(int capacity,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return new SharedArrayStorage(stores.expand(storage, capacity));
    }

    @ExportMessage
    protected Object extractRange(int start, int end,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return new SharedArrayStorage(stores.extractRange(storage, start, end));
    }

    @ExportMessage
    protected Object extractRangeAndUnshare(int start, int end,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.extractRange(storage, start, end);
    }

    @ExportMessage
    protected Object[] boxedCopyOfRange(int start, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.boxedCopyOfRange(storage, start, length);
    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class CopyContents {

        @Specialization(guards = "srcStore == destStore")
        static void copyContents(
                SharedArrayStorage srcStore, int srcStart, SharedArrayStorage destStore, int destStart, int length) {
            System.arraycopy(srcStore.storage, srcStart, destStore.storage, destStart, length);
        }

        @Specialization(guards = "differentStores(srcStore, destStore)", limit = "storageStrategyLimit()")
        static void copyContents(SharedArrayStorage srcStore, int srcStart, Object destStore, int destStart, int length,
                @Bind Node node,
                @Cached @Exclusive LoopConditionProfile loopProfile,
                @CachedLibrary("srcStore.storage") ArrayStoreLibrary srcStores,
                @CachedLibrary("destStore") ArrayStoreLibrary destStores) {
            int i = 0;
            try {
                for (; loopProfile.inject(i < length); i++) {
                    destStores.write(destStore, destStart + i, srcStore.read(srcStart + i, srcStores));
                    TruffleSafepoint.poll(node);
                }
            } finally {
                RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
            }
        }

        protected static boolean differentStores(SharedArrayStorage srcStore, Object destStore) {
            return srcStore != destStore;
        }
    }

    @ExportMessage
    protected void clear(int start, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        stores.clear(storage, start, length);
    }

    @ExportMessage
    protected Object toJavaArrayCopy(int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.toJavaArrayCopy(storage, length);
    }

    @ExportMessage
    protected void sort(int size,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        stores.sort(storage, size);
    }

    @ExportMessage
    protected Iterable<Object> getIterable(int from, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return stores.getIterable(storage, from, length);
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
        return new SharedArrayStorage(stores.allocateForNewValue(storage, newValue, length));
    }

    @ExportMessage
    protected Object allocateForNewStore(Object newStore, int length,
            @CachedLibrary("this.storage") ArrayStoreLibrary stores) {
        return new SharedArrayStorage(stores.allocateForNewStore(storage, newStore, length));
    }

    @ExportMessage
    protected Object unsharedAllocateForNewStore(Object newStore, int length,
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
        return new SharedArrayAllocator(stores.unsharedAllocator(storage));
    }

    @ExportMessage
    protected ArrayAllocator unsharedAllocator(
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

            for (int i = 0; i < objectArray.length; i++) {
                final Object value = objectArray[i];
                if (ObjectGraph.isRubyObject(value)) {
                    reachable.add(value);
                }
            }
        } else if (storage instanceof ObjectGraphNode) {
            ((ObjectGraphNode) storage).getAdjacentObjects(reachable);
        }
    }

    static final ArrayAllocator SHARED_ZERO_LENGTH_ARRAY_ALLOCATOR = new SharedArrayAllocator(
            ZeroLengthArrayStore.ZERO_LENGTH_ALLOCATOR);
    static final ArrayAllocator SHARED_INTEGER_ARRAY_ALLOCATOR = new SharedArrayAllocator(
            IntegerArrayStore.INTEGER_ARRAY_ALLOCATOR);
    static final ArrayAllocator SHARED_LONG_ARRAY_ALLOCATOR = new SharedArrayAllocator(
            LongArrayStore.LONG_ARRAY_ALLOCATOR);
    static final ArrayAllocator SHARED_DOUBLE_ARRAY_ALLOCATOR = new SharedArrayAllocator(
            DoubleArrayStore.DOUBLE_ARRAY_ALLOCATOR);
    static final ArrayAllocator SHARED_OBJECT_ARRAY_ALLOCATOR = new SharedArrayAllocator(
            ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR);

    private static final class SharedArrayAllocator extends ArrayAllocator {

        private final ArrayAllocator storageAllocator;

        SharedArrayAllocator(ArrayAllocator storageAllocator) {
            this.storageAllocator = storageAllocator;
        }

        @Override
        public SharedArrayStorage allocate(int capacity) {
            return new SharedArrayStorage(storageAllocator.allocate(capacity));
        }

        @Override
        public boolean accepts(Object value) {
            return storageAllocator.accepts(value);
        }

        @Override
        public boolean specializesFor(Object value) {
            return storageAllocator.specializesFor(value);
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return storageAllocator.isDefaultValue(value);
        }
    }
}
