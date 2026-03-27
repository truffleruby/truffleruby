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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import java.util.Arrays;

/** This node prepares an array to receive content copied from another array. In particular:
 * <ul>
 * <li>It makes sure that the array's store's capacity is sufficient to handle the copy, and update its size.
 * <li>It makes sure the array's store is mutable, and compatible with the source array, generalizing it if needed.
 * <li>If the copy is done entirely beyond the current size of the array, it makes sure intervening slots are filled
 * with {@code nil}.
 * </ul>
 * <p>
 * The copy itself can then be performed with {@link ArrayCopyCompatibleRangeNode}. In fact it MUST be performed, as the
 * array may otherwise contain uninitialized elements (in particular, {@code null} values in {@code Object} arrays). */
@ReportPolymorphism // for ArrayStoreLibrary
@ImportStatic(ArrayGuards.class)
public abstract class ArrayPrepareForCopyNode extends RubyBaseNode {

    public static ArrayPrepareForCopyNode create() {
        return ArrayPrepareForCopyNodeGen.create();
    }

    public abstract Object execute(RubyArray dst, RubyArray src, int dstStart, int length);

    @ReportPolymorphism.Exclude
    @Specialization(guards = { "length == 0", "start <= dst.size" })
    Object noChange(RubyArray dst, RubyArray src, int start, int length) {
        return dst.getStore();
    }

    @Specialization(
            guards = "start > dst.size",
            limit = "storageStrategyLimit()")
    Object nilPad(RubyArray dst, RubyArray src, int start, int length,
            @Bind("dst.getStore()") Object dstStore,
            @CachedLibrary("dstStore") ArrayStoreLibrary dstStores) {

        final int oldSize = dst.size;
        final Object[] newStore = new Object[ArrayUtils.capacity(getLanguage(), oldSize, start + length)];
        dstStores.copyContents(dstStore, 0, newStore, 0, oldSize); // copy the original store
        Arrays.fill(newStore, oldSize, start, nil); // nil-pad the new empty part
        dst.setStore(newStore);
        dst.size = start + length;
        return newStore;
    }

    @Specialization(
            guards = { "length > 0", "start <= dst.size", "compatible(dstStores, dstStore, srcStore)" },
            limit = "storageStrategyLimit()")
    Object resizeCompatible(RubyArray dst, RubyArray src, int start, int length,
            @Bind("dst.getStore()") Object dstStore,
            @Bind("src.getStore()") Object srcStore,
            @Cached ArrayEnsureCapacityNode ensureCapacityNode,
            @CachedLibrary("dstStore") ArrayStoreLibrary dstStores) {

        // Necessary even if under capacity to ensure that the destination gets a mutable store.
        Object newStore = ensureCapacityNode.executeEnsureCapacity(dst, start + length);
        if (start + length > dst.size) {
            dst.size = start + length;
        }
        return newStore;
    }

    @Specialization(
            guards = { "length > 0", "start <= dst.size", "!compatible(dstStores, dstStore, srcStore)" },
            limit = "storageStrategyLimit()")
    Object resizeGeneralize(RubyArray dst, RubyArray src, int start, int length,
            @Bind("dst.getStore()") Object dstStore,
            @Bind("src.getStore()") Object srcStore,
            @CachedLibrary("dstStore") ArrayStoreLibrary dstStores) {

        final int oldDstSize = dst.size;
        final int newDstSize = Math.max(oldDstSize, start + length);
        final Object newDstStore = dstStores.allocateForNewStore(dstStore, srcStore, newDstSize);

        // NOTE(norswap, 10 Jun 2020)
        //  The semantics of this node guarantee that the whole array will be copied (some code relies on it).
        //  Otherwise, we could copy only up to `start` if we could assume the rest of the array will be filled, and copy
        //  the rest ([start + length, oldDstSize[) only if actually required.
        //  It's not clear that even in that case much would be gained by splitting the copy in two parts, unless
        //  `length` is truly big.
        dstStores.copyContents(dstStore, 0, newDstStore, 0, oldDstSize);
        dst.setStore(newDstStore);
        if (newDstSize > oldDstSize) {
            dst.size = newDstSize;
        }
        return newDstStore;
    }

    protected static boolean compatible(ArrayStoreLibrary stores, Object dstStore, Object srcStore) {
        return stores.acceptsAllValues(dstStore, srcStore);
    }
}
