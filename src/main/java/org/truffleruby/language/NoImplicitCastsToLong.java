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
package org.truffleruby.language;

import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;

/** Same as {@link RubyTypes} but without implicit casts from * to long. */
@TypeSystem
public abstract class NoImplicitCastsToLong {

    // Check singletons by identity for performance

    @TypeCheck(Nil.class)
    public static boolean isNil(Object value) {
        return value == Nil.INSTANCE;
    }

    @TypeCast(Nil.class)
    public static Nil asNil(Object value) {
        return Nil.INSTANCE;
    }

    @TypeCheck(NotProvided.class)
    public static boolean isNotProvided(Object value) {
        return value == NotProvided.INSTANCE;
    }

    @TypeCast(NotProvided.class)
    public static NotProvided asNotProvided(Object value) {
        return NotProvided.INSTANCE;
    }

}
