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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.hash.CompareHashKeysNode;
import org.truffleruby.core.hash.Entry;
import org.truffleruby.core.hash.FreezeHashKeyIfNeededNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.hash.HashLookupResult;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryWithHashedCallback;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.shared.SharedObjects;

import java.util.Set;

@ExportLibrary(value = HashStoreLibrary.class)
@GenerateUncached
public final class BucketsHashStore {

    private final Entry[] entries;
    private Entry firstInSequence;
    private Entry lastInSequence;

    public BucketsHashStore(Entry[] entries, Entry firstInSequence, Entry lastInSequence) {
        this.entries = entries;
        this.firstInSequence = firstInSequence;
        this.lastInSequence = lastInSequence;
    }

    // region Constants

    // If the size is more than this fraction of the number of buckets, resize
    private static final double LOAD_FACTOR = 0.75;

    // Create this many more buckets than there are entries when resizing or creating from scratch
    private static final int OVERALLOCATE_FACTOR = 4;

    private static final int SIGN_BIT_MASK = ~(1 << 31);

    // Prime numbers used in MRI
    private static final int[] CAPACITIES = {
            8 + 3,
            16 + 3,
            32 + 5,
            64 + 3,
            128 + 3,
            256 + 27,
            512 + 9,
            1024 + 9,
            2048 + 5,
            4096 + 3,
            8192 + 27,
            16384 + 43,
            32768 + 3,
            65536 + 45,
            131072 + 29,
            262144 + 3,
            524288 + 21,
            1048576 + 7,
            2097152 + 17,
            4194304 + 15,
            8388608 + 9,
            16777216 + 43,
            33554432 + 35,
            67108864 + 15,
            134217728 + 29,
            268435456 + 3,
            536870912 + 11,
            1073741824 + 85
    };

    public static final int INITIAL_CAPACITY = CAPACITIES[0];

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final int MAX_ENTRIES = (int) (MAX_ARRAY_SIZE * LOAD_FACTOR);

    // endregion
    // region Utilities

    @TruffleBoundary
    public static int growthCapacityGreaterThan(int size) {
        int buckets = 0;
        for (int capacity : CAPACITIES) {
            if (capacity > size) {
                buckets = capacity * OVERALLOCATE_FACTOR;
                break;
            }
        }

        if (buckets > 0) {
            assert buckets * LOAD_FACTOR > size;
            return buckets;
        } else if (size < MAX_ENTRIES) {
            return MAX_ARRAY_SIZE;
        } else {
            throw new OutOfMemoryError("too big Hash: " + size + " entries");
        }
    }

    static int getBucketIndex(int hashed, int bucketsCount) {
        return (hashed & SIGN_BIT_MASK) % bucketsCount;
    }

    @TruffleBoundary
    private void resize(RubyHash hash, int size) {
        final int bucketsCount = growthCapacityGreaterThan(size);
        final Entry[] newEntries = new Entry[bucketsCount];

        final Entry firstInSequence = this.firstInSequence;
        Entry lastInSequence = null;
        Entry entry = firstInSequence;

        while (entry != null) {
            final int bucketIndex = getBucketIndex(entry.getHashed(), bucketsCount);
            appendToLookupChain(newEntries, entry, bucketIndex);

            entry.setNextInLookup(null);
            lastInSequence = entry;
            entry = entry.getNextInSequence();
        }

        hash.setStore(new BucketsHashStore(newEntries, firstInSequence, lastInSequence));
    }

    public void getAdjacentObjects(Set<Object> reachable) {
        Entry entry = this.firstInSequence;
        while (entry != null) {
            ObjectGraph.addProperty(reachable, entry.getKey());
            ObjectGraph.addProperty(reachable, entry.getValue());
            entry = entry.getNextInSequence();
        }
    }

    private void removeFromSequenceChain(Entry entry) {
        final Entry previousInSequence = entry.getPreviousInSequence();
        final Entry nextInSequence = entry.getNextInSequence();

        if (previousInSequence == null) {
            assert this.firstInSequence == entry;
            this.firstInSequence = nextInSequence;
        } else {
            assert this.firstInSequence != entry;
            previousInSequence.setNextInSequence(nextInSequence);
        }

        if (nextInSequence == null) {
            assert this.lastInSequence == entry;
            this.lastInSequence = previousInSequence;
        } else {
            assert this.lastInSequence != entry;
            nextInSequence.setPreviousInSequence(previousInSequence);
        }
    }

