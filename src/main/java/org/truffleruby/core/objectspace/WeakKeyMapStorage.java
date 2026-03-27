/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.objectspace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.collections.ConcurrentWeakKeysMap;
import org.truffleruby.core.hash.HashingNodes.ToHashByHashCode;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;

public final class WeakKeyMapStorage extends ConcurrentWeakKeysMap<Object, Object> {

    @TruffleBoundary
    public Object getKey(Object key) {
        removeStaleEntries();

        int hashCode = ToHashByHashCode.executeUncached(key);
        var ref = new RecordingCompareByRubyHashEqlWeakReference(key, hashCode);

        if (map.containsKey(ref)) {
            return Objects.requireNonNull(ref.keyInMap);
        } else {
            return null;
        }
    }

    @Override
    protected WeakReference<Object> buildWeakReference(Object key) {
        int hashCode = ToHashByHashCode.executeUncached(key);
        return new CompareByRubyHashEqlWeakReference(key, hashCode);
    }

    @Override
    protected WeakReference<Object> buildWeakReference(Object key, ReferenceQueue<Object> referenceQueue) {
        int hashCode = ToHashByHashCode.executeUncached(key);
        return new CompareByRubyHashEqlWeakReference(key, referenceQueue, hashCode);
    }

    /** Wraps a key so that it will be hashed and compared according to Ruby Hash key semantics, using #hash and
     * #eql?. */
    private static class CompareByRubyHashEqlWeakReference extends WeakReference<Object> {

        private final int hashCode;

        /** @param hashCode the result of {@link ToHashByHashCode} on the key */
        public CompareByRubyHashEqlWeakReference(Object key, int hashCode) {
            super(key);
            Objects.requireNonNull(key);
            this.hashCode = hashCode;
        }

        /** @param hashCode the result of {@link ToHashByHashCode} on the key */
        public CompareByRubyHashEqlWeakReference(Object key, ReferenceQueue<? super Object> queue, int hashCode) {
            super(key, queue);
            Objects.requireNonNull(key);
            this.hashCode = hashCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (other instanceof CompareByRubyHashEqlWeakReference ref) {
                Object key = get();
                Object otherKey = ref.get();

                // We use an uncached SameOrEqlNode here since we are not in PE-able code
                return key != null && otherKey != null && SameOrEqlNode.executeUncached(key, otherKey);
            } else {
                return false;
            }
        }
    }

    private static final class RecordingCompareByRubyHashEqlWeakReference extends CompareByRubyHashEqlWeakReference {

        public Object keyInMap;

        public RecordingCompareByRubyHashEqlWeakReference(Object key, int hashCode) {
            super(key, hashCode);
        }

        public RecordingCompareByRubyHashEqlWeakReference(
                Object key,
                ReferenceQueue<? super Object> queue,
                int hashCode) {
            super(key, queue, hashCode);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (super.equals(other)) {
                this.keyInMap = ((CompareByRubyHashEqlWeakReference) other).get();
                return true;
            } else {
                return false;
            }
        }
    }

}
