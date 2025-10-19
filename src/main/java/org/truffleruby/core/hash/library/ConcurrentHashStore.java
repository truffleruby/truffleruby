/*
 * Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash.library;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.LightweightLayoutLock;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.hash.CompareHashKeysNode;
import org.truffleruby.core.hash.ConcurrentEntry;
import org.truffleruby.core.hash.Entry;
import org.truffleruby.core.hash.FreezeHashKeyIfNeededNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.HashingNodes.ToHash;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryWithHashedCallback;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** From <a href="https://eregon.me/blog/research/">Benoit Daloze's PhD</a> and the paper "B. Daloze, A. Tal, S. Marr,
 * H. Mossenbock, E. Petrank. Parallelization of Dynamic Languages: Synchronizing Built-in Collections. In Proceedings
 * of the 2018 Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA), Boston, United
 * States, November 7-9, 2018". <a href="https://dl.acm.org/doi/10.1145/3276478">Freely available here</a>.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>RubyHash#compareByIdentity is only changed inside a LayoutChange, which means all operations will notice it when
 * it happens (either by waiting or retrying).</li>
 * <li>RubyHash#size is only changed under a write lock, that way it's not changing when inside a layout lock.</li>
 * <li>We try to always access RubyHash#size as volatile, but it's hard to do it everywhere.</li>
 * <li>defaultBlock and defaultValue are currently not synchronized, probably they should be volatile for
 * ConcurrentHashStore.</li>
 * </ul>
 */
@ExportLibrary(value = HashStoreLibrary.class)
@GenerateUncached
public final class ConcurrentHashStore {

    private final LightweightLayoutLock lock = new LightweightLayoutLock();
    // mutable here because the lock protects it and also avoids allocating a new ConcurrentHashStore when resizing
    private volatile AtomicReferenceArray<ConcurrentEntry> buckets;
    private final ConcurrentEntry head;
    private final ConcurrentEntry tail;

    private ConcurrentHashStore(int capacity) {
        this.head = new ConcurrentEntry(false, false, null, null, null);
        this.tail = new ConcurrentEntry(false, false, null, null, null);
        head.setNextInSequence(tail);
        tail.setPreviousInSequence(head);
        this.buckets = new AtomicReferenceArray<>(capacity);
    }

    /** Must only be done inside a Layout Change */
    public void setBuckets(AtomicReferenceArray<ConcurrentEntry> buckets) {
        this.buckets = buckets;
    }

    public static ConcurrentHashStore getStore(RubyHash hash) {
        return (ConcurrentHashStore) hash.store;
    }

    /** Returns first non-removed entry, possibly tail */
    public ConcurrentEntry getFirstEntry() {
        return nextNonRemovedEntry(head);
    }

    /** Returns last non-removed entry, possibly head */
    private ConcurrentEntry getLastEntry() {
        return prevNonRemovedEntry(tail);
    }

    private static ConcurrentEntry nextNonRemovedEntry(ConcurrentEntry entry) {
        entry = entry.getNextInSequence();
        if (entry.isRemoved()) {
            entry = entry.getNextInSequence();
        }
        assert !entry.isRemoved();
        return entry;
    }

    private static ConcurrentEntry prevNonRemovedEntry(ConcurrentEntry entry) {
        entry = entry.getPreviousInSequence();
        if (entry.isRemoved()) {
            entry = entry.getPreviousInSequence();
        }
        assert !entry.isRemoved();
        return entry;
    }

    public void getAdjacentObjects(Set<Object> reachable) {
        ConcurrentEntry entry = getFirstEntry();
        while (entry != tail) {
            if (!entry.isRemoved()) {
                ObjectGraph.addProperty(reachable, entry.getKey());
                ObjectGraph.addProperty(reachable, entry.getValue());
            }
            entry = entry.getNextInSequence();
        }
    }

    public static final int INITIAL_CAPACITY = BucketsHashStore.INITIAL_CAPACITY;

    // The general technique to handle removal in a linked list is to first CAS node.next to a
    // removed node with removed.next = next and then CAS node.next to next. Otherwise,
    // concurrent insertions would be lost.
    // From Harris T. - A Pragmatic Implementation of Non-Blocking Linked-List,
    // using an extra Node as a way to mark the next reference as "deleted".

