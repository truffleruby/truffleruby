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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.dsl.Cached.Exclusive;
import static org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;

import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.hash.CompareHashKeysNode;
import org.truffleruby.core.hash.FreezeHashKeyIfNeededNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryWithHashedCallback;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import org.truffleruby.language.objects.shared.SharedObjects;

/** The Compact hash strategy from Hash Maps That Don't Hate You. See
 * https://blog.toit.io/hash-maps-that-dont-hate-you-1a96150b492a for more details (archived at
 * https://archive.ph/wip/sucRY). Specifically that blog post has good visualizations of the index and kvStore arrays.
 * We do not currently implement the deletion optimization. */
@ExportLibrary(value = HashStoreLibrary.class)
@GenerateUncached
public final class CompactHashStore {
    //  ---------------------------------------------------------------------------------------------------------------
    // Big Picture:
    //  (1) A Compact Hash's big idea is storing all key-values in a contiguous array (the KV array) in insertion order
    //      This enables us to do sequential iteration on all key-values in insertion order, which is what we want
    //      But it doesn't allow constant-time lookups
    //  -----------------------------------------------
    //  (2) Thus, a Compact Hash also stores the position of every key in the KV array in another array called the index
    //      That KV position is stored in the index array at a location that depends on the respective key's hash
    //      Therefore, given a key we can always quickly find where it's in the KV array by:
    //                                    -----------------------------------------------
    //              1- Hashing it
    //              2- Calculating the position of the hash from its value
    //              3- Looking up the index array at that position,
    //              4- Where we find the KV position
    //              5- Then looking up the KV array by that position
    //              6- Where we find the key and then the value right next to it
    //                                    -----------------------------------------------
    //      (step 3 is oversimplified a bit, the position calculated in (2) is where we store the hash itself in the
    //       index array, the KV position is stored next to it. The reason we store the hash is that step 2 is lossy,
    //       multiple not-equal hashes can map to the same position in the index array, that's a different sort of
    //       "collision" in addition to the usual collision of equal hash values coming from different keys. Both kinds
    //       of collisions actually result in the same outcome: contention on an index slot. So both are automatically
    //       handled by the same collision-resolution strategy: open-addressing with linear probing.)
    //  -----------------------------------------------
    //  (3) tl;dr: Compact Hashes give you both insertion-order iteration AND the usual const-time guarantees
    //  ---------------------------------------------------------------------------------------------------------------

    /** We view the index array as consisting of "slots", each slot is 2 array positions. index[0] and index[1] is one
     * slot, index[2] and index[3] is another,... The first position of a slot holds the hash of a key and the second
     * holds an offsetted index into the KV store (index + 1) where the key is stored. The reason we store an offsetted
     * index and not the index itself is so that 0 is not a valid index, thus we can use it as unused slot marker, and
     * since all int[] arrays start out zeroed this means that new index arrays are marked unused "for free" */
    private int[] index;
    /** An array of key and value pairs, in insertion order */
    private Object[] kvStore;
    /** This tracks the next valid insertion position into kvStore, it starts at 0 and always increases by 2. We can't
     * use the size of the RubyHash for that, because deletion reduces its hash.size. Whereas the insertion pos into
     * kvStore can never decrease, we don't reuse empty slots (without also resizing the whole hash). */
    private int kvStoreInsertionPos;
    /** This tracks the number of occupied slots at which we should rebuild the index array. For example, at a load
     * factor of 0.75 and an index of total size 64 slots, that number is 48 slots. (Technically, that's redundant
     * information that is derived from the load factor and the index size, but deriving it requires a float
     * multiplication or division, and we want to check for it on every insertion, so it's inefficient to keep
     * calculating it every time it is needed) */
    private int indexGrowthThreshold;

    /** Each slot in the index array can be in one of 3 states, depending on the value of its second (offset) field:
     * <li>Offset >= 1: Filled, the data in the hash field and the offset field is valid. Subtracting one from the
     * offset will yield a valid index into the KV array.</li>
     * <li>Offset == 0: Unused, the data in the hash field and the offset field is NOT valid, and the slot was never
     * filled with valid data. The value 0 is used for unused slots so `new int[]` automatically makes all slots unused
     * without an extra Arrays.fill().</li>
     * <li>Offset == -1: Deleted, the data in the hash field and the offset field is NOT valid, but the slot was
     * occupied with valid data before.</li> */
    private static final int INDEX_SLOT_UNUSED = 0;

