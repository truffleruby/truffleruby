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
package org.truffleruby.core.bool;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.cast.BooleanCastNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "TrueClass", isClass = true)
public abstract class TrueClassNodes {

    @CoreMethod(names = "&", needsSelf = false, required = 1)
    public abstract static class AndNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean and(Object other,
                @Cached BooleanCastNode cast) {
            return cast.execute(this, other);
        }
    }

    @CoreMethod(names = "|", needsSelf = false, required = 1)
    public abstract static class OrNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean or(Object other) {
            return true;
        }
    }

    @CoreMethod(names = "^", needsSelf = false, required = 1)
    public abstract static class XorNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean xor(Object other,
                @Cached BooleanCastNode cast) {
            return !cast.execute(this, other);
        }
    }

}
