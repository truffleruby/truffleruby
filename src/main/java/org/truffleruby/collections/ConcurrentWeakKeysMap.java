/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.collections;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** A concurrent thread-safe map with weak keys. Keys cannot be null. This map currently assumes keys never need to be
 * re-hashed with ReHashable, which means no Ruby ObjectSpace::WeakKeyMap instance should be part of the pre-initialized
 * context. ConcurrentWeakKeysMap and ConcurrentWeakSet with keys whose hashCode() does not depend on the Ruby random
 * seed are fine as part of the pre-initialized context. */
public class ConcurrentWeakKeysMap<Key, Value> {

    protected final ConcurrentHashMap<WeakReference<Key>, Value> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<Key> referenceQueue = new ReferenceQueue<>();

    @TruffleBoundary
    public ConcurrentWeakKeysMap() {
    }

    @TruffleBoundary
    public void clear() {
        map.clear();
    }

    @TruffleBoundary
    public boolean containsKey(Key key) {
        removeStaleEntries();
        return map.containsKey(buildWeakReference(key));
    }

    @TruffleBoundary
    public Value get(Key key) {
        removeStaleEntries();
        return map.get(buildWeakReference(key));
    }

    /** Sets the value in the cache, always returns the old value. */
    @TruffleBoundary
    public Value put(Key key, Value value) {
        removeStaleEntries();
        var ref = buildWeakReference(key, referenceQueue);
        return map.put(ref, value);
    }

    @TruffleBoundary
    public int size() {
        removeStaleEntries();
        int size = 0;

        // Filter out null entries.
        for (var e : map.keySet()) {
            final Key key = e.get();
            if (key != null) {
                ++size;
            }
        }

        return size;
    }

    @TruffleBoundary
    public Collection<Key> keys() {
        removeStaleEntries();
        final Collection<Key> keys = new ArrayList<>(map.size());

        // Filter out null entries.
        for (var e : map.keySet()) {
            final Key key = e.get();
            if (key != null) {
                keys.add(key);
            }
        }

        return keys;
    }

    @TruffleBoundary
    public Value remove(Key key) {
        removeStaleEntries();
        return map.remove(buildWeakReference(key));
    }

    /** It's important a WeakReference that is built returns true for {@code ref.equals(ref)} even if the reference is
     * cleared. */
    protected WeakReference<Key> buildWeakReference(Key key) {
        return new WeakKeyReference<>(key);
    }

    /** It's important a WeakReference that is built returns true for {@code ref.equals(ref)} even if the reference is
     * cleared. */
    protected WeakReference<Key> buildWeakReference(Key key, ReferenceQueue<Key> referenceQueue) {
        return new WeakKeyReference<>(key, referenceQueue);
    }

    /** Attempts to remove map entries whose values have been made unreachable by the GC.
     * <p>
     * This relies on the underlying {@link WeakReference} instance being enqueued to the {@link #referenceQueue} queue.
     * It is possible that the map still contains {@link WeakReference} instances whose key has been nulled out after a
     * call to this method (the reference not having been enqueued yet)! */
    protected void removeStaleEntries() {
        WeakReference<?> ref;
        while ((ref = (WeakReference<?>) referenceQueue.poll()) != null) {
            // Here ref.get() is null, so it will not remove a new key-value pair with the same key
            // as that is a different WeakReference instance.
            map.remove(ref);
        }
    }

    /** A default implementation of a key that wraps in a user-provided key. Compares keys by
     * {@link Object#equals(Object)} */
    protected static final class WeakKeyReference<Key> extends WeakReference<Key> {
        private final int hashCode;

        public WeakKeyReference(Key key) {
            super(key);
            Objects.requireNonNull(key);
            this.hashCode = key.hashCode();
        }

        public WeakKeyReference(Key key, ReferenceQueue<? super Key> queue) {
            super(key, queue);
            Objects.requireNonNull(key);
            this.hashCode = key.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof WeakKeyReference<?> ref) {
                Key key = get();
                Object otherKey = ref.get();
                return key != null && otherKey != null && key.equals(otherKey);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

}
