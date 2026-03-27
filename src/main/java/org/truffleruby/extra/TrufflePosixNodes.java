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
package org.truffleruby.extra;

import com.oracle.truffle.api.dsl.Cached;

import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.time.GetTimeZoneNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

@CoreModule(value = "Truffle::POSIX", isClass = true)
public abstract class TrufflePosixNodes {

    @TruffleBoundary
    private static void invalidateENV(String name) {
        if (name.equals("TZ")) {
            GetTimeZoneNode.invalidateTZ();
        }
    }

    @Primitive(name = "posix_invalidate_env")
    public abstract static class InvalidateEnvNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "libEnvVar.isRubyString(this, envVar)", limit = "1")
        Object invalidate(Object envVar,
                @Cached RubyStringLibrary libEnvVar) {
            invalidateENV(StringOperations.getJavaString(envVar));
            return envVar;
        }

    }

}
