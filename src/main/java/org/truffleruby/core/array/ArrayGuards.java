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

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.ZeroLengthArrayStore;

public final class ArrayGuards {

    public static int storageStrategyLimit() {
        return RubyLanguage.getCurrentLanguage().options.ARRAY_STRATEGY_CACHE;
    }

    // Storage strategies

    public static boolean isObjectArray(RubyArray array) {
        final Object store = array.getStore();
        return store.getClass() == Object[].class;
    }

    // Higher level properties

    public static boolean isEmptyArray(RubyArray array) {
        return array.size == 0;
    }

    public static boolean basicStore(Object store) {
        assert !(store instanceof Object[]) ||
                store.getClass() == Object[].class : "Must be Object[], not a subclass: " + store;
        return store instanceof int[] || store instanceof long[] || store instanceof double[] ||
                store instanceof Object[];
    }

    public static boolean zeroLengthStore(Object store) {
        return store == ZeroLengthArrayStore.ZERO_LENGTH_STORE;
    }

    public static boolean isZeroLengthArrayStore(Object store) {
        return store == ZeroLengthArrayStore.ZERO_LENGTH_STORE || store == ArrayStoreLibrary.SHARED_INITIAL_STORE;
    }
}
