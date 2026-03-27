/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.language.objects.AllocationTracing;

/** Dup an array, without using any method lookup. This isn't a call - it's an operation on a core class. */
@ImportStatic(ArrayGuards.class)
public abstract class ArrayDupNode extends RubyBaseNode {

    public abstract RubyArray executeDup(RubyArray array);

    @Specialization(
            guards = {
                    "from.size == cachedSize",
                    "cachedSize <= MAX_EXPLODE_SIZE" },
            limit = "getCacheLimit()")
    RubyArray dupProfiledSize(RubyArray from,
            @Bind("from.getStore()") Object fromStore,
            @CachedLibrary("fromStore") ArrayStoreLibrary fromStores,
            @CachedLibrary(limit = "1") ArrayStoreLibrary toStores,
            @Cached("from.size") int cachedSize) {
        return copyArraySmall(getLanguage(), fromStores, toStores, fromStore, cachedSize);
    }

    @ExplodeLoop
    private RubyArray copyArraySmall(RubyLanguage language,
            ArrayStoreLibrary fromStores,
            ArrayStoreLibrary toStores,
            Object fromStore,
            int cachedSize) {
        final Object copy = fromStores.unsharedAllocator(fromStore).allocate(cachedSize);
        for (int i = 0; i < cachedSize; i++) {
            toStores.write(copy, i, fromStores.read(fromStore, i));
        }
        return allocateArray(coreLibrary().arrayClass, copy, cachedSize);
    }

    @Specialization(replaces = "dupProfiledSize")
    RubyArray dup(RubyArray from,
            @Bind("from.getStore()") Object fromStore,
            @Cached ArrayCopyOnWriteNode cowNode) {
        final int size = from.size;
        final Object copy = cowNode.execute(from, 0, from.size);
        return allocateArray(coreLibrary().arrayClass, copy, size);
    }

    private RubyArray allocateArray(RubyClass rubyClass, Object store, int size) {
        RubyArray array = new RubyArray(rubyClass, getLanguage().arrayShape, store, size);
        AllocationTracing.trace(array, this);
        return array;
    }

    protected int getCacheLimit() {
        return getLanguage().options.ARRAY_DUP_CACHE;
    }

}