    private static void removeFromLookupChain(Entry[] entries, int index, Entry entry, Entry previousEntry) {
        if (previousEntry == null) {
            entries[index] = entry.getNextInLookup();
        } else {
            previousEntry.setNextInLookup(entry.getNextInLookup());
        }
    }

    static void appendToLookupChain(Entry[] entries, Entry entry, int bucketIndex) {
        Entry previousInLookup = entries[bucketIndex];

        if (previousInLookup == null) {
            entries[bucketIndex] = entry;
        } else {
            while (true) {
                final Entry nextInLookup = previousInLookup.getNextInLookup();
                if (nextInLookup == null) {
                    break;
                } else {
                    previousInLookup = nextInLookup;
                }
            }

            previousInLookup.setNextInLookup(entry);
        }
    }

    // endregion
    // region Messages

    @ExportMessage
    protected Object lookupOrDefault(Frame frame, RubyHash hash, Object key, PEBiFunction defaultNode,
            @Cached @Shared LookupEntryNode lookup,
            @Cached @Exclusive InlinedConditionProfile found,
            @Bind Node node) {

        final Entry[] entries = this.entries;
        final HashLookupResult hashLookupResult = lookup.execute(hash, entries, key);

        if (found.profile(node, hashLookupResult.getEntry() != null)) {
            return hashLookupResult.getEntry().getValue();
        }

        return defaultNode.accept(frame, hash, key);
    }

    @ExportMessage
    protected boolean set(RubyHash hash, Object originalKey, Object value, boolean byIdentity,
            @Cached FreezeHashKeyIfNeededNode freezeHashKeyIfNeeded,
            @Cached @Shared LookupEntryNode lookup,
            @Cached @Exclusive InlinedConditionProfile missing,
            @Cached @Exclusive InlinedConditionProfile bucketCollision,
            @Cached @Exclusive InlinedConditionProfile appending,
            @Cached @Exclusive InlinedConditionProfile resize,
            @Bind Node node) {
        assert verify(hash);

        final Object key = freezeHashKeyIfNeeded.executeFreezeIfNeeded(node, originalKey, byIdentity);

        final Entry[] entries = this.entries;
        final HashLookupResult result = lookup.execute(hash, entries, key);
        final Entry entry = result.getEntry();

        if (missing.profile(node, entry == null)) {
            final Entry newEntry = new Entry(result.getHashed(), key, value);

            if (bucketCollision.profile(node, result.getPreviousEntry() == null)) {
                entries[result.getIndex()] = newEntry;
            } else {
                result.getPreviousEntry().setNextInLookup(newEntry);
            }

            final Entry lastInSequence = this.lastInSequence;
            if (appending.profile(node, lastInSequence == null)) {
                this.firstInSequence = newEntry;
            } else {
                lastInSequence.setNextInSequence(newEntry);
                newEntry.setPreviousInSequence(lastInSequence);
            }
            this.lastInSequence = newEntry;

            final int newSize = (hash.size += 1);
            assert verify(hash);

            if (resize.profile(node, newSize / (double) entries.length > LOAD_FACTOR)) {
                resize(hash, newSize);
                assert ((BucketsHashStore) hash.store).verify(hash); // store changed!
            }
            return true;
        } else {
            entry.setValue(value);
            assert verify(hash);
            return false;
        }
    }

    @ExportMessage
    protected Object delete(RubyHash hash, Object key,
            @Cached @Shared LookupEntryNode lookup,
            @Cached @Exclusive InlinedConditionProfile missing,
            @Bind Node node) {
        assert verify(hash);

        final Entry[] entries = this.entries;
        final HashLookupResult lookupResult = lookup.execute(hash, entries, key);
        final Entry entry = lookupResult.getEntry();

        if (missing.profile(node, entry == null)) {
            return null;
        }

        removeFromSequenceChain(entry);
        removeFromLookupChain(entries, lookupResult.getIndex(), entry, lookupResult.getPreviousEntry());
        hash.size -= 1;
        assert verify(hash);
        return entry.getValue();
    }

