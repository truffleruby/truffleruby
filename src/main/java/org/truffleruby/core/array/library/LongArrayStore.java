/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.core.array.library;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.library.ArrayStoreLibrary.ArrayAllocator;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.language.RubyBaseNode;

@ExportLibrary(value = ArrayStoreLibrary.class, receiverType = long[].class)
@GenerateUncached
public final class LongArrayStore {

    @ExportMessage
    static long read(long[] store, int index) {
        return store[index];
    }

    @ExportMessage
    static boolean acceptsValue(long[] store, Object value) {
        return value instanceof Long || value instanceof Integer;
    }

    @ExportMessage
    static final class AcceptsAllValues {

        @Specialization
        static boolean acceptsZeroValues(long[] store, ZeroLengthArrayStore otherStore) {
            return true;
        }

        @Specialization
        static boolean acceptsIntValues(long[] store, int[] otherStore) {
            return true;
        }

        @Specialization
        static boolean acceptsLongValues(long[] store, long[] otherStore) {
            return true;
        }

        @Specialization
        static boolean acceptsDelegateValues(long[] store, DelegatedArrayStorage otherStore) {
            return otherStore.storage instanceof int[] || otherStore.storage instanceof long[];
        }

        @Specialization
        static boolean acceptsSharedValues(long[] store, SharedArrayStorage otherStore,
                @CachedLibrary(limit = "1") ArrayStoreLibrary stores) {
            return stores.acceptsAllValues(store, otherStore.storage);
        }

        @Fallback
        static boolean acceptsOtherValues(long[] store, Object otherStore) {
            return false;
        }
    }

    @ExportMessage
    static boolean isMutable(long[] store) {
        return true;
    }

    @ExportMessage
    static boolean isPrimitive(long[] store) {
        return true;
    }

    @ExportMessage
    static String toString(long[] store) {
        return "long[]";
    }

    @ExportMessage
    static final class Write {
        @Specialization
        static void write(long[] store, int index, int value) {
            store[index] = value;
        }

        @Specialization
        static void write(long[] store, int index, long value) {
            store[index] = value;
        }
    }

    @ExportMessage
    static int capacity(long[] store) {
        return store.length;
    }

    @ExportMessage
    static long[] expand(long[] store, int newCapacity) {
        return ArrayUtils.grow(store, newCapacity);
    }

    @ExportMessage
    static Object[] boxedCopyOfRange(long[] store, int start, int length) {
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++) {
            result[i] = store[start + i];
        }
        return result;
    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class CopyContents {

        @Specialization
        static void copyContents(long[] srcStore, int srcStart, long[] destStore, int destStart, int length) {
            System.arraycopy(srcStore, srcStart, destStore, destStart, length);
        }

        @Specialization(guards = "!isLongStore(destStore)", limit = "storageStrategyLimit()")
        static void copyContents(long[] srcStore, int srcStart, Object destStore, int destStart, int length,
                @Bind Node node,
                @Cached LoopConditionProfile loopProfile,
                @CachedLibrary("destStore") ArrayStoreLibrary destStores) {
            int i = 0;
            try {
                for (; loopProfile.inject(i < length); i++) {
                    destStores.write(destStore, destStart + i, srcStore[srcStart + i]);
                    TruffleSafepoint.poll(node);
                }
            } finally {
                RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
            }
        }

        protected static boolean isLongStore(Object store) {
            return store instanceof long[];
        }
    }

    @ExportMessage
    static void clear(long[] store, int start, int length) {
    }

    @ExportMessage
    static final class Fill {
        @Specialization
        static void fill(long[] store, int start, int length, int value) {
            Arrays.fill(store, start, start + length, value);
        }

        @Specialization
        static void write(long[] store, int start, int length, long value) {
            Arrays.fill(store, start, start + length, value);
        }
    }

    @ExportMessage
    static long[] toJavaArrayCopy(long[] store, int length) {
        return ArrayUtils.extractRange(store, 0, length);
    }

    @ExportMessage
    @TruffleBoundary
    static void sort(long[] store, int size) {
        Arrays.sort(store, 0, size);
    }

    @ExportMessage
    static Iterable<Object> getIterable(long[] store, int from, int length) {
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

                final Object object = store[n];
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
    static final class GeneralizeForValue {

        @Specialization
        static ArrayAllocator generalize(long[] store, int newValue) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(long[] store, long newValue) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(long[] store, double newValue) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }

        @Fallback
        static ArrayAllocator generalize(long[] store, Object newValue) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }
    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class GeneralizeForStore {

        @Specialization
        static ArrayAllocator generalize(long[] store, int[] newStore) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(long[] store, long[] newStore) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(long[] store, double[] newStore) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(long[] store, Object[] newStore) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }

        @Specialization(limit = "storageStrategyLimit()")
        static ArrayAllocator generalize(long[] store, Object newStore,
                @CachedLibrary("newStore") ArrayStoreLibrary newStores) {
            return newStores.generalizeForStore(newStore, store);
        }
    }

    @ExportMessage
    static final class AllocateForNewValue {

        @Specialization
        static Object allocateForNewStore(long[] store, int newValue, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocateForNewStore(long[] store, long newValue, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocateForNewStore(long[] store, double newValue, int length) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
        }

        @Fallback
        static Object allocateForNewStore(long[] store, Object newValue, int length) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
        }
    }

    @ExportMessage
    static ArrayAllocator generalizeForSharing(long[] store) {
        return SharedArrayStorage.SHARED_LONG_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class AllocateForNewStore {

        @Specialization
        static Object allocate(long[] store, ZeroLengthArrayStore newStore, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocate(long[] store, int[] newStore, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocate(long[] store, long[] newStore, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocate(long[] store, double[] newStore, int length) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocate(long[] store, Object[] newStore, int length) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization(guards = { "!basicStore(newStore)", "!zeroLengthStore(newStore)" },
                limit = "storageStrategyLimit()")
        static Object allocate(long[] store, Object newStore, int length,
                @CachedLibrary("newStore") ArrayStoreLibrary newStores) {
            return newStores.unsharedAllocateForNewStore(newStore, store, length);
        }
    }

    @ExportMessage
    protected static final class IsDefaultValue {

        @Specialization
        static boolean isDefaultValue(long[] store, long value) {
            return value == 0L;
        }

        @Fallback
        static boolean isDefaultValue(long[] store, Object value) {
            return false;
        }
    }

    @ExportMessage
    static ArrayAllocator allocator(long[] store) {
        return LONG_ARRAY_ALLOCATOR;
    }

    public static final ArrayAllocator LONG_ARRAY_ALLOCATOR = new LongArrayAllocator();

    private static final class LongArrayAllocator extends ArrayAllocator {

        @Override
        public long[] allocate(int capacity) {
            return new long[capacity];
        }

        @Override
        public boolean accepts(Object value) {
            return value instanceof Integer || value instanceof Long;
        }

        @Override
        public boolean specializesFor(Object value) {
            return value instanceof Long;
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return (long) value == 0;
        }
    }
}
