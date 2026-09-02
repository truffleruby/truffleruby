/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.RubyBaseNode;

/** Unwraps a native VALUE[] (an address and a length) to the corresponding Ruby objects */
@GenerateUncached
public abstract class UnwrapCArrayNode extends RubyBaseNode {

    public abstract Object[] execute(long address, int size);

    @ExplodeLoop
    @Specialization(
            guards = { "size == cachedSize", "cachedSize <= MAX_EXPLODE_SIZE" },
            limit = "1")
    Object[] unwrapCArrayExplode(long address, int size,
            @Cached("size") int cachedSize,
            @Cached @Shared UnwrapNode unwrapNode) {
        final Object[] store = new Object[cachedSize];
        for (int i = 0; i < cachedSize; i++) {
            store[i] = unwrapNode.execute(this, Pointer.rawReadLong(address + i * 8L));
        }
        return store;
    }

    @Specialization(replaces = "unwrapCArrayExplode")
    Object[] unwrapCArray(long address, int size,
            @Cached @Shared UnwrapNode unwrapNode,
            @Cached LoopConditionProfile loopProfile) {
        final Object[] store = new Object[size];
        int i = 0;
        try {
            for (; loopProfile.inject(i < size); i++) {
                store[i] = unwrapNode.execute(this, Pointer.rawReadLong(address + i * 8L));
                TruffleSafepoint.poll(this);
            }
        } finally {
            profileAndReportLoopCount(loopProfile, i);
        }
        return store;
    }
}