    @ExportMessage
    protected Object deleteLast(RubyHash hash, Object key,
            @Cached @Exclusive InlinedConditionProfile singleEntry,
            @Bind Node node) {
        assert verify(hash);

        final Entry[] entries = this.entries;
        final Entry lastEntry = this.lastInSequence;
        if (key != lastEntry.getKey()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives
                    .shouldNotReachHere("The last key was not " + key + " as expected but was " + lastEntry.getKey());
        }

        // Lookup previous entry
        final int index = getBucketIndex(lastEntry.getHashed(), entries.length);
        Entry entry = entries[index];
        Entry previousEntry = null;
        while (entry != lastEntry) {
            previousEntry = entry;
            entry = entry.getNextInLookup();
        }
        assert entry.getNextInSequence() == null;

        if (singleEntry.profile(node, this.firstInSequence == entry)) {
            assert entry.getPreviousInSequence() == null;
            this.firstInSequence = null;
            this.lastInSequence = null;
        } else {
            assert entry.getPreviousInSequence() != null;
            final Entry previousInSequence = entry.getPreviousInSequence();
            previousInSequence.setNextInSequence(null);
            this.lastInSequence = previousInSequence;
        }

        removeFromLookupChain(entries, index, entry, previousEntry);
        hash.size -= 1;
        assert verify(hash);
        return entry.getValue();
    }

