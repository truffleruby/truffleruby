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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;

/** Library for accessing and manipulating the storage used for representing arrays. This includes reading, modifying,
 * and copy the storage. */
@GenerateLibrary
@DefaultExport(IntegerArrayStore.class)
@DefaultExport(LongArrayStore.class)
@DefaultExport(DoubleArrayStore.class)
@DefaultExport(ObjectArrayStore.class)
public abstract class ArrayStoreLibrary extends Library {

    /** An initial immutable empty array store. This is what should be assigned initially to an array of zero size. */
    private static final Object INITIAL_STORE = ZeroLengthArrayStore.ZERO_LENGTH_STORE;
    private static final ArrayAllocator INITIAL_ALLOCATOR = ZeroLengthArrayStore.ZERO_LENGTH_ALLOCATOR;

    public static final Object SHARED_INITIAL_STORE = new SharedArrayStorage(ZeroLengthArrayStore.ZERO_LENGTH_STORE);
    private static final ArrayAllocator SHARED_INITIAL_ALLOCATOR = SharedArrayStorage.SHARED_ZERO_LENGTH_ARRAY_ALLOCATOR;

    private static final LibraryFactory<ArrayStoreLibrary> FACTORY = LibraryFactory.resolve(ArrayStoreLibrary.class);
    private static final ArrayStoreLibrary UNCACHED = FACTORY.getUncached();

    public static ArrayStoreLibrary createDispatched(RubyLanguage language) {
        return FACTORY.createDispatched(language.options.ARRAY_STRATEGY_CACHE);
    }

    public static ArrayStoreLibrary create(Object store) {
        return FACTORY.create(store);
    }

    @SuppressFBWarnings("MS_EXPOSE_REP")
    public static ArrayStoreLibrary getUncached() {
        CompilerAsserts.neverPartOfCompilation("uncached libraries must not be used in PE code");
        return UNCACHED;
    }

    public static ArrayStoreLibrary getUncached(Object store) {
        CompilerAsserts.neverPartOfCompilation("uncached libraries must not be used in PE code");
        return FACTORY.getUncached(store);
    }

    public static Object initialStorage(boolean shared) {
        if (shared) {
            return SHARED_INITIAL_STORE;
        } else {
            return INITIAL_STORE;
        }
    }

    public static ArrayAllocator initialAllocator(boolean shared) {
        if (shared) {
            return SHARED_INITIAL_ALLOCATOR;
        } else {
            return INITIAL_ALLOCATOR;
        }
    }

    /** Return an allocator for storage that can hold {@code value}. */
    public static ArrayAllocator allocatorForValue(Object value) {
        if (value instanceof Integer) {
            return IntegerArrayStore.INTEGER_ARRAY_ALLOCATOR;
        } else if (value instanceof Long) {
            return LongArrayStore.LONG_ARRAY_ALLOCATOR;
        } else if (value instanceof Double) {
            return DoubleArrayStore.DOUBLE_ARRAY_ALLOCATOR;
        } else {
            return ObjectArrayStore.OBJECT_ARRAY_ALLOCATOR;
        }
    }

    /** Read the value from {@code index} of {@code store}. */
    public abstract Object read(Object store, int index);

    /** Return whether {@code store} can accept this {@code value}. */
    @Abstract(ifExported = { "write", "acceptsAllValues", "isMutable", "fill" })
    public boolean acceptsValue(Object store, Object value) {
        return false;
    }

    /** Return whether {@code store} can accept all values that could be held in {@code otherStore}. */
    @Abstract(ifExported = { "write", "acceptsValue", "isMutable", "fill" })
    public boolean acceptsAllValues(Object store, Object otherStore) {
        return false;
    }

    /** Return whether {@code store} can be mutated. If not, then an allocator must be used to create a mutable version
     * of the store and contents copied in order to make modifications. */
    @Abstract(ifExported = { "write", "acceptsValue", "acceptsAllValues", "fill" })
    public boolean isMutable(Object store) {
        return false;
    }

    /** Return whether the {@code store} is native, i.e. can it be accessed via native pointers from C? */
    public boolean isNative(Object store) {
        return false;
    }

    /** Return whether the {@code store} only holds primitives such as integers or doubles, or might hold other objects
     * as well. */
    @Abstract(ifExported = "sort")
    public boolean isPrimitive(Object store) {
        return false;
    }

    /** Return whether the {@code store} is shared between multiple threads. */
    public boolean isShared(Object store) {
        return false;
    }

    /** Return an initial store with the appropriate sharing. */
    public Object initialStore(Object store) {
        return INITIAL_STORE;
    }

    /** Return the underlying storage used by this array, which may be behind multiple wrappers. Stores which wrap some
     * array store should always recursively unwrap that store using this same library call. */
    public Object backingStore(Object store) {
        return store;
    }

    /** Return a store that can be shared across threads. */
    public Object makeShared(Object store, int size) {
        return new SharedArrayStorage(store);
    }

    /** Do any work required to start sharing elements across threads in the range from {@code start} (inclusive) to
     * {@code end} (exclusive). */
    public void shareElements(Object store, int start, int end) {
        assert isPrimitive(store);
    }

