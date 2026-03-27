/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

/** This node will convert the array to a copy on write version and return a second view representing the requested
 * portion. If you are going to immediately mutate the resulting stores then this node is probably not an appropriate
 * way to it. */
@ImportStatic(ArrayGuards.class)
public abstract class ArrayCopyOnWriteNode extends RubyBaseNode {

    public abstract Object execute(RubyArray array, int start, int length);

    @Specialization(guards = "stores.isMutable(store)", limit = "storageStrategyLimit()")
    Object extractFromMutableArray(RubyArray array, int start, int length,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        int size = array.size;
        Object cowStore = stores.extractRange(store, 0, size);
        /* This new store should not be shared because the RubyArray object which will own it is not yet shared. */
        Object range = stores.extractRangeAndUnshare(store, start, start + length);
        array.setStore(cowStore);
        return range;
    }

    @Specialization(guards = "!stores.isMutable(store)", limit = "storageStrategyLimit()")
    Object extractFromNonMutableArray(RubyArray array, int start, int length,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        Object range = stores.extractRangeAndUnshare(store, start, start + length);
        return range;
    }
}