    @TruffleBoundary
    public static void appendInSequence(ConcurrentEntry entry, ConcurrentEntry tail) {
        // last -> tail becomes
        // last -> entry -> tail
        ConcurrentEntry last;
        do {
            last = tail.getPreviousInSequence();
            entry.setPreviousInSequence(last);
        } while (last.isRemoved() || !last.compareAndSetNextInSequence(tail, entry));

        while (!tail.compareAndSetPreviousInSequence(last, entry)) {
            Thread.yield();
        }

        entry.setPublished(true);
    }

    @TruffleBoundary
    public static boolean removeFromSequence(ConcurrentEntry entry) {
        assert !entry.isRemoved() && entry.getKey() != null;
        while (!entry.isPublished()) {
            Thread.yield();
        }

        // First mark as deleted to avoid losing concurrent insertions

        // Block entry -> nextDeleted -> next
        ConcurrentEntry next;
        ConcurrentEntry nextDeleted;
        while (true) {
            next = entry.getNextInSequence();
            if (next.isLock()) {
                // Concurrent delete on next
                while (entry.getNextInSequence().isLock()) {
                    Thread.yield();
                }
            } else if (next.isRemoved()) {
                // when 2 thread concurrently try to remove the same entry
                return false;
            } else {
                nextDeleted = new ConcurrentEntry(true, false, null, next, null);
                if (entry.compareAndSetNextInSequence(next, nextDeleted)) {
                    break;
                }
            }
        }

        // Block prev -> lockPrevDelete -> entry to avoid concurrent adjacent deletes
        ConcurrentEntry prev;
        ConcurrentEntry prevNext;
        final ConcurrentEntry lockPrevDelete = new ConcurrentEntry(true, true, null, entry, null);
        while (true) {
            prev = entry.getPreviousInSequence();
            assert !prev.isRemoved();
            prevNext = prev.getNextInSequence();
            if (prevNext.isRemoved()) {
                // Concurrent delete on prev
                while (entry.getPreviousInSequence() == prev) {
                    Thread.yield();
                }
            } else {
                assert prevNext == entry;
                if (prev.compareAndSetNextInSequence(entry, lockPrevDelete)) {
                    break;
                }
            }
        }

        // Now, nobody can insert or remove between
        // prev -> lockPrevDelete -> entry -> nextDeleted -> next

        // prev -> next
        prev.setNextInSequence(next);

        // Appender can go, but tail.prev link is not yet correct

        // prev <- next
        while (!next.compareAndSetPreviousInSequence(entry, prev)) {
            Thread.yield();
        }

        return true;
    }

    @TruffleBoundary
    public static ConcurrentEntry removeFirstFromSequence(RubyHash hash, ConcurrentEntry tail) {
        // Should we skip to next instead of busy waiting on first?
        final ConcurrentHashStore concurrentHash = ConcurrentHashStore.getStore(hash);
        ConcurrentEntry entry = concurrentHash.getFirstEntry();
        while (entry != tail) {
            if (removeFromSequence(entry)) {
                return entry;
            }

            entry = concurrentHash.getFirstEntry();
        }

        // Empty Hash, nothing to remove
        return null;
    }

    public static boolean insertInLookup(AtomicReferenceArray<ConcurrentEntry> buckets, int index,
            ConcurrentEntry firstEntry, ConcurrentEntry newEntry) {
        assert firstEntry == null || !firstEntry.isRemoved();
        assert BucketsHashStore.getBucketIndex(newEntry.getHashed(), buckets.length()) == index;
        assert firstEntry == null || BucketsHashStore.getBucketIndex(firstEntry.getHashed(), buckets.length()) == index;
        newEntry.setNextInLookup(firstEntry);
        return buckets.compareAndSet(index, firstEntry, newEntry);
    }

    @TruffleBoundary
    public static void removeFromLookup(RubyHash hash, ConcurrentEntry entry,
            AtomicReferenceArray<ConcurrentEntry> store) {
        final int index = BucketsHashStore.getBucketIndex(entry.getHashed(), store.length());

        // Prevent insertions after entry so adjacent concurrent deletes serialize
        ConcurrentEntry next, nextDeleted;
        do {
            next = entry.getNextInLookup();
            assert next == null || !next.isRemoved();
            nextDeleted = new ConcurrentEntry(true, false, null, null, next);
        } while (!entry.compareAndSetNextInLookup(next, nextDeleted));

        while (true) {
            final ConcurrentEntry previousEntry = searchPreviousLookupEntry(hash, store, entry, index);
            if (previousEntry == null) {
                if (store.compareAndSet(index, entry, next)) {
                    break;
                }
            } else if (!previousEntry.isRemoved()) {
                if (previousEntry.compareAndSetNextInLookup(entry, next)) {
                    break;
                }
            } else {
                Thread.yield();
            }
        }
    }