    @ExportMessage
    protected Object eachEntry(RubyHash hash, EachEntryCallback callback, Object state,
            @Bind Node node,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        assert verify(hash);

        int i = 0;
        Entry entry = this.firstInSequence;
        try {
            while (loopProfile.inject(entry != null)) {
                callback.accept(i++, entry.getKey(), entry.getValue(), state);
                entry = entry.getNextInSequence();
                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
        return state;
    }

    @ExportMessage
    protected Object eachEntryHashed(RubyHash hash, EachEntryWithHashedCallback callback, Object state,
            @Bind Node node,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        // NOTE: cannot assert verify(hash); here because this is called in onShareHook(),
        // and the hash is already shared when that hook is called.

        int i = 0;
        Entry entry = this.firstInSequence;
        try {
            while (loopProfile.inject(entry != null)) {
                callback.accept(i++, entry.getHashed(), entry.getKey(), entry.getValue(), state);
                entry = entry.getNextInSequence();
                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
        return state;
    }

    @ExportMessage
    protected Object eachEntrySafe(RubyHash hash, EachEntryCallback callback, Object state,
            @CachedLibrary("this") HashStoreLibrary self) {
        return self.eachEntry(this, hash, callback, state);
    }

    @ExportMessage
    protected Object copyStore() {
        final Entry[] newEntries = new Entry[entries.length];

        Entry firstInSequence = null;
        Entry lastInSequence = null;
        Entry entry = this.firstInSequence;

        while (entry != null) {
            final Entry newEntry = new Entry(entry.getHashed(), entry.getKey(), entry.getValue());
            final int index = getBucketIndex(entry.getHashed(), newEntries.length);
            newEntry.setNextInLookup(newEntries[index]);
            newEntries[index] = newEntry;
            if (firstInSequence == null) {
                firstInSequence = newEntry;
            }
            if (lastInSequence != null) {
                lastInSequence.setNextInSequence(newEntry);
                newEntry.setPreviousInSequence(lastInSequence);
            }
            lastInSequence = newEntry;
            entry = entry.getNextInSequence();
        }

        return new BucketsHashStore(newEntries, firstInSequence, lastInSequence);
    }

    @ExportMessage
    protected RubyArray shift(RubyHash hash,
            @Bind Node node) {
        assert verify(hash);

        final Entry[] entries = this.entries;
        final Entry first = this.firstInSequence;
        assert first.getPreviousInSequence() == null;

        final Object key = first.getKey();
        final Object value = first.getValue();

        final Entry second = first.getNextInSequence();
        this.firstInSequence = second;
        if (second == null) {
            this.lastInSequence = null;
        } else {
            second.setPreviousInSequence(null);
        }

        final int index = getBucketIndex(first.getHashed(), entries.length);

        Entry previous = null;
        Entry entry = entries[index];
        while (entry != null) {
            if (entry == first) {
                removeFromLookupChain(entries, index, first, previous);
                break;
            }

            previous = entry;
            entry = entry.getNextInLookup();
        }

        hash.size -= 1;

        assert verify(hash);
        final RubyLanguage language = RubyLanguage.get(node);
        final RubyContext context = RubyContext.get(node);
        return ArrayHelpers.createArray(context, language, new Object[]{ key, value });
    }

    @ExportMessage
    protected void rehash(RubyHash hash,
            @CachedLibrary("this") HashStoreLibrary self) {
        assert verify(hash);
        final Entry[] newEntries = new Entry[entries.length];

        Entry entry = this.firstInSequence;
        BucketsHashStore newStore = new BucketsHashStore(newEntries, null, null);
        hash.setStore(newStore);
        hash.size = 0;

        while (entry != null) {
            self.set(newStore, hash, entry.getKey(), entry.getValue(), hash.compareByIdentity);
            entry = entry.getNextInSequence();
        }

        assert newStore.verify(hash);
    }

    @TruffleBoundary
    @ExportMessage
    public boolean verify(RubyHash hash) {
        assert hash.store == this;
        assert !SharedObjects.isShared(hash);

        final Entry[] entries = this.entries;
        final int size = hash.size;
        final Entry firstInSequence = this.firstInSequence;
        final Entry lastInSequence = this.lastInSequence;
        assert lastInSequence == null || lastInSequence.getNextInSequence() == null;

        Entry foundFirst = null;
        Entry foundLast = null;
        int foundSizeBuckets = 0;
        for (Entry entry : entries) {
            while (entry != null) {
                assert entry.getKey() != null;
                assert entry.getValue() != null;
                foundSizeBuckets++;
                if (entry == firstInSequence) {
                    assert foundFirst == null;
                    foundFirst = entry;
                }
                if (entry == lastInSequence) {
                    assert foundLast == null;
                    foundLast = entry;
                }
                entry = entry.getNextInLookup();
            }
        }
        assert foundSizeBuckets == size;
        assert firstInSequence == foundFirst;
        assert lastInSequence == foundLast;

        int foundSizeSequence = 0;
        Entry entry = firstInSequence;
        while (entry != null) {
            foundSizeSequence++;
            if (entry.getNextInSequence() == null) {
                assert entry == lastInSequence;
            } else {
                assert entry.getNextInSequence().getPreviousInSequence() == entry;
            }
            entry = entry.getNextInSequence();
            assert entry != firstInSequence;
        }
        assert foundSizeSequence == size : StringUtils.format("%d %d", foundSizeSequence, size);

        return true;
    }

    // endregion
    // region Nodes

    @GenerateUncached
    abstract static class LookupEntryNode extends RubyBaseNode {

        public abstract HashLookupResult execute(RubyHash hash, Entry[] entries, Object key);

        @Specialization
        HashLookupResult lookup(RubyHash hash, Entry[] entries, Object key,
                @Cached HashingNodes.ToHash hashNode,
                @Cached CompareHashKeysNode compareHashKeysNode,
                @Cached InlinedConditionProfile byIdentityProfile) {
            final boolean compareByIdentity = byIdentityProfile.profile(this, hash.compareByIdentity);
            int hashed = hashNode.execute(key, compareByIdentity);

            final int index = getBucketIndex(hashed, entries.length);
            Entry entry = entries[index];

            Entry previousEntry = null;

            while (entry != null) {
                if (compareHashKeysNode.execute(this, compareByIdentity, key, hashed, entry.getKey(),
                        entry.getHashed())) {
                    return new HashLookupResult(hashed, index, previousEntry, entry);
                }

                previousEntry = entry;
                entry = entry.getNextInLookup();
            }

            return new HashLookupResult(hashed, index, previousEntry, null);
        }

    }

    public static final class BucketHashLiteralNode extends HashLiteralNode {

        @Child HashStoreLibrary hashes;
        private final int bucketsCount;

        public BucketHashLiteralNode(RubyNode[] keyValues) {
            super(keyValues);
            bucketsCount = growthCapacityGreaterThan(getNumberOfEntries());
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            if (hashes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashes = insert(HashStoreLibrary.createDispatched());
            }

            final RubyHash hash = new RubyHash(
                    coreLibrary().hashClass,
                    getLanguage().hashShape,
                    getContext(),
                    new BucketsHashStore(new Entry[bucketsCount], null, null),
                    0,
                    false);

            for (int n = 0; n < keyValues.length; n += 2) {
                final Object key = keyValues[n].execute(frame);
                final Object value = keyValues[n + 1].execute(frame);
                hashes.set(hash.store, hash, key, value, false);
            }

            return hash;
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = new BucketHashLiteralNode(cloneUninitialized(keyValues));
            return copy.copyFlags(this);
        }

    }

    // endregion
}
