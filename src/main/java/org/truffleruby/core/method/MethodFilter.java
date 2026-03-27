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
package org.truffleruby.core.method;

import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.methods.InternalMethod;

public abstract class MethodFilter {

    private MethodFilter() {
    }

    public static final MethodFilter PUBLIC = new MethodFilter() {
        @Override
        public boolean filter(InternalMethod method) {
            return method.getVisibility() == Visibility.PUBLIC;
        }
    };

    public static final MethodFilter PUBLIC_PROTECTED = new MethodFilter() {
        @Override
        public boolean filter(InternalMethod method) {
            return (method.getVisibility() == Visibility.PUBLIC ||
                    method.getVisibility() == Visibility.PROTECTED);
        }
    };

    public static final MethodFilter PROTECTED = new MethodFilter() {
        @Override
        public boolean filter(InternalMethod method) {
            return method.getVisibility() == Visibility.PROTECTED;
        }
    };

    public static final MethodFilter PRIVATE = new MethodFilter() {
        @Override
        public boolean filter(InternalMethod method) {
            return method.getVisibility() == Visibility.PRIVATE;
        }
    };

    public abstract boolean filter(InternalMethod method);

    public static MethodFilter by(Visibility visibility) {
        switch (visibility) {
            case PUBLIC:
                return PUBLIC;
            case PROTECTED:
                return PROTECTED;
            case PRIVATE:
                return PRIVATE;
            default:
                throw CompilerDirectives.shouldNotReachHere("unsupported visibility: " + visibility);
        }
    }
}
