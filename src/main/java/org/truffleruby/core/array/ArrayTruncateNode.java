/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;


/** Truncates an array by setting its size and clearing the remainder of the store for non-primitive store, in order to
 * avoid memory leaks. */
@ImportStatic(ArrayGuards.class)
public abstract class ArrayTruncateNode extends RubyBaseNode {

    public static ArrayTruncateNode create() {
        return ArrayTruncateNodeGen.create();
    }

    public abstract void execute(RubyArray array, int size);

    @Specialization(
            guards = { "array.size > size", "stores.isMutable(store)" },
            limit = "storageStrategyLimit()")
    void truncate(RubyArray array, int size,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {

        final int oldSize = array.size;
        array.size = size;
        stores.clear(store, size, oldSize - size);
    }

    @Specialization(
            guards = { "array.size > size", "!stores.isMutable(store)" },
            limit = "storageStrategyLimit()")
    void truncateCopy(RubyArray array, int size,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {

        final Object newStore = stores.allocateForNewStore(store, store, size);
        stores.copyContents(store, 0, newStore, 0, size);
        array.setStoreAndSize(newStore, size);
    }

    @ReportPolymorphism.Exclude
    @Specialization(guards = "array.size <= size")
    void doNothing(RubyArray array, int size) {
    }
}
