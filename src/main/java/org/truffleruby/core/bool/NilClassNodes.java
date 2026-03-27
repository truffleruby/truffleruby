/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.bool;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.inlined.InlinedIsNilNode;

import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "NilClass", isClass = true)
public abstract class NilClassNodes {

    /** Needs to be in Java for {@link InlinedIsNilNode} */
    @CoreMethod(names = "nil?", needsSelf = false)
    public abstract static class IsNilNode extends CoreMethodNode {

        @Specialization
        boolean isNil() {
            return true;
        }
    }

}
