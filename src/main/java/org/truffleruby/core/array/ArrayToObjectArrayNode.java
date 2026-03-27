/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.IntValueProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayToObjectArrayNode extends RubyBaseNode {

    @NeverDefault
    public static ArrayToObjectArrayNode create() {
        return ArrayToObjectArrayNodeGen.create();
    }

    public Object[] unsplat(Object[] arguments) {
        assert arguments.length == 1;
        return executeToObjectArray((RubyArray) arguments[0]);
    }

    public abstract Object[] executeToObjectArray(RubyArray array);

    @Specialization(limit = "storageStrategyLimit()")
    Object[] toObjectArrayOther(RubyArray array,
            @Bind("array.getStore()") Object store,
            @Cached IntValueProfile sizeProfile,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        final int size = sizeProfile.profile(array.size);
        return stores.boxedCopyOfRange(store, 0, size);
    }

}