    /** Return a description of {@code store} for debugging output. */
    public abstract String toString(Object store);

    /** Write {@code value} to {@code index} of {@code store}. */
    @Abstract(ifExported = { "acceptsValue", "acceptsAllValues", "isMutable", "fill" })
    public void write(Object store, int index, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    /** Return the capacity of {@code store}. */
    public abstract int capacity(Object store);

    /** Return a copy of {@code store} expanded to the requested capacity. */
    public abstract Object expand(Object store, int capacity);

    /** Return a store representing the range from {@code start} (inclusive) to {@code end} (exclusive) of {@code store}
     * . This is usually an unmodifiable view onto the existing store, so the user may need to replace the reference to
     * the original store with a second unmodifiable view. */
    public Object extractRange(Object store, int start, int end) {
        return new DelegatedArrayStorage(store, start, (end - start));
    }

    public Object extractRangeAndUnshare(Object store, int start, int end) {
        return extractRange(store, start, end);
    }

    /** Copy a range from this array store into a plane Object[]. */
    public abstract Object[] boxedCopyOfRange(Object store, int start, int length);

    /** Copy the contents of {@code store} from {@code srcStart} to the {@code dest} store starting from
     * {@code destStart}. {@code length} entries will be copied. */
    public abstract void copyContents(Object store, int srcStart, Object dest, int destStart, int length);

    /** If the array is mutable, clears the part of the array starting at {@code start} and extending for {@code length}
     * elements, so that that range does not retain references to objects/memory/resources. This can be understood as
     * "nulling out" that part of the array, and will do nothing for primitive arrays. */
    public abstract void clear(Object store, int start, int length);


    /** Fill the part of the array starting at {@code start} and extending for {@code length} elements using
     * {@code value}, which must be accepted by the store. */
    @Abstract(ifExported = "write")
    public void fill(Object store, int start, int length, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert acceptsValue(store, value);
        throw new UnsupportedOperationException();
    }

    /** Create a mutable copy of {@code store} of length {@code length}. */
    public abstract Object toJavaArrayCopy(Object store, int length);

    /** Sort the first {@code size} elements of {@code store} in place. */
    @Abstract(ifExported = "isPrimitive")
    public void sort(Object store, int size) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new UnsupportedOperationException();
    }

    /** Return an iterator over {@code store} from the element at {@code start} index, and returning up to
     * {@code length} elements. */
    public abstract Iterable<Object> getIterable(Object store, int start, int length);

    /** Return an allocator that can accept all the values of {@code store} and {@code newValue}. */
    public abstract ArrayAllocator generalizeForValue(Object store, Object newValue);

    /** Return an allocator that can accept all the values of {@code store} and all the values that could be held in
     * {@code newStore}. */
    public abstract ArrayAllocator generalizeForStore(Object store, Object newStore);

    /** Return an allocator that can accept all the values of {@code store} and will share values stored in storage
     * created by it. */
    public abstract ArrayAllocator generalizeForSharing(Object store);

    /** Return a new store of length {@code length} that can accept all the values of {@code store} and {@code newValue}
     * . */
    public abstract Object allocateForNewValue(Object store, Object newValue, int length);

    /** Return a new store of length {@code length} that can accept all the values of {@code store} and all the values
     * of {@code newStore}. */
    public abstract Object allocateForNewStore(Object store, Object newStore, int length);

    /** Return a new store of length {@code length} that can accept all the values of {@code store} and all the values
     * of {@code newStore} and is unshared. */
    public Object unsharedAllocateForNewStore(Object store, Object newStore, int length) {
        return allocateForNewStore(store, newStore, length);
    }

    /** Return an allocator for a mutable version of {@code store}. Only use this if the new store is reassigned to the
     * same RubyArray. Otherwise, {@link #unsharedAllocator(Object)} should be used. */
    public abstract ArrayAllocator allocator(Object store);

    /** Return an allocator for a mutable, unshared version of {@code store}. */
    public ArrayAllocator unsharedAllocator(Object store) {
        return allocator(store);
    }

    /** Return whether the {@code store}'s default value is {@code value}. */
    public abstract boolean isDefaultValue(Object store, Object value);

    /** Class for allocating array stores and querying properties related to that. */
    public abstract static class ArrayAllocator {

        /** Return a new array store with {@code capacity} elements. */
        public abstract Object allocate(int capacity);

        /** Return whether stores returned by this allocator can hold {@code value}. */
        public abstract boolean accepts(Object value);

        /** Return whether stores returned by this allocator specialize for {@code value}. This differs from
         * {@link #accepts(Object)} because a store holding objects might accepts a {@code
         * long}, but does not specialize for that in the way that a store that holds unboxed {@code long}s does. */
        public abstract boolean specializesFor(Object value);

        /** Return whether {@code value} is the default value for stores returned by this allocator. For primitive
         * stores the default value will normally be 0, or its numerical equivalent, while stores that hold Objects will
         * usually default to {@code null}, this may not be true for those interacting with native memory where their
         * default value will be whatever is represented by a zero value in their implementation. */
        public abstract boolean isDefaultValue(Object value);

        /** Return whether the allocated storage is good for sharing across threads. */
        public boolean isShared() {
            return false;
        }
    }
}
