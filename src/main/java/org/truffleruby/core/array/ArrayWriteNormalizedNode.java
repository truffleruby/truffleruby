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

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

@ImportStatic(ArrayGuards.class)
@ReportPolymorphism // for ArrayStoreLibrary
public abstract class ArrayWriteNormalizedNode extends RubyBaseNode {

    public abstract Object executeWrite(RubyArray array, int index, Object value);

    // Writing within an existing array with a compatible type

    @Specialization(
            guards = { "isInBounds(array, index)", "stores.acceptsValue(store, value)" },
            limit = "storageStrategyLimit()")
    Object writeWithin(RubyArray array, int index, Object value,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        stores.write(store, index, value);
        return value;
    }

    // Writing within an existing array with an incompatible type - need to generalise

    @Specialization(
            guards = {
                    "isInBounds(array, index)",
                    "!stores.acceptsValue(store, value)"
            },
            limit = "storageStrategyLimit()")
    Object writeWithinGeneralizeNonMutable(RubyArray array, int index, Object value,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores,
            @CachedLibrary(limit = "1") @Exclusive ArrayStoreLibrary newStores) {
        final int size = array.size;
        final Object newStore = stores.allocateForNewValue(store, value, size);
        stores.copyContents(store, 0, newStore, 0, size);
        newStores.write(newStore, index, value);
        array.setStore(newStore);
        return value;
    }

    // Extending an array of compatible type by just one

    @Specialization(guards = "isExtendingByOne(array, index)")
    Object writeExtendByOne(RubyArray array, int index, Object value,
            @Cached ArrayAppendOneNode appendNode) {
        appendNode.executeAppendOne(array, value);
        return value;
    }

    // Writing beyond the end of an array - may need to generalize to Object[] or otherwise extend
    @Specialization(
            guards = {
                    "!isInBounds(array, index)",
                    "!isExtendingByOne(array, index)",
                    "stores.isPrimitive(store)" },
            limit = "storageStrategyLimit()")
    Object writeBeyondPrimitive(RubyArray array, int index, Object value,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores,
            @CachedLibrary(limit = "1") @Exclusive ArrayStoreLibrary newStores,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        final int newSize = index + 1;
        final Object objectStore = stores.allocateForNewValue(store, nil, newSize);
        int oldSize = array.size;
        stores.copyContents(store, 0, objectStore, 0, oldSize);
        int n = oldSize;
        try {
            for (; loopProfile.inject(n < index); n++) {
                newStores.write(objectStore, n, nil);
                TruffleSafepoint.poll(this);
            }
        } finally {
            profileAndReportLoopCount(loopProfile, n - oldSize);
        }
        newStores.write(objectStore, index, value);
        array.setStoreAndSize(objectStore, newSize);
        return value;
    }

    @Specialization(
            guards = {
                    "!isInBounds(array, index)",
                    "!isExtendingByOne(array, index)",
                    "!stores.isPrimitive(store)" },
            limit = "storageStrategyLimit()")
    Object writeBeyondObject(RubyArray array, int index, Object value,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores,
            @CachedLibrary(limit = "1") @Exclusive ArrayStoreLibrary newStores,
            @Cached ArrayEnsureCapacityNode ensureCapacityNode,
            @Cached @Exclusive LoopConditionProfile loopProfile) {
        Object newStore = ensureCapacityNode.executeEnsureCapacity(array, index + 1);
        int n = array.size;
        try {
            for (; loopProfile.inject(n < index); n++) {
                newStores.write(newStore, n, nil);
                TruffleSafepoint.poll(this);
            }
        } finally {
            profileAndReportLoopCount(loopProfile, n - array.size);
        }
        newStores.write(newStore, index, value);
        array.setSize(index + 1);
        return value;
    }

    // Guards

    protected static boolean isInBounds(RubyArray array, int index) {
        return index >= 0 && index < array.size;
    }

    protected static boolean isExtendingByOne(RubyArray array, int index) {
        return index == array.size;
    }

}
