/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash.library;

import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.hash.HashGuards;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/** Library for accessing and manipulating the storage used for representing hashes. This includes reading, modifying,
 * and copy the storage. */
@DefaultExport(PackedHashStoreLibrary.class)
@GenerateLibrary
public abstract class HashStoreLibrary extends Library {

    private static final LibraryFactory<HashStoreLibrary> FACTORY = LibraryFactory.resolve(HashStoreLibrary.class);

    public static HashStoreLibrary createDispatched() {
        return FACTORY.createDispatched(HashGuards.hashStrategyLimit());
    }

    public static HashStoreLibrary getUncached(RubyHash hash) {
        CompilerAsserts.neverPartOfCompilation("uncached libraries must not be used in PE code");
        final Object store = hash.store;
        return FACTORY.getUncached(store);
    }

    public static boolean verify(RubyHash hash) {
        final Object store = hash.store;
        return FACTORY.getUncached(store).verify(store, hash);
    }

    /** Looks up the key in the hash and returns the associated value, or the result of calling {@code defaultNode} if
     * no entry for the given key exists. */
    @Abstract
    public abstract Object lookupOrDefault(Object store, Frame frame, RubyHash hash, Object key,
            PEBiFunction defaultNode);

    /** Associates the key with the value and returns true only if the hash didn't contain the key before the operation
     * (i.e. returns false if and only if the key was already associated with any value). {@code byIdentity} indicates
     * whether the key should be compared using {@link ReferenceEqualNode} or {@link SameOrEqlNode}. */
    @Abstract
    public abstract boolean set(Object store, RubyHash hash, Object key, Object value, boolean byIdentity);

    public void clear(Object store, RubyHash hash) {
        hash.setStore(EmptyHashStore.NULL_HASH_STORE);
        hash.size = 0;
    }

    /** Removes the entry for the key from the hash, and returns the associated value. If the hash does not have an
     * entry for the key, returns {@code null}. */
    @Abstract
    public abstract Object delete(Object store, RubyHash hash, Object key);

    /** A helper for detect_recursion. A variant of {@link #delete(Object, RubyHash, Object)} optimized for removing the
     * most recently added key, because we are using the hash like a stack and guarantee that the last entry we added is
     * the one we'll want to delete. Checks that the deleted key is the one expected by the user. */
    @Abstract
    public abstract Object deleteLast(Object store, RubyHash hash, Object key);

    /** Calls {@code callback} on every entry in the hash, returning the state object. The callback object MUST be
     * passed sequential increasing indices starting from 0 */
    @Abstract
    public abstract Object eachEntry(Object store, RubyHash hash, EachEntryCallback callback, Object state);

    /** Same as {@link #eachEntry(Object, RubyHash, EachEntryCallback, Object)} but also yields the hashed value. */
    @Abstract
    public abstract Object eachEntryHashed(Object store, RubyHash hash, EachEntryWithHashedCallback callback,
            Object state);

    /** Same as {@link #eachEntry(Object, RubyHash, EachEntryCallback, Object)} but guaranteed to be safe to use if the
     * hash is modified during iteration. In particular, the guarantee is that the same entry won't be processed twice.
     * The callback object MUST be passed sequential increasing indices starting from 0 */
    @Abstract
    public abstract Object eachEntrySafe(Object store, RubyHash hash, EachEntryCallback callback, Object state);

    /** Returns a copy of the store, useful for {@code Hash#replace}. */
    @Abstract
    public abstract Object copyStore(Object store);

    /** Removes the first key-value pair in insertion order from the hash and returns it as the two-item array [key,
     * value]. Can return null if the hash becomes empty concurrently. */
    @Abstract
    public abstract RubyArray shift(Object store, RubyHash hash);

    /** Re-hashes the keys in the hash (if the keys are mutable objects, then changes to these objects may change the
     * hash.) */
    @Abstract
    public abstract void rehash(Object store, RubyHash hash);

    public void becomeCompareByIdentityAndRehash(Object store, RubyHash hash) {
        hash.compareByIdentity = true;
        rehash(store, hash);
    }

    /** Returns true only if the store is in a valid state. To be used in assertions, and also uses assertions
     * internally. */
    @Abstract
    public abstract boolean verify(Object store, RubyHash hash);

    public interface EachEntryCallback {
        void accept(int index, Object key, Object value, Object state);
    }

    public interface EachEntryWithHashedCallback {
        void accept(int index, int hashed, Object key, Object value, Object state);
    }

    /** Call the block with a key-value entry. If the block has > 1 arity, passes the key and the value as arguments,
     * otherwise passes an array containing the key and the value as single argument. */
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class YieldPairNode extends RubyBaseNode {

        public static YieldPairNode create() {
            return HashStoreLibraryFactory.YieldPairNodeGen.create();
        }

        public abstract Object execute(RubyProc block, Object key, Object value);

        @Specialization
        Object yieldPair(RubyProc block, Object key, Object value,
                @Cached CallBlockNode yieldNode,
                @Cached InlinedConditionProfile arityMoreThanOne) {
            // MRI behavior, see rb_hash_each_pair()
            // We use getMethodArityNumber() here since for non-lambda the semantics are the same for both branches
            if (arityMoreThanOne.profile(this, block.arity.getMethodArityNumber() > 1)) {
                return yieldNode.yield(this, block, key, value);
            } else {
                return yieldNode.yield(this, block, createArray(new Object[]{ key, value }));
            }
        }
    }
}