    // Returned by methods doing array search which don't find what they are looking for
    static final int KEY_NOT_FOUND = -2;
    static final int HASH_NOT_FOUND = KEY_NOT_FOUND;

    public static final float THRESHOLD_LOAD_FACTOR_FOR_INDEX_REBUILD = 0.75f;

    public CompactHashStore(int capacity) {
        if (capacity < 1 || capacity >= (1 << 28)) {
            throw shouldNotReachHere();
        }

        int kvCapacity = roundUpwardsToNearestPowerOf2(capacity);
        // the index array needs to be a little sparse for good performance (i.e. low load factor)
        // so to store 8 key-value entries an index of exactly 8 slots is too crowded.
        // This way the initial load factor (for capacity entries) is between and 0.25 and 0.5.
        int indexCapacity = 2 * kvCapacity;

        // All zeros by default, so all slots are marked empty for free
        this.index = new int[2 * indexCapacity];
        this.kvStore = new Object[2 * kvCapacity];
        this.kvStoreInsertionPos = 0;
        this.indexGrowthThreshold = (int) (indexCapacity * THRESHOLD_LOAD_FACTOR_FOR_INDEX_REBUILD);
    }

    // private non-allocating constructor for .copy()
    private CompactHashStore(int[] index, Object[] kvStore, int kvStoreInsertionPos, int indexGrowthThreshold) {
        this.index = index;
        this.kvStore = kvStore;
        this.kvStoreInsertionPos = kvStoreInsertionPos;
        this.indexGrowthThreshold = indexGrowthThreshold;
    }

    /** Rounds up powers of 2 themselves : 1 => 2, 2 => 4, 3 => 4, 4 => 8, ... */
    private static int roundUpwardsToNearestPowerOf2(int num) {
        return Integer.highestOneBit(num) << 1;
    }

    private static int indexPosToKeyPos(int[] index, int indexPos) {
        return index[indexPos + 1] - 1;
    }

    private static int indexPosToValuePos(int[] index, int indexPos) {
        return index[indexPos + 1];
    }

    @ExportMessage
    Object lookupOrDefault(Frame frame, RubyHash hash, Object key, PEBiFunction defaultNode,
            @Cached @Shared GetIndexPosForKeyNode getIndexPosForKeyNode,
            @Cached @Shared HashingNodes.ToHash hashFunction,
            @Cached @Exclusive InlinedConditionProfile keyNotFound,
            @Bind Node node) {
        int keyHash = hashFunction.execute(key, hash.compareByIdentity);

        int indexPos = getIndexPosForKeyNode.execute(node, key, keyHash, hash.compareByIdentity, index, kvStore, false);
        if (keyNotFound.profile(node, indexPos == KEY_NOT_FOUND)) {
            return defaultNode.accept(frame, hash, key);
        }

        int valuePos = indexPosToValuePos(index, indexPos);
        return kvStore[valuePos];
    }

    @ExportMessage
    boolean set(RubyHash hash, Object key, Object value, boolean byIdentity,
            @Cached @Shared HashingNodes.ToHash hashFunction,
            @Cached @Shared GetIndexPosForKeyNode getIndexPosForKeyNode,
            @Cached FreezeHashKeyIfNeededNode freezeKey,
            @Cached SetKvAtNode setKv,
            @Bind Node node) {
        assert verify(hash);
        var frozenKey = freezeKey.executeFreezeIfNeeded(node, key, byIdentity);
        int keyHash = hashFunction.execute(frozenKey, byIdentity);
        int indexPos = getIndexPosForKeyNode.execute(node, frozenKey, keyHash, byIdentity, index, kvStore, true);
        int keyPos = indexPosToKeyPos(index, indexPos); // can be < 0 if inserting new key

        return setKv.execute(node, hash, this, indexPos, keyPos, keyHash, frozenKey, value);
    }

    @ExportMessage
    Object delete(RubyHash hash, Object key,
            @Cached @Shared GetIndexPosForKeyNode getIndexPosForKeyNode,
            @Cached @Shared HashingNodes.ToHash hashFunction,
            @Cached @Exclusive InlinedConditionProfile keyNotFound,
            @Bind Node node) {
        int keyHash = hashFunction.execute(key, hash.compareByIdentity);
        int indexPos = getIndexPosForKeyNode.execute(node, key, keyHash, hash.compareByIdentity, index, kvStore, false);
        if (keyNotFound.profile(node, indexPos == KEY_NOT_FOUND)) {
            return null;
        }

        int keyPos = indexPosToKeyPos(index, indexPos);
        return deleteKvAndGetV(hash, indexPos, keyPos);
    }

