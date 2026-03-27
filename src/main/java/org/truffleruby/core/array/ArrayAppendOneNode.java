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

import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.profiles.CountingConditionProfile;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

@NodeChild(value = "arrayNode", type = RubyNode.class)
@NodeChild(value = "valueNode", type = RubyNode.class)
@ImportStatic(ArrayGuards.class)
@ReportPolymorphism // for ArrayStoreLibrary
public abstract class ArrayAppendOneNode extends RubyContextSourceNode {

    @NeverDefault
    public static ArrayAppendOneNode create() {
        return ArrayAppendOneNodeGen.create(null, null);
    }

    public abstract RubyArray executeAppendOne(RubyArray array, Object value);

    public abstract RubyNode getArrayNode();

    public abstract RubyNode getValueNode();

    // Append of the correct type

    @Specialization(
            guards = "stores.acceptsValue(store, value)",
            limit = "storageStrategyLimit()")
    RubyArray appendOneSameType(RubyArray array, Object value,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores,
            @Cached CountingConditionProfile extendProfile) {
        final int oldSize = array.size;
        final int newSize = oldSize + 1;
        final int length = stores.capacity(store);

        if (extendProfile.profile(newSize > length)) {
            final int capacity = ArrayUtils.capacityForOneMore(getLanguage(), length);
            final Object newStore = stores.expand(store, capacity);
            stores.write(newStore, oldSize, value);
            array.setStoreAndSize(newStore, newSize);
        } else {
            stores.write(store, oldSize, value);
            array.setSize(newSize);
        }
        return array;
    }

    // Append to an empty array

    @ReportPolymorphism.Exclude
    @Specialization(
            guards = "isZeroLengthArrayStore(array.getStore())",
            limit = "storageStrategyLimit()")
    RubyArray appendOneToEmptyArray(RubyArray array, Object value,
            @Bind("array.getStore()") Object currentStore,
            @CachedLibrary("currentStore") ArrayStoreLibrary currentStores,
            @CachedLibrary(limit = "storageStrategyLimit()") @Exclusive ArrayStoreLibrary newStores) {
        final int newCapacity = ArrayUtils.capacityForOneMore(getLanguage(), 0);
        final Object newStore = currentStores.allocateForNewValue(currentStore, value, newCapacity);
        newStores.write(newStore, 0, value);
        array.setStoreAndSize(newStore, 1);
        return array;
    }

    // Append forcing a generalization

    @Specialization(
            guards = {
                    "!isZeroLengthArrayStore(array.getStore())",
                    "!currentStores.acceptsValue(array.getStore(), value)" },
            limit = "storageStrategyLimit()")
    RubyArray appendOneGeneralizeNonMutable(RubyArray array, Object value,
            @Bind("array.getStore()") Object currentStore,
            @CachedLibrary("currentStore") ArrayStoreLibrary currentStores,
            @CachedLibrary(limit = "storageStrategyLimit()") @Exclusive ArrayStoreLibrary newStores) {
        final int oldSize = array.size;
        final int newSize = oldSize + 1;
        final int oldCapacity = currentStores.capacity(currentStore);
        final int newCapacity = newSize > oldCapacity
                ? ArrayUtils.capacityForOneMore(getLanguage(), oldCapacity)
                : oldCapacity;
        final Object newStore = currentStores.allocateForNewValue(currentStore, value, newCapacity);
        currentStores.copyContents(currentStore, 0, newStore, 0, oldSize);
        newStores.write(newStore, oldSize, value);
        array.setStoreAndSize(newStore, newSize);
        return array;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ArrayAppendOneNodeGen.create(
                getArrayNode().cloneUninitialized(),
                getValueNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
