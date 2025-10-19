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

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.hash.CompareHashKeysNode;
import org.truffleruby.core.hash.Entry;
import org.truffleruby.core.hash.FreezeHashKeyIfNeededNode;
import org.truffleruby.core.hash.HashGuards;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryWithHashedCallback;
import org.truffleruby.core.hash.library.PackedHashStoreLibraryFactory.SmallHashLiteralNodeGen;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@ExportLibrary(value = HashStoreLibrary.class, receiverType = Object[].class)
@GenerateUncached
public final class PackedHashStoreLibrary {

    /** Maximum numbers of entries to be represented as a packed Hash */
    public static final int MAX_ENTRIES = 3;
    private static final int ELEMENTS_PER_ENTRY = 3;
    public static final int TOTAL_ELEMENTS = MAX_ENTRIES * ELEMENTS_PER_ENTRY;

    // region Utilities

    public static Object[] createStore() {
        return new Object[TOTAL_ELEMENTS];
    }

    private static int getHashed(Object[] store, int n) {
        return (int) store[n * ELEMENTS_PER_ENTRY];
    }

    private static Object getKey(Object[] store, int n) {
        return store[n * ELEMENTS_PER_ENTRY + 1];
    }

    private static Object getValue(Object[] store, int n) {
        return store[n * ELEMENTS_PER_ENTRY + 2];
    }

    private static void setHashed(Object[] store, int n, int hashed) {
        store[n * ELEMENTS_PER_ENTRY] = hashed;
    }

    private static void setKey(Object[] store, int n, Object key) {
        store[n * ELEMENTS_PER_ENTRY + 1] = key;
    }

    private static void setValue(Object[] store, int n, Object value) {
        store[n * ELEMENTS_PER_ENTRY + 2] = value;
    }

    public static void setHashedKeyValue(Object[] store, int n, int hashed, Object key, Object value) {
        setHashed(store, n, hashed);
        setKey(store, n, key);
        setValue(store, n, value);
    }

    private static void removeEntry(Object[] store, int n) {
        assert verifyIntegerHashes(store);

        final int index = n * ELEMENTS_PER_ENTRY;
        System.arraycopy(
                store,
                index + ELEMENTS_PER_ENTRY,
                store,
                index,
                TOTAL_ELEMENTS - ELEMENTS_PER_ENTRY - index);

        assert verifyIntegerHashes(store);
    }

    private static boolean verifyIntegerHashes(Object[] store) {
        for (int i = 0; i < TOTAL_ELEMENTS; i += ELEMENTS_PER_ENTRY) {
            assert store[i] == null || store[i] instanceof Integer;
        }
        return true;
    }

    @TruffleBoundary
    private static void promoteToBuckets(RubyHash hash, Object[] store, int size) {
        final Entry[] buckets = new Entry[BucketsHashStore.growthCapacityGreaterThan(size)];

        Entry firstInSequence = null;
        Entry previousInSequence = null;
        Entry lastInSequence = null;

        for (int n = 0; n < size; n++) {
            final int hashed = getHashed(store, n);
            final Entry entry = new Entry(hashed, getKey(store, n), getValue(store, n));

            if (previousInSequence == null) {
                firstInSequence = entry;
            } else {
                previousInSequence.setNextInSequence(entry);
                entry.setPreviousInSequence(previousInSequence);
            }

            previousInSequence = entry;
            lastInSequence = entry;

            final int bucketIndex = BucketsHashStore.getBucketIndex(hashed, buckets.length);
            BucketsHashStore.appendToLookupChain(buckets, entry, bucketIndex);
        }

        hash.setStore(new BucketsHashStore(buckets, firstInSequence, lastInSequence));
        hash.size = size;
    }

    private static void promoteToCompact(RubyHash hash, Object[] store) {
        CompactHashStore newStore = new CompactHashStore(MAX_ENTRIES);
        for (int n = 0; n < MAX_ENTRIES; n++) {
            newStore.insertHashKeyValue(getHashed(store, n), getKey(store, n), getValue(store, n));
        }
        hash.setStore(newStore);
        hash.size = MAX_ENTRIES;
    }

    // endregion
    // region Messages

    @ExportMessage
    static Object lookupOrDefault(Object[] store, Frame frame, RubyHash hash, Object key, PEBiFunction defaultNode,
            @Cached LookupPackedEntryNode lookupPackedEntryNode,
            @Cached @Shared HashingNodes.ToHash hashNode) {

        int hashed = hashNode.execute(key, hash.compareByIdentity);
        return lookupPackedEntryNode.execute(frame, hash, key, hashed, defaultNode);
    }