    @ExportMessage
    Object deleteLast(RubyHash hash, Object key,
            @Cached @Shared HashingNodes.ToHash hashFunction,
            @Cached @Shared GetIndexPosFromKeyPosNode getIndexPosFromKeyPosNode,
            @Cached @Shared InlinedLoopConditionProfile nonNullKeyNotYetFound,
            @Bind Node node) {
        assert hash.size > 0;
        int keyPos = firstNonNullKeyPosFromEnd(nonNullKeyNotYetFound, node);
        Object lastKey = kvStore[keyPos];
        if (key != lastKey) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives
                    .shouldNotReachHere("The last key was not " + key + " as expected but was " + lastKey);
        }

        int keyHash = hashFunction.execute(key, hash.compareByIdentity);
        int indexPos = getIndexPosFromKeyPosNode.execute(node, keyHash, keyPos, index);

        return deleteKvAndGetV(hash, indexPos, keyPos);
    }

    @ExportMessage
    RubyArray shift(RubyHash hash,
            @Cached @Shared HashingNodes.ToHash hashFunction,
            @Cached @Shared GetIndexPosFromKeyPosNode getIndexPosFromKeyPosNode,
            @Cached @Shared InlinedLoopConditionProfile nonNullKeyNotYetFound,
            @Bind Node node) {
        assert hash.size > 0;
        int keyPos = firstNonNullKeyPosFromBeginning(nonNullKeyNotYetFound, node);

        Object key = kvStore[keyPos];
        int keyHash = hashFunction.execute(key, hash.compareByIdentity);
        int indexPos = getIndexPosFromKeyPosNode.execute(node, keyHash, keyPos, index);
        Object val = deleteKvAndGetV(hash, indexPos, keyPos);

        return ArrayHelpers.createArray(RubyContext.get(node), RubyLanguage.get(node), new Object[]{ key, val });
    }

    @ExportMessage
    Object eachEntry(RubyHash hash, EachEntryCallback callback, Object state,
            @Cached @Exclusive InlinedConditionProfile keyNotNull,
            @Cached @Exclusive InlinedLoopConditionProfile loopProfile,
            @Bind Node node) {
        int i = 0;
        int callbackIdx = 0;
        try {
            for (; loopProfile.inject(node, i < kvStoreInsertionPos); i += 2) {
                if (keyNotNull.profile(node, kvStore[i] != null)) {
                    callback.accept(callbackIdx, kvStore[i], kvStore[i + 1], state);
                    callbackIdx++;
                }
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i >> 1);
        }
        return state;
    }

    @ExportMessage
    Object eachEntryHashed(RubyHash hash, EachEntryWithHashedCallback callback, Object state) {
        // NOTE: cannot assert verify(hash); here because this is called in onShareHook(),
        // and the hash is already shared when that hook is called.

        throw CompilerDirectives.shouldNotReachHere("not yet implemented");
    }

    @ExportMessage
    Object eachEntrySafe(RubyHash hash, EachEntryCallback callback, Object state,
            @CachedLibrary("this") HashStoreLibrary self) {
        return self.eachEntry(this, hash, callback, state);
    }

    @ExportMessage
    protected Object copyStore() {
        return this.copy();
    }

    @TruffleBoundary
    @ExportMessage
    void rehash(RubyHash hash,
            @Cached @Exclusive InlinedConditionProfile slotUsed,
            @Cached @Exclusive InlinedLoopConditionProfile loopProfile,
            @CachedLibrary("this") HashStoreLibrary self,
            @Bind Node node) {
        Object[] oldKvStore = this.kvStore;
        int oldKvStoreInsertionPos = this.kvStoreInsertionPos;

        this.kvStore = new Object[oldKvStore.length];
        this.kvStoreInsertionPos = 0;
        this.index = new int[this.index.length];
        hash.size = 0;

        int i = 0;
        try {
            for (; loopProfile.inject(node, i < oldKvStoreInsertionPos); i += 2) {
                if (slotUsed.profile(node, oldKvStore[i] != null)) {
                    self.set(this, hash, oldKvStore[i], oldKvStore[i + 1], hash.compareByIdentity);
                }
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i >> 1);
        }
    }

    @ExportMessage
    boolean verify(RubyHash hash) {
        assert hash.store == this;
        assert !SharedObjects.isShared(hash);

        assert kvStoreInsertionPos >= 0;
        assert kvStoreInsertionPos <= kvStore.length;
        assert kvStoreInsertionPos % 2 == 0;
        assert kvStoreInsertionPos >= hash.size; // because deletes only decrease hash.size

        int indexCapacity = index.length >> 1;
        assert hash.size < indexCapacity : "there must be unused or deleted slots left, otherwise lookup can cycle";
        assert ((float) hash.size) / indexCapacity <= THRESHOLD_LOAD_FACTOR_FOR_INDEX_REBUILD;

        assert (index.length & (index.length - 1)) == 0; // index.length is always a power of 2

        assertAllKvPositionsAreValid();

        return true;
    }

    private void assertAllKvPositionsAreValid() {
        boolean foundAtLeastOneUnusedSlot = false;
        for (int indexPos = 0; indexPos < index.length; indexPos += 2) {
            int valuePos = indexPosToValuePos(index, indexPos);
            if (valuePos == INDEX_SLOT_UNUSED) {
                foundAtLeastOneUnusedSlot = true;
            }
            if (valuePos != INDEX_SLOT_UNUSED) {
                assert valuePos > 0;
                assert valuePos < kvStoreInsertionPos;
                assert (valuePos & 1) == 1;
                int keyPos = valuePos - 1;
                assert kvStore[keyPos] != null;
                assert kvStore[valuePos] != null;

                int hashCode = index[indexPos];
                int startPos = indexPosFromHashCode(hashCode, index.length);
                for (int i = startPos; i != indexPos; i = incrementIndexPos(i, index.length)) {
                    assert indexPosToValuePos(index, i) > INDEX_SLOT_UNUSED;
                }
            }
        }
        assert foundAtLeastOneUnusedSlot;
    }

    private Object deleteKvAndGetV(RubyHash hash, int indexPos, int keyPos) {
        assert verify(hash);

        // Remove the index slot, move entries following it to avoid tombstones which would make the code
        // more complex, lookup slower and could fill the entire index array with tombstones,
        // in which case lookup would become linear.

        // From https://en.wikipedia.org/wiki/Open_addressing#Example_pseudocode,
        // https://stackoverflow.com/a/60709252/388803 and https://stackoverflow.com/a/60644631/388803

        // First let's override this slot so in case nothing follows it is correctly marked as unused.
        // An alternative would be to clear at reusePos after the loop but then the intermediate state is less clear.
        index[indexPos] = 0;
        index[indexPos + 1] = INDEX_SLOT_UNUSED;

        int reusePos = indexPos; // i
        int nextPos = incrementIndexPos(indexPos, index.length); // j
        while (indexPosToValuePos(index, nextPos) != INDEX_SLOT_UNUSED) { // not found an unused slot
            int hashCode = index[nextPos];
            int startPos = indexPosFromHashCode(hashCode, index.length); // k
            if (nextPos < reusePos ^ startPos <= reusePos ^ startPos > nextPos) {
                // move the slot to the reuse slot and keep going
                assert index[reusePos + 1] == INDEX_SLOT_UNUSED : "safe to move the slot to reuse slot";
                index[reusePos] = index[nextPos];
                index[reusePos + 1] = index[nextPos + 1];
                index[nextPos] = 0;
                index[nextPos + 1] = INDEX_SLOT_UNUSED;
                reusePos = nextPos;
            }

            nextPos = incrementIndexPos(nextPos, index.length);
            assert nextPos != indexPos;
        }

        // Remove the kvStore slot

        Object deletedValue = kvStore[keyPos + 1];

        // TODO: Instead of naively nulling out the key-value, which can produce long gaps of tombstones in the kvStore,
        //       See if we can annotate each gap with its length so that iteration code can "jump" over it
        kvStore[keyPos] = null;
        kvStore[keyPos + 1] = null;
        // If removing the last kvStore entry, reset kvStoreInsertionPos before it to avoid extra tombstones
        if (keyPos + 2 == kvStoreInsertionPos) {
            kvStoreInsertionPos = keyPos;
        }

        hash.size--;

        assert verify(hash);
        return deletedValue;
    }

    private int firstNonNullKeyPosFromEnd(InlinedLoopConditionProfile nonNullKeyNotYetFound, Node node) {
        int lastKeyPos = kvStoreInsertionPos - 2;
        while (nonNullKeyNotYetFound.profile(node,
                lastKeyPos > 0 && kvStore[lastKeyPos] == null)) {
            lastKeyPos -= 2;
        }
        assert kvStore[lastKeyPos] != null;
        return lastKeyPos;
    }

    private int firstNonNullKeyPosFromBeginning(InlinedLoopConditionProfile nonNullKeyNotYetFound, Node node) {
        int firstKeyPos = 0;
        while (nonNullKeyNotYetFound.profile(node,
                firstKeyPos < kvStoreInsertionPos && kvStore[firstKeyPos] == null)) {
            firstKeyPos += 2;
        }
        assert firstKeyPos < kvStoreInsertionPos;
        return firstKeyPos;
    }

    private CompactHashStore copy() {
        return new CompactHashStore(index.clone(), kvStore.clone(), kvStoreInsertionPos, indexGrowthThreshold);
    }

    /** @param hashCode a hash code
     * @param indexLength the length of the index array
     * @return An even index ranging from 0 to max-1 */
    private static int indexPosFromHashCode(int hashCode, int indexLength) {
        // x & (m - 1) is equivalent to x % m if m is a power of 2 (which index.length is)
        // x & (m - 2) is equivalent to doing x % m then turning off the lowest bit to ensure the result is even
        return hashCode & (indexLength - 2);
    }

    /** @param pos current position
     * @param indexLength the length of the index array
     * @return the circularly next position after the current position */
    private static int incrementIndexPos(int pos, int indexLength) {
        return (pos + 2) & (indexLength - 1);
    }

    public void getAdjacentObjects(Set<Object> reachable) {
        for (int i = 0; i < kvStoreInsertionPos; i += 2) {
            if (kvStore[i] != null) {
                ObjectGraph.addProperty(reachable, kvStore[i]);
                ObjectGraph.addProperty(reachable, kvStore[i + 1]);
            }
        }
    }

    /** Given a key and its hash, returns the index pos where this key's hashCode is found. If not found, returns where
     * it could be inserted if insert is true and KEY_NOT_FOUND otherwise. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class GetIndexPosForKeyNode extends RubyBaseNode {

        public abstract int execute(Node node, Object key, int hash, boolean compareByIdentity, int[] index,
                Object[] kvStore, boolean insert);

        @Specialization
        static int findIndexPos(
                Node node,
                Object key,
                int hash,
                boolean compareByIdentity,
                int[] index,
                Object[] kvStore,
                boolean insert,
                @Cached CompareHashKeysNode.AssumingEqualHashes compareHashKeysNode,
                @Cached @Exclusive InlinedConditionProfile unused,
                @Cached @Exclusive InlinedConditionProfile sameHash) {
            CompilerAsserts.partialEvaluationConstant(insert);

            int startPos = indexPosFromHashCode(hash, index.length);
            int indexPos = startPos;
            while (true) {
                int valuePos = indexPosToValuePos(index, indexPos);
                if (unused.profile(node, valuePos == INDEX_SLOT_UNUSED)) {
                    return insert ? indexPos : KEY_NOT_FOUND;
                } else if (sameHash.profile(node, index[indexPos] == hash) &&
                        compareHashKeysNode.execute(node, compareByIdentity, key, kvStore[valuePos - 1])) { // keyPos == valuePos - 1
                    return indexPos;
                }
                indexPos = incrementIndexPos(indexPos, index.length);
                assert indexPos != startPos;
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class GetIndexPosFromKeyPosNode extends RubyBaseNode {

        public abstract int execute(Node node, int hash, int keyPos, int[] index);

        @Specialization
        static int findIndexPos(Node node, int hash, int keyPos, int[] index,
                @Cached @Exclusive InlinedConditionProfile unused,
                @Cached @Exclusive InlinedConditionProfile found) {
            int valuePosToSearch = keyPos + 1;
            int startPos = indexPosFromHashCode(hash, index.length);
            int indexPos = startPos;
            while (true) {
                int valuePos = indexPosToValuePos(index, indexPos);
                if (unused.profile(node, valuePos == INDEX_SLOT_UNUSED)) {
                    // Keep going, this means the hash of the key changed
                } else if (found.profile(node, valuePos == valuePosToSearch)) {
                    return indexPos;
                }
                indexPos = incrementIndexPos(indexPos, index.length);
                assert indexPos != startPos;
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class SetKvAtNode extends RubyBaseNode {

        public abstract boolean execute(Node node, RubyHash hash, CompactHashStore store, int indexPos, int keyPos,
                int keyHash, Object key, Object value);

        // key already exist, very fast because there is no need to touch the index
        @Specialization(guards = "keyPos >= 0")
        static boolean keyAlreadyExistsWithDifferentValue(
                Node node,
                RubyHash hash,
                CompactHashStore store,
                int indexPos,
                int keyPos,
                int keyHash,
                Object key,
                Object value) {
            store.kvStore[keyPos + 1] = value;
            return false;
        }

        // setting a new key is more expensive
        @SuppressFBWarnings("IP_PARAMETER_IS_DEAD_BUT_OVERWRITTEN")
        @Specialization(guards = "keyPos < 0")
        static boolean keyDoesntExist(
                Node node,
                RubyHash hash,
                CompactHashStore store,
                int indexPos,
                int keyPos,
                int keyHash,
                Object key,
                Object value,
                @Cached @Exclusive InlinedConditionProfile kvResizingIsNeeded,
                @Cached @Exclusive InlinedConditionProfile indexResizingIsNeeded) {
            if (kvResizingIsNeeded.profile(node, store.kvStoreInsertionPos >= store.kvStore.length)) {
                resizeKvStore(store);
            }

            keyPos = store.kvStoreInsertionPos;
            insertIntoKv(store, keyPos, key, value);

            assert store.index[indexPos + 1] <= 0;
            store.index[indexPos] = keyHash;
            store.index[indexPos + 1] = keyPos + 1;

            hash.size++;

            if (indexResizingIsNeeded.profile(node, hash.size >= store.indexGrowthThreshold)) {
                // Resize the index array after insertion, as it invalidates indexPos
                resizeIndex(store);
            }

            return true;
        }

        private static void insertIntoIndex(int hashCode, int valuePos, int[] index) {
            int pos = indexPosFromHashCode(hashCode, index.length);

            while (index[pos + 1] > INDEX_SLOT_UNUSED) {
                pos = incrementIndexPos(pos, index.length);
            }

            index[pos] = hashCode;
            index[pos + 1] = valuePos;
        }

        private static void insertIntoKv(CompactHashStore store, int keyPos, Object key, Object value) {
            store.kvStore[keyPos] = key;
            store.kvStore[keyPos + 1] = value;
            store.kvStoreInsertionPos = keyPos + 2;
        }

        @TruffleBoundary
        private static void resizeIndex(CompactHashStore store) {
            int[] oldIndex = store.index;
            int[] newIndex = new int[2 * oldIndex.length];
            int newIndexCapacity = newIndex.length >> 1;

            int i = 0;
            for (; i < oldIndex.length; i += 2) {
                int hash = oldIndex[i];
                int valuePos = oldIndex[i + 1];

                if (valuePos > INDEX_SLOT_UNUSED) {
                    insertIntoIndex(hash, valuePos, newIndex);
                }
            }

            store.index = newIndex;
            store.indexGrowthThreshold = (int) (newIndexCapacity * THRESHOLD_LOAD_FACTOR_FOR_INDEX_REBUILD);
        }

        private static void resizeKvStore(CompactHashStore store) {
            store.kvStore = ArrayUtils.grow(store.kvStore, 2 * store.kvStore.length);
        }
    }

    /** For promoting from packed to compact */
    void insertHashKeyValue(int hashCode, Object key, Object value) {
        int keyPos = kvStoreInsertionPos;
        int valuePos = keyPos + 1;
        SetKvAtNode.insertIntoKv(this, keyPos, key, value);
        SetKvAtNode.insertIntoIndex(hashCode, valuePos, index);
    }

    public static final class CompactHashLiteralNode extends HashLiteralNode {

        @Child HashStoreLibrary hashes;

        public CompactHashLiteralNode(RubyNode[] keyValues) {
            super(keyValues);
            hashes = insert(HashStoreLibrary.createDispatched());
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            CompactHashStore store = new CompactHashStore(getNumberOfEntries());
            RubyHash hash = new RubyHash(coreLibrary().hashClass,
                    getLanguage().hashShape,
                    getContext(),
                    store,
                    0,
                    false);

            for (int i = 0; i < keyValues.length; i += 2) {
                Object key = keyValues[i].execute(frame);
                Object value = keyValues[i + 1].execute(frame);
                hashes.set(store, hash, key, value, false);
            }
            return hash;
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = new CompactHashLiteralNode(cloneUninitialized(keyValues));
            return copy.copyFlags(this);
        }
    }
}