    private static ConcurrentEntry searchPreviousLookupEntry(RubyHash hash, AtomicReferenceArray<ConcurrentEntry> store,
            ConcurrentEntry entry, int index) {
        assert store == ConcurrentHashStore.getStore(hash).buckets;
        assert BucketsHashStore.getBucketIndex(entry.getHashed(), store.length()) == index;

        // ConcurrentEntry keep identity on rehash/resize, so we just need to find it in the bucket
        ConcurrentEntry previousEntry = null;
        ConcurrentEntry e = store.get(index);

        while (e != null) {
            if (e == entry) {
                return previousEntry;
            }

            previousEntry = e;
            e = e.getNextInLookup();
        }

        assert store == ConcurrentHashStore.getStore(hash).buckets;
        assert BucketsHashStore.getBucketIndex(entry.getHashed(), store.length()) == index;

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw previousLookupEntryNotFound(hash, store, entry, index);
    }

    private static AssertionError previousLookupEntryNotFound(RubyHash hash,
            AtomicReferenceArray<ConcurrentEntry> store, ConcurrentEntry entry, int index) {
        ConcurrentEntry e;
        StringBuilder builder = new StringBuilder();
        builder.append('\n');
        e = store.get(index);
        builder.append("Searched for ").append(entry.getKey()).append("(bucket ").append(index).append(") among ")
                .append(e).append(":\n");
        while (e != null) {
            if (!e.isRemoved()) {
                builder.append(e.getKey()).append('\n');
            }
            e = e.getNextInLookup();
        }
        builder.append('\n');
        builder.append("size = ").append(hash.getSizeVolatile()).append('\n');
        for (int i = 0; i < store.length(); i++) {
            builder.append(i).append(":");
            e = store.get(i);
            while (e != null) {
                if (!e.isRemoved()) {
                    builder.append(" ").append(e.getKey());
                }
                e = e.getNextInLookup();
            }
            builder.append('\n');
        }
        builder.append('\n');
        System.err.println(builder);

        throw new AssertionError("Could not find the previous entry in the bucket");
    }

    @TruffleBoundary
    private void resize(RubyHash hash, int newSize) {
        assert verify(hash);
        assert lock.inLayoutChange();

        int bucketsCount = BucketsHashStore.growthCapacityGreaterThan(newSize);
        var newEntries = new AtomicReferenceArray<ConcurrentEntry>(bucketsCount);

        for (ConcurrentEntry entry : iterableEntries(this)) {
            int bucketIndex = BucketsHashStore.getBucketIndex(entry.getHashed(), bucketsCount);

            // Like insertInLookup() but simplified
            entry.setNextInLookup(newEntries.get(bucketIndex));
            newEntries.set(bucketIndex, entry);
        }

        this.setBuckets(newEntries);
        assert verify(hash);
    }

    @TruffleBoundary
    private void doResize(RubyHash hash, int newSize) {
        final long stamp = lock.startLayoutChange();
        try {
            // Check again to make sure another thread did not already resized
            int bucketsCount = ConcurrentHashStore.getStore(hash).buckets.length();
            if (newSize * 4 > bucketsCount * 3) {
                resize(hash, newSize);
            }
        } finally {
            lock.finishLayoutChange(stamp);
        }
    }

    public static BucketsHashStore copyToBucketsHashStore(ConcurrentHashStore fromStore) {
        final Entry[] newEntries = new Entry[fromStore.buckets.length()];

        Entry firstInSequence = null;
        Entry lastInSequence = null;

        for (ConcurrentEntry entry : iterableEntries(fromStore)) {
            Entry newEntry = new Entry(entry.getHashed(), entry.getKey(), entry.getValue());

            int index = BucketsHashStore.getBucketIndex(entry.getHashed(), newEntries.length);

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
        }

        return new BucketsHashStore(newEntries, firstInSequence, lastInSequence);
    }