    @ImportStatic(HashGuards.class)
    @ExportMessage
    protected static final class Set {
        @Specialization(guards = "hash.size == 0")
        static boolean setFirst(Object[] store, RubyHash hash, Object key, Object value, boolean byIdentity,
                @Cached @Shared FreezeHashKeyIfNeededNode freezeHashKeyIfNeeded,
                @Cached @Shared HashingNodes.ToHash hashNode,
                @Bind Node node) {
            assert verify(store, hash);

            final Object key2 = freezeHashKeyIfNeeded.executeFreezeIfNeeded(node, key, byIdentity);
            setHashedKeyValue(store, 0, hashNode.execute(key2, byIdentity), key2, value);
            hash.size = 1;
            assert verify(store, hash);
            return true;
        }

        @Specialization(guards = "hash.size > 0")
        static boolean set(Object[] store, RubyHash hash, Object key, Object value, boolean byIdentity,
                @Cached @Shared FreezeHashKeyIfNeededNode freezeHashKeyIfNeeded,
                @Cached @Shared HashingNodes.ToHash hashNode,
                @Cached @Shared CompareHashKeysNode compareHashKeys,
                @CachedLibrary(limit = "hashStrategyLimit()") HashStoreLibrary hashes,
                @Cached InlinedConditionProfile withinCapacity,
                @Bind Node node) {
            assert verify(store, hash);

            final int size = hash.size;
            final Object key2 = freezeHashKeyIfNeeded.executeFreezeIfNeeded(node, key, byIdentity);
            final int hashed = hashNode.execute(key2, byIdentity);

            // written very carefully to allow PE
            for (int n = 0; n < MAX_ENTRIES; n++) {
                if (n < size) {
                    final int otherHashed = getHashed(store, n);
                    final Object otherKey = getKey(store, n);
                    if (compareHashKeys.execute(node, byIdentity, key2, hashed, otherKey, otherHashed)) {
                        setValue(store, n, value);
                        return false;
                    }
                }
            }

            if (withinCapacity.profile(node, size < MAX_ENTRIES)) {
                setHashedKeyValue(store, size, hashed, key2, value);
                hash.size += 1;
                return true;
            }


            assert size == MAX_ENTRIES;
            promote(store, hash, node);

            hashes.set(hash.store, hash, key2, value, byIdentity);
            return true;
        }
    }

    private static void promote(Object[] store, RubyHash hash, Node node) {
        if (RubyLanguage.get(node).options.BIG_HASH_STRATEGY_IS_BUCKETS) {
            promoteToBuckets(hash, store, MAX_ENTRIES);
        } else {
            promoteToCompact(hash, store);
        }
    }

    @ExportMessage
    static Object delete(Object[] store, RubyHash hash, Object key,
            @Cached @Shared HashingNodes.ToHash hashNode,
            @Cached @Shared CompareHashKeysNode compareHashKeys,
            @Bind Node node) {

        assert verify(store, hash);
        final int hashed = hashNode.execute(key, hash.compareByIdentity);
        final int size = hash.size;
        // written very carefully to allow PE
        for (int n = 0; n < MAX_ENTRIES; n++) {
            if (n < size) {
                final int otherHashed = getHashed(store, n);
                final Object otherKey = getKey(store, n);

                if (compareHashKeys.execute(node, hash.compareByIdentity, key, hashed, otherKey, otherHashed)) {
                    final Object value = getValue(store, n);
                    removeEntry(store, n);
                    hash.size -= 1;
                    return value;
                }
            }
        }
        assert verify(store, hash);
        return null;
    }

