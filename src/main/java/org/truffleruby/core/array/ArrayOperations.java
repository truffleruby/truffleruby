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

import org.truffleruby.core.array.library.ArrayStoreLibrary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class ArrayOperations {

    @TruffleBoundary
    public static Iterable<Object> toIterable(RubyArray array) {
        return ArrayStoreLibrary.getUncached().getIterable(array.getStore(), 0, array.size);
    }

    @TruffleBoundary
    public static int getStoreCapacity(RubyArray array) {
        return ArrayStoreLibrary.getUncached().capacity(array.getStore());
    }

}