    @TruffleBoundary
    public static void convertFromOtherStrategy(RubyHash hash) {
        int capacity = Math.max(BucketsHashStore.growthCapacityGreaterThan(hash.size), INITIAL_CAPACITY);
        ConcurrentHashStore concurrentHash = new ConcurrentHashStore(capacity);

        fromOtherStrategyWithNewBuckets(hash, hash, concurrentHash);
        hash.setStore(concurrentHash);
        concurrentHash.verify(hash);
    }

    @TruffleBoundary
    private static void fromOtherStrategyWithNewBuckets(RubyHash from, RubyHash to,
            ConcurrentHashStore concurrentHash) {
        assert SharedObjects.isShared(to);

        RubyLanguage language = RubyLanguage.get(null);
        var newBuckets = concurrentHash.buckets;
        var tail = concurrentHash.tail;

        HashStoreLibrary.getUncached(from).eachEntryHashed(from.store, from,
                (int i, int hashed, Object key, Object value, Object state) -> {
                    SharedObjects.writeBarrier(language, key);
                    SharedObjects.writeBarrier(language, value);

                    // Immediately mark as published as we either have the layout lock or are sharing a Hash
                    ConcurrentEntry newEntry = new ConcurrentEntry(hashed, key, value, true);

                    int index = BucketsHashStore.getBucketIndex(hashed, newBuckets.length());

                    newEntry.setNextInLookup(newBuckets.get(index));
                    newBuckets.set(index, newEntry);

                    var prevLast = tail.getPreviousInSequence();

                    prevLast.setNextInSequence(newEntry);
                    newEntry.setPreviousInSequence(prevLast);

                    newEntry.setNextInSequence(tail);
                    tail.setPreviousInSequence(newEntry);
                }, null);
    }

