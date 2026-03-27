/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
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

@ImportStatic(ArrayGuards.class)
public abstract class ArrayPopOneNode extends RubyBaseNode {

    public abstract Object executePopOne(RubyArray array);

    // Pop from an empty array

    @Specialization(guards = "isEmptyArray(array)")
    Object popOneEmpty(RubyArray array) {
        return nil;
    }

    // Pop from a non-empty array

    @Specialization(guards = "!isEmptyArray(array)", limit = "storageStrategyLimit()")
    Object popOne(RubyArray array,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        final int size = array.size;
        final Object value = stores.read(store, size - 1);
        stores.clear(store, size - 1, 1);
        array.setSize(size - 1);
        return value;
    }

}
