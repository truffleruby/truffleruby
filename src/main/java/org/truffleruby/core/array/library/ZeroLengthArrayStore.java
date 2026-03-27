/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.core.array.library;

import java.util.Collections;

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

@ExportLibrary(ArrayStoreLibrary.class)
@GenerateUncached
public final class ZeroLengthArrayStore {

    private ZeroLengthArrayStore() {
    }

    public static final ZeroLengthArrayStore ZERO_LENGTH_STORE = new ZeroLengthArrayStore();

    @ExportMessage
    @TruffleBoundary
    protected Object read(int index) {
        throw new UnsupportedOperationException();
    }

    @ExportMessage
    protected boolean isPrimitive() {
        return true;
    }

    @ExportMessage
    static String toString(ZeroLengthArrayStore store) {
        return "empty";
    }

    @ExportMessage
    protected int capacity() {
        return 0;
    }

    @ExportMessage
    protected Object[] expand(int newCapacity) {
        return new Object[newCapacity];
    }

    @ExportMessage
    protected Object extractRange(int start, int end) {
        assert start == 0;
        assert end == 0;
        return this;
    }

    @ExportMessage
    protected Object[] boxedCopyOfRange(int start, int length) {
        assert start == 0;
        assert length == 0;

        return ArrayUtils.EMPTY_ARRAY;
    }

    @ExportMessage
    protected void copyContents(int srcStart, Object destStore, int destStart, int length) {
        assert srcStart == 0;
        assert length == 0;
    }

    @ExportMessage
    protected void clear(int start, int length) {
    }

    @ExportMessage
    protected Object[] toJavaArrayCopy(int length) {
        assert length == 0;
        return new Object[length];
    }

    @ExportMessage
    protected void sort(int size) {
        assert size == 0;
    }

    @ExportMessage
    protected Iterable<Object> getIterable(int from, int length) {
        return () -> Collections.emptyIterator();
    }

    @ExportMessage
    static final class GeneralizeForValue {

        @Specialization
        static ArrayAllocator generalize(ZeroLengthArrayStore store, int newValue) {
            return IntegerArrayStore.INTEGER_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(ZeroLengthArrayStore store, long newValue) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        }

        @Specialization
        static ArrayAllocator generalize(ZeroLengthArrayStore store, double newValue) {
            return DoubleArrayStore.DOUBLE_ARRAY_ALLOCATOR;
        }

        @Fallback
        static ArrayAllocator generalize(ZeroLengthArrayStore store, Object newValue) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }
    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class GeneralizeForStore {

        @Specialization
        static ArrayAllocator generalize(ZeroLengthArrayStore store, ZeroLengthArrayStore newStore) {
            return ZERO_LENGTH_ALLOCATOR;
        }

        @Specialization(limit = "storageStrategyLimit()")
        static ArrayAllocator generalize(ZeroLengthArrayStore store, Object newStore,
                @CachedLibrary("newStore") ArrayStoreLibrary newStores) {
            return newStores.allocator(newStore);
        }
    }

    @ExportMessage
    static ArrayAllocator generalizeForSharing(ZeroLengthArrayStore store) {
        return SharedArrayStorage.SHARED_ZERO_LENGTH_ARRAY_ALLOCATOR;
    }

    @ExportMessage
    static final class AllocateForNewValue {

        @Specialization
        static Object allocateForNewValue(ZeroLengthArrayStore store, int newValue, int length) {
            return IntegerArrayStore.INTEGER_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocateForNewValue(ZeroLengthArrayStore store, long newValue, int length) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR.allocate(length);
        }

        @Specialization
        static Object allocateForNewValue(ZeroLengthArrayStore store, double newValue, int length) {
            return DoubleArrayStore.DOUBLE_ARRAY_ALLOCATOR.allocate(length);
        }

        @Fallback
        static Object allocateForNewValue(ZeroLengthArrayStore store, Object newValue, int length) {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR.allocate(length);
        }

    }

    @ExportMessage
    @ImportStatic(ArrayGuards.class)
    static final class AllocateForNewStore {

        @Specialization
        static Object allocateForNewStore(ZeroLengthArrayStore store, ZeroLengthArrayStore newStore, int length) {
            return ZERO_LENGTH_ALLOCATOR.allocate(length);
        }

        @Specialization(guards = "!zeroLengthStore(newStore)", limit = "storageStrategyLimit()")
        static Object allocateForNewStore(ZeroLengthArrayStore store, Object newStore, int length,
                @CachedLibrary("newStore") ArrayStoreLibrary newStores) {
            // We have to be careful here in case newStore is a a
            // wrapped version of the zero length store, and we don't
            // want to end up recursing back to this case repeatedly.
            return newStores.unsharedAllocateForNewStore(newStore, store, length);
        }

        protected static boolean zeroLengthStore(Object store) {
            return store instanceof ZeroLengthArrayStore;
        }
    }

    @ExportMessage
    protected boolean isDefaultValue(Object value) {
        return false;
    }

    @ExportMessage
    static ArrayAllocator allocator(ZeroLengthArrayStore store) {
        return ZERO_LENGTH_ALLOCATOR;
    }

    static final ArrayAllocator ZERO_LENGTH_ALLOCATOR = new ZeroLengthAllocator();

    static final class ZeroLengthAllocator extends ArrayAllocator {

        @Override
        public Object allocate(int capacity) {
            assert capacity == 0;
            return ZERO_LENGTH_STORE;
        }

        @Override
        public boolean accepts(Object value) {
            return false;
        }

        @Override
        public boolean specializesFor(Object value) {
            return false;
        }

        @Override
        public boolean isDefaultValue(Object value) {
            return value == null;
        }
    }
}