    public static Iterator<ConcurrentEntry> iterateEntries(ConcurrentHashStore concurrentHash) {
        final ConcurrentEntry first = concurrentHash.getFirstEntry();
        final ConcurrentEntry tail = concurrentHash.tail;

        return new Iterator<>() {

            private ConcurrentEntry entry = first;

            @Override
            public boolean hasNext() {
                return entry != tail;
            }

            @Override
            public ConcurrentEntry next() {
                assert hasNext();

                final ConcurrentEntry current = entry;

                // Goes through "being deleted" entries, much simpler to check
                entry = nextNonRemovedEntry(entry);

                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    public static Iterable<ConcurrentEntry> iterableEntries(ConcurrentHashStore concurrentHash) {
        return () -> iterateEntries(concurrentHash);
    }

    /** The result of looking for an entry (a {@link ConcurrentEntry}) in a Ruby hash. We get the previous entry in the
     * lookup chain for this index until the entry was found, the entry that was found, and the index that was used.
     * There are three possible outcomes for a search.
     * <ul>
     * <li>There is nothing at that index, in which case the entry and previous entry in the chain will be
     * {@code null}</li>
     * <li>There were entries at that index, but none for our key, in which case the entry will be null, but the
     * previous entry will be the last entry in the chain at that index, presumably where we will want to insert our new
     * entry</li>
     * <li>An entry was found for our key, in which case the entry will be the one correspond to the key, and the
     * previous entry will be the one in the entry chain before that one</li>
     * </ul>
     */
    record ConcurrentHashLookupResult(
            AtomicReferenceArray<ConcurrentEntry> buckets,
            int hashed,
            int index,
            ConcurrentEntry previousEntry,
            ConcurrentEntry entry) {

    }

    @GenerateUncached
    abstract static class ConcurrentLookupEntryNode extends RubyBaseNode {

        public abstract ConcurrentHashLookupResult execute(RubyHash hash, Object key);

        @Specialization
        ConcurrentHashLookupResult lookup(RubyHash hash, Object key,
                @Bind Node node,
                @Cached HashingNodes.ToHash hashNode,
                @Cached CompareHashKeysNode compareHashKeysNode,
                @Cached InlinedBranchProfile changedToIdentityHashProfile,
                @Cached InlinedConditionProfile isRemovedProfile,
                @Cached InlinedConditionProfile byIdentityProfile,
                @Cached InlinedConditionProfile getThreadStateProfile,
                @Cached InlinedConditionProfile finishReadProfile) {
            var store = ConcurrentHashStore.getStore(hash);
            assert store.verify(hash);

            boolean compareByIdentity = byIdentityProfile.profile(node, hash.compareByIdentity);
            int hashed = hashNode.execute(key, compareByIdentity);

            var lock = store.lock;
            var fiber = getLanguage().getCurrentFiber();
            var threadState = fiber.getLayoutLockThreadState(lock, getThreadStateProfile, node);
            ConcurrentHashLookupResult result;
            do {
                result = doLookup(hash, store, key, compareByIdentity, hashed, hashNode, compareHashKeysNode,
                        changedToIdentityHashProfile, isRemovedProfile, node);
            } while (!lock.finishRead(threadState, finishReadProfile, node));

            return result;
        }

        private ConcurrentHashLookupResult doLookup(RubyHash hash, ConcurrentHashStore store, Object key,
                boolean compareByIdentity, int hashed,
                ToHash hashNode,
                CompareHashKeysNode compareHashKeysNode,
                InlinedBranchProfile changedToIdentityHashProfile,
                InlinedConditionProfile isRemovedProfile,
                Node node) {
            if (hash.compareByIdentity != compareByIdentity) {
                changedToIdentityHashProfile.enter(node);
                compareByIdentity = true;
                hashed = hashNode.execute(key, compareByIdentity);
            }

            var entries = store.buckets;
            int index = BucketsHashStore.getBucketIndex(hashed, entries.length());
            ConcurrentEntry firstEntry = entries.get(index);

            ConcurrentEntry entry = firstEntry;
            ConcurrentEntry previousEntry = null;

            while (entry != null) {
                if (!isRemovedProfile.profile(node, entry.isRemoved())) {
                    int otherHashed = entry.getHashed();
                    if (compareHashKeysNode.execute(node, compareByIdentity, key, hashed, entry.getKey(),
                            otherHashed)) {
                        return new ConcurrentHashLookupResult(entries, hashed, index, previousEntry, entry);
                    }
                }

                previousEntry = entry;
                entry = entry.getNextInLookup();
            }

            return new ConcurrentHashLookupResult(entries, hashed, index, firstEntry, null);
        }

    }

    @ExportMessage
    Object lookupOrDefault(Frame frame, RubyHash hash, Object key, PEBiFunction defaultNode,
            @Cached @Shared ConcurrentLookupEntryNode lookupEntryNode,
            @Cached @Exclusive InlinedConditionProfile found,
            @Bind Node node) {
        assert verify(hash);
        var hashLookupResult = lookupEntryNode.execute(hash, key);

        if (found.profile(node, hashLookupResult.entry != null)) {
            return hashLookupResult.entry.getValue();
        }

        return defaultNode.accept(frame, hash, key);
    }


    @GenerateUncached
    abstract static class SetNode extends RubyBaseNode {

        public abstract boolean execute(RubyHash hash, Object originalKey, Object value, boolean byIdentity);

        @Specialization
        boolean set(RubyHash hash, Object originalKey, Object value, boolean byIdentity,
                @Bind Node node,
                @Cached FreezeHashKeyIfNeededNode freezeHashKeyIfNeeded,
                @Cached InlinedConditionProfile getThreadStateProfile,
                @Cached InlinedConditionProfile startWriteProfile,
                @Cached InlinedConditionProfile foundProfile,
                @Cached InlinedConditionProfile insertionProfile,
                @Cached InlinedConditionProfile resizeProfile,
                @Cached ConcurrentLookupEntryNode lookupEntryNode,
                @Cached WriteBarrierNode writeBarrierNode,
                @Cached InlinedConditionProfile sameBucketsProfile) {
            assert HashStoreLibrary.verify(hash);
            final Object key = freezeHashKeyIfNeeded.executeFreezeIfNeeded(node, originalKey, byIdentity);
            writeBarrierNode.execute(node, value);

            var store = ConcurrentHashStore.getStore(hash);
            var lock = store.lock;
            var fiber = RubyLanguage.get(node).getCurrentFiber();
            var threadState = fiber.getLayoutLockThreadState(lock, getThreadStateProfile, node);

            while (true) {
                var result = lookupEntryNode.execute(hash, key);
                var entry = result.entry;

                if (foundProfile.profile(node, entry == null)) {
                    writeBarrierNode.execute(node, key);

                    var firstEntry = result.previousEntry;
                    var newEntry = new ConcurrentEntry(result.hashed, key, value, false);
                    var tail = store.tail;
                    newEntry.setNextInSequence(tail);

                    // Insert in the lookup chain
                    final int bucketsCount;
                    final int newSize;
                    lock.startWrite(threadState, startWriteProfile, node);
                    try {
                        boolean success;
                        var buckets = store.buckets;
                        if (sameBucketsProfile.profile(node, buckets == result.buckets)) {
                            success = insertInLookup(buckets, result.index, firstEntry, newEntry);
                        } else {
                            // the buckets changed between the lookup and now, retry
                            success = false;
                        }

                        if (!insertionProfile.profile(node, success)) {
                            // An entry got inserted in this bucket concurrently, retry
                            continue;
                        }

                        // Increment size
                        newSize = hash.incrementAndGetSize();
                        bucketsCount = buckets.length();

                        // Insert in the sequence chain
                        appendInSequence(newEntry, tail);
                    } finally {
                        lock.finishWrite(threadState);
                    }

                    if (resizeProfile.profile(node, newSize * 4 > bucketsCount * 3)) {
                        store.doResize(hash, newSize);
                    }

                    assert HashStoreLibrary.verify(hash);
                    return true;
                } else {
                    // We need a write lock because rehash() changes uses new ConcurrentEntry instances,
                    // so we need to prevent layout changes concurrent to our write to the entry to ensure that write cannot be lost.
                    // OTOH, if that entry is removed concurrently by Hash#delete that's fine,
                    // they executed concurrently and the ordering ([]=; delete;) is acceptable.
                    lock.startWrite(threadState, startWriteProfile, node);
                    try {
                        if (sameBucketsProfile.profile(node, store.buckets == result.buckets)) {
                            entry.setValue(value);
                        } else {
                            continue; // the buckets changed between the lookup and now, retry
                        }
                    } finally {
                        lock.finishWrite(threadState);
                    }

                    assert HashStoreLibrary.verify(hash);
                    return false;
                }
            }
        }
    }

    @ExportMessage
    boolean set(RubyHash hash, Object originalKey, Object value, boolean byIdentity,
            @Cached @Exclusive SetNode setNode) {
        return setNode.execute(hash, originalKey, value, byIdentity);
    }

    @ExportMessage
    void clear(RubyHash hash) {
        assert verify(hash);
        final long stamp = lock.startLayoutChange();
        try {
            clearInsideLayoutChange(hash, INITIAL_CAPACITY);
        } finally {
            lock.finishLayoutChange(stamp);
        }
        assert verify(hash);
    }

    private void clearInsideLayoutChange(RubyHash hash, int capacity) {
        assert lock.inLayoutChange();
        head.setNextInSequence(tail);
        tail.setPreviousInSequence(head);
        setBuckets(new AtomicReferenceArray<>(capacity));
        hash.size = 0;
    }

    @ExportMessage
    Object delete(RubyHash hash, Object key,
            @Bind Node node,
            @Cached @Shared ConcurrentLookupEntryNode lookupEntryNode,
            @Cached @Exclusive InlinedConditionProfile missing,
            @Cached @Exclusive InlinedConditionProfile getThreadStateProfile,
            @Cached @Exclusive InlinedConditionProfile startWriteProfile) {
        assert verify(hash);
        final ConcurrentHashLookupResult hashLookupResult = lookupEntryNode.execute(hash, key);
        final ConcurrentEntry entry = hashLookupResult.entry;

        if (missing.profile(node, entry == null)) {
            return null;
        }

        return removeEntry(hash, entry, getThreadStateProfile, startWriteProfile, node);
    }

    private static Object removeEntry(RubyHash hash, ConcurrentEntry entry,
            InlinedConditionProfile getThreadStateProfile, InlinedConditionProfile startWriteProfile, Node node) {
        boolean removed;
        var store = ConcurrentHashStore.getStore(hash);
        var lock = store.lock;
        var fiber = RubyLanguage.get(node).getCurrentFiber();
        var threadState = fiber.getLayoutLockThreadState(lock, getThreadStateProfile, node);
        lock.startWrite(threadState, startWriteProfile, node);
        try {
            // Remove from the sequence chain
            removed = removeFromSequence(entry);
            if (removed) {
                // Decrement size
                hash.decrementSize();

                // Remove from the lookup chain
                removeFromLookup(hash, entry, store.buckets);
            }
        } finally {
            lock.finishWrite(threadState);
        }

        if (!removed) {
            return null;
        }

        assert HashStoreLibrary.verify(hash);
        return entry.getValue();
    }

    @ExportMessage
    Object deleteLast(RubyHash hash, Object key,
            @Bind Node node,
            @Cached @Exclusive InlinedConditionProfile getThreadStateProfile,
            @Cached @Exclusive InlinedConditionProfile startWriteProfile) {
        assert verify(hash);
        var lastEntry = getLastEntry();

        if (key != lastEntry.getKey()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives
                    .shouldNotReachHere("The last key was not " + key + " as expected but was " + lastEntry.getKey());
        }

        return removeEntry(hash, lastEntry, getThreadStateProfile, startWriteProfile, node);
    }

    @ExportMessage
    Object eachEntry(RubyHash hash, EachEntryCallback callback, Object state,
            @Bind Node node,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        assert verify(hash);
        ConcurrentEntry first = getFirstEntry();
        ConcurrentEntry tail = this.tail;

        int i = 0;
        ConcurrentEntry entry = first;
        try {
            while (loopProfile.inject(entry != tail)) {
                callback.accept(i++, entry.getKey(), entry.getValue(), state);

                entry = nextNonRemovedEntry(entry);

                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
        return state;
    }

    @ExportMessage
    Object eachEntryHashed(RubyHash hash, EachEntryWithHashedCallback callback, Object state,
            @Bind Node node,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        assert verify(hash);
        ConcurrentEntry first = getFirstEntry();
        ConcurrentEntry tail = this.tail;

        int i = 0;
        ConcurrentEntry entry = first;
        try {
            while (loopProfile.inject(entry != tail)) {
                callback.accept(i++, entry.getHashed(), entry.getKey(), entry.getValue(), state);

                entry = nextNonRemovedEntry(entry);

                TruffleSafepoint.poll(node);
            }
        } finally {
            RubyBaseNode.profileAndReportLoopCount(node, loopProfile, i);
        }
        return state;
    }

    @ExportMessage
    Object eachEntrySafe(RubyHash hash, EachEntryCallback callback, Object state,
            @CachedLibrary("this") HashStoreLibrary self) {
        return self.eachEntry(this, hash, callback, state);
    }

    @TruffleBoundary
    public void replace(RubyHash hash, RubyHash from) {
        assert verify(hash);

        final long stamp = lock.startLayoutChange();
        try {
            int capacity = Math.max(BucketsHashStore.growthCapacityGreaterThan(from.size), INITIAL_CAPACITY);
            clearInsideLayoutChange(hash, capacity);

            fromOtherStrategyWithNewBuckets(from, hash, this);
            hash.size = from.size;
            hash.copyFieldsExceptStoreAndSize(from);
        } finally {
            lock.finishLayoutChange(stamp);
        }

        assert verify(hash);
    }

    @ExportMessage
    Object copyStore() {
        // Copy from is shared to not-shared, it's an opportunity to return an unshared Hash.
        return copyToBucketsHashStore(this);
    }

    @ExportMessage
    RubyArray shift(RubyHash hash,
            @Bind Node node,
            @Cached @Exclusive InlinedConditionProfile getThreadStateProfile,
            @Cached @Exclusive InlinedConditionProfile startWriteProfile) {
        assert verify(hash);

        var language = RubyLanguage.get(node);
        var tail = this.tail;

        ConcurrentEntry entry;
        var fiber = language.getCurrentFiber();
        var threadState = fiber.getLayoutLockThreadState(lock, getThreadStateProfile, node);

        lock.startWrite(threadState, startWriteProfile, node);
        try {
            // Remove from the sequence chain
            entry = removeFirstFromSequence(hash, tail);
            if (entry != null) {
                // Decrement size
                hash.decrementSize();

                // Remove from the lookup chain
                removeFromLookup(hash, entry, buckets);
            }
        } finally {
            lock.finishWrite(threadState);
        }

        if (entry == null) {
            return null;
        }

        assert verify(hash);
        final RubyContext context = RubyContext.get(node);
        return ArrayHelpers.createArray(context, language, new Object[]{ entry.getKey(), entry.getValue() });
    }

    @ExportMessage
    void rehash(RubyHash hash,
            @Cached @Exclusive SetNode setNode) {
        assert verify(hash);

        final long stamp = lock.startLayoutChange();
        try {
            if (hash.compareByIdentity) { // check inside the lock to be sure
                return;
            }

            rehashInLayoutChange(hash, setNode);
        } finally {
            lock.finishLayoutChange(stamp);
        }

        assert verify(hash);
    }

    private void rehashInLayoutChange(RubyHash hash, SetNode setNode) {
        assert lock.inLayoutChange();

        if (hash.size == 0) {
            assert head.getNextInSequence() == tail;
            assert tail.getPreviousInSequence() == head;
            return; // nothing to rehash
        }

        // We need to be inside a Layout Change while rehashing, so no lookups fail unexpectedly due to being in the middle of rehashing.
        // But we can't safely call #hash inside a layout change as that could do anything and lead to a deadlock on this Hash's lock.
        // So we rehash on a copy, which has its own lock, and then adopt the resulting buckets.
        var language = RubyLanguage.get(setNode);
        var context = RubyContext.get(setNode);
        var copyStore = new ConcurrentHashStore(buckets.length());
        RubyHash copy = new RubyHash(context.getCoreLibrary().hashClass, language.sharedHashShape, context,
                copyStore, 0, hash.ruby2_keywords);
        copy.copyFieldsExceptStoreAndSize(hash);

        var entry = getFirstEntry();
        assert !entry.isSentinel();
        while (entry != tail) {
            setNode.execute(copy, entry.getKey(), entry.getValue(), copy.compareByIdentity);
            entry = nextNonRemovedEntry(entry);
        }

        var newBuckets = copyStore.buckets;
        int size = copy.size;
        var first = copyStore.getFirstEntry();
        var last = copyStore.getLastEntry();

        assert size > 0;
        assert !first.isSentinel();
        assert !last.isSentinel();

        // Empty copy in case the user holds onto it
        copyStore.clear(copy);

        first.setPreviousInSequence(head);
        head.setNextInSequence(first);

        last.setNextInSequence(tail);
        tail.setPreviousInSequence(last);

        setBuckets(newBuckets);
        hash.size = size;
    }

    @ExportMessage
    void becomeCompareByIdentityAndRehash(RubyHash hash,
            @Cached @Exclusive SetNode setNode) {
        assert verify(hash);

        final long stamp = lock.startLayoutChange();
        try {
            hash.compareByIdentity = true;
            rehashInLayoutChange(hash, setNode);
        } finally {
            lock.finishLayoutChange(stamp);
        }

        assert verify(hash);
    }

    @TruffleBoundary
    @ExportMessage
    public boolean verify(RubyHash hash) {
        assert hash.store == this;
        assert SharedObjects.isShared(hash);

        assert head.isSentinel();
        assert head.getKey() == null;
        assert head.getValue() == null;
        assert head.getNextInLookup() == null;
        assert head.getNextInSequence() != null;
        assert head.getPreviousInSequence() == null;

        assert tail.isSentinel();
        assert tail.getKey() == null;
        assert tail.getValue() == null;
        assert tail.getNextInLookup() == null;
        assert tail.getNextInSequence() == null;
        assert tail.getPreviousInSequence() != null;

        ConcurrentEntry entry = head.getNextInSequence();
        while (entry != tail) {
            assert !entry.isSentinel() : entry;
            if (!entry.isRemoved()) {
                assert SharedObjects.assertPropagateSharing(hash, entry.getKey());
                assert SharedObjects.assertPropagateSharing(hash, entry.getValue());

                ConcurrentEntry nextInLookup = entry.getNextInLookup();
                while (nextInLookup != null) {
                    assert nextInLookup != entry;
                    nextInLookup = nextInLookup.getNextInLookup();
                }

                ConcurrentEntry next = nextNonRemovedEntry(entry);
                ConcurrentEntry nextPrev = prevNonRemovedEntry(next);
                // assert entry == nextPrev; might not hold if emtry is removed concurrently
                assert nextPrev != null;
            }
            entry = entry.getNextInSequence();
        }

        return true;
    }

    public static final class ConcurrentHashLiteralNode extends HashLiteralNode {

        @Child HashStoreLibrary hashes;
        private final int bucketsCount;

        public ConcurrentHashLiteralNode(RubyNode[] keyValues, RubyLanguage language) {
            super(keyValues);
            bucketsCount = BucketsHashStore.growthCapacityGreaterThan(getNumberOfEntries());
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
                    getLanguage().sharedHashShape,
                    getContext(),
                    new ConcurrentHashStore(bucketsCount),
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
            var copy = new ConcurrentHashLiteralNode(cloneUninitialized(keyValues), getLanguage());
            return copy.copyFlags(this);
        }

    }

}