    @ExportMessage
    static Object deleteLast(Object[] store, RubyHash hash, Object key) {

        assert verify(store, hash);
        final int n = hash.size - 1;
        final Object lastKey = getKey(store, n);
        if (key != lastKey) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives
                    .shouldNotReachHere("The last key was not " + key + " as expected but was " + lastKey);
        }
        final Object value = getValue(store, n);
        removeEntry(store, n);
        hash.size -= 1;
        assert verify(store, hash);
        return value;
    }

    @ExportMessage
    @ImportStatic(HashGuards.class)
    static final class EachEntry {

        @Specialization(guards = "hash.size == cachedSize", limit = "packedHashLimit()")
        @ExplodeLoop
        static Object eachEntry(Object[] store, RubyHash hash, EachEntryCallback callback, Object state,
                @Bind Node node,
                @Cached(value = "hash.size", allowUncached = true) int cachedSize,
                @Cached @Exclusive LoopConditionProfile loopProfile) {

            // Don't verify hash here, as `store != hash.store` when calling from `eachEntrySafe`.
            int i = 0;
            try {
                for (; loopProfile.inject(i < cachedSize); i++) {
                    callback.accept(i, getKey(store, i), getValue(store, i), state);
                    TruffleSafepoint.poll(node);
                }
            } finally {
                RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
            }
            return state;
        }
    }

    @ExportMessage
    @ImportStatic(HashGuards.class)
    static final class EachEntryHashed {

        @Specialization(guards = "hash.size == cachedSize", limit = "packedHashLimit()")
        @ExplodeLoop
        static Object eachEntry(Object[] store, RubyHash hash, EachEntryWithHashedCallback callback, Object state,
                @Bind Node node,
                @Cached(value = "hash.size", allowUncached = true) int cachedSize,
                @Cached @Exclusive LoopConditionProfile loopProfile) {
            // NOTE: cannot assert verify(store, hash); here because this is called in onShareHook(),
            // and the hash is already shared when that hook is called.

            int i = 0;
            try {
                for (; loopProfile.inject(i < cachedSize); i++) {
                    callback.accept(i, getHashed(store, i), getKey(store, i), getValue(store, i), state);
                    TruffleSafepoint.poll(node);
                }
            } finally {
                RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
            }
            return state;
        }
    }

    @ExportMessage
    static Object eachEntrySafe(Object[] store, RubyHash hash, EachEntryCallback callback, Object state,
            @CachedLibrary("store") HashStoreLibrary self) {
        return self.eachEntry(copyStore(store), hash, callback, state);
    }

    @ExportMessage(name = "copyStore")
    static Object[] copyStore(Object[] store) {
        final Object[] copied = createStore();
        System.arraycopy(store, 0, copied, 0, TOTAL_ELEMENTS);
        return copied;
    }

    @ExportMessage
    static RubyArray shift(Object[] store, RubyHash hash,
            @Bind Node node) {

        assert verify(store, hash);
        final Object key = getKey(store, 0);
        final Object value = getValue(store, 0);
        removeEntry(store, 0);
        hash.size -= 1;
        assert verify(store, hash);
        final RubyLanguage language = RubyLanguage.get(node);
        final RubyContext context = RubyContext.get(node);
        return ArrayHelpers.createArray(context, language, new Object[]{ key, value });
    }

    @ExportMessage
    static void rehash(Object[] store, RubyHash hash,
            @Cached @Shared CompareHashKeysNode compareHashKeys,
            @Cached @Shared HashingNodes.ToHash hashNode,
            @Bind Node node) {

        assert verify(store, hash);
        int size = hash.size;
        for (int n = 0; n < size; n++) {
            final Object key = getKey(store, n);
            final int newHash = hashNode.execute(getKey(store, n), hash.compareByIdentity);
            setHashed(store, n, newHash);

            for (int m = n - 1; m >= 0; m--) {
                if (getHashed(store, m) == newHash && compareHashKeys.execute(
                        node,
                        hash.compareByIdentity,
                        key,
                        newHash,
                        getKey(store, m),
                        getHashed(store, m))) {
                    removeEntry(store, n);
                    size--;
                    n--;
                    break;
                }
            }
        }
        hash.size = size;
        assert verify(store, hash);
    }

    @TruffleBoundary
    @ExportMessage
    static boolean verify(Object[] store, RubyHash hash) {
        assert hash.store == store;
        assert !SharedObjects.isShared(hash);

        final int size = hash.size;
        assert store.length == TOTAL_ELEMENTS : store.length;

        for (int i = 0; i < size * ELEMENTS_PER_ENTRY; i++) {
            assert store[i] != null;
        }

        for (int n = 0; n < size; n++) {
            final Object key = getKey(store, n);
            assert key != null;
            final Object value = getValue(store, n);
            assert value != null;
        }

        return true;
    }

    // endregion
    // region Nodes

    // Splitting: naturally split by usages
    @GenerateUncached
    @ImportStatic(HashGuards.class)
    public abstract static class LookupPackedEntryNode extends RubyBaseNode {

        public abstract Object execute(Frame frame, RubyHash hash, Object key, int hashed, PEBiFunction defaultValue);

        @Specialization(
                guards = {
                        "isCompareByIdentity(hash) == cachedByIdentity",
                        "cachedIndex >= 0",
                        "cachedIndex < hash.size",
                        "sameKeysAtIndex(node, refEqual, hash, key, hashed, cachedIndex, cachedByIdentity)" },
                limit = "1")
        static Object getConstantIndexPackedArray(RubyHash hash, Object key, int hashed, PEBiFunction defaultValueNode,
                @Cached ReferenceEqualNode refEqual,
                @Cached("isCompareByIdentity(hash)") boolean cachedByIdentity,
                @Bind Node node,
                @Cached("index(node, refEqual, hash, key, hashed, cachedByIdentity)") int cachedIndex) {

            final Object[] store = (Object[]) hash.store;
            return getValue(store, cachedIndex);
        }

        protected static int index(Node node, ReferenceEqualNode refEqual, RubyHash hash, Object key, int hashed,
                boolean compareByIdentity) {

            final Object[] store = (Object[]) hash.store;
            final int size = hash.size;
            for (int n = 0; n < size; n++) {
                final int otherHashed = getHashed(store, n);
                final Object otherKey = getKey(store, n);
                if (sameKeys(node, refEqual, compareByIdentity, key, hashed, otherKey, otherHashed)) {
                    return n;
                }
            }
            return -1;
        }

        protected static boolean sameKeysAtIndex(Node node, ReferenceEqualNode refEqual, RubyHash hash, Object key,
                int hashed,
                int cachedIndex, boolean cachedByIdentity) {

            final Object[] store = (Object[]) hash.store;
            final Object otherKey = getKey(store, cachedIndex);
            final int otherHashed = getHashed(store, cachedIndex);
            return sameKeys(node, refEqual, cachedByIdentity, key, hashed, otherKey, otherHashed);
        }

        private static boolean sameKeys(Node node, ReferenceEqualNode refEqual, boolean compareByIdentity, Object key,
                int hashed, Object otherKey, int otherHashed) {
            return CompareHashKeysNode
                    .referenceEqualKeys(node, refEqual, compareByIdentity, key, hashed, otherKey, otherHashed);
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Specialization(replaces = "getConstantIndexPackedArray")
        Object getPackedArray(Frame frame, RubyHash hash, Object key, int hashed, PEBiFunction defaultValueNode,
                @Cached CompareHashKeysNode compareHashKeys,
                @Cached InlinedBranchProfile notInHashProfile,
                @Cached InlinedConditionProfile byIdentityProfile,
                @Bind Node node) {

            final boolean compareByIdentity = byIdentityProfile.profile(node, hash.compareByIdentity);
            final Object[] store = (Object[]) hash.store;
            final int size = hash.size;
            for (int n = 0; n < MAX_ENTRIES; n++) {
                if (n < size) {
                    final int otherHashed = getHashed(store, n);
                    final Object otherKey = getKey(store, n);
                    if (compareHashKeys.execute(node, compareByIdentity, key, hashed, otherKey, otherHashed)) {
                        return getValue(store, n);
                    }
                }
            }

            notInHashProfile.enter(node);
            return defaultValueNode.accept(frame, hash, key);
        }
    }

    public abstract static class SmallHashLiteralNode extends HashLiteralNode {

        @Child private HashingNodes.ToHashByHashCode hashNode;
        @Child private DispatchNode equalNode;

        public SmallHashLiteralNode(RubyNode[] keyValues) {
            super(keyValues);
        }

        @Specialization
        @ExplodeLoop
        Object doHash(VirtualFrame frame,
                @Cached BooleanCastNode booleanCastNode,
                @Cached InlinedBranchProfile duplicateKeyProfile,
                @Cached FreezeHashKeyIfNeededNode freezeHashKeyIfNeededNode) {
            final Object[] store = createStore();
            int size = 0;

            for (int n = 0; n < keyValues.length / 2; n++) {
                Object key = keyValues[n * 2].execute(frame);
                key = freezeHashKeyIfNeededNode.executeFreezeIfNeeded(this, key, false);

                final int hashed = hash(key);

                final Object value = keyValues[n * 2 + 1].execute(frame);
                boolean duplicateKey = false;

                for (int i = 0; i < n; i++) {
                    if (i < size &&
                            hashed == getHashed(store, i) &&
                            callEqual(key, getKey(store, i), booleanCastNode)) {
                        duplicateKeyProfile.enter(this);
                        setKey(store, i, key);
                        setValue(store, i, value);
                        duplicateKey = true;
                        break;
                    }
                }

                if (!duplicateKey) {
                    setHashedKeyValue(store, size, hashed, key, value);
                    size++;
                }
            }

            return new RubyHash(
                    coreLibrary().hashClass,
                    getLanguage().hashShape,
                    getContext(),
                    store,
                    size,
                    false);
        }

        private int hash(Object key) {
            if (hashNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashNode = insert(HashingNodes.ToHashByHashCode.create());
            }
            return hashNode.executeCached(key);
        }

        private boolean callEqual(Object receiver, Object key, BooleanCastNode booleanCastNode) {
            if (equalNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalNode = insert(DispatchNode.create());
            }

            return booleanCastNode.execute(this, equalNode.call(receiver, "eql?", key));
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = SmallHashLiteralNodeGen.create(cloneUninitialized(keyValues));
            return copy.copyFlags(this);
        }

    }

    // endregion
}
