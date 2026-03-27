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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(value = { RubyGuards.class })
public abstract class SingleValueCastNode extends RubyBaseNode {

    public abstract Object execute(Node node, Object[] args);

    @Specialization(guards = "noArguments(args)")
    static Object castNil(Object[] args) {
        return nil;
    }

    @Specialization(guards = "singleArgument(args)")
    static Object castSingle(Object[] args) {
        return args[0];
    }

    @Specialization(guards = { "!noArguments(args)", "!singleArgument(args)" })
    static RubyArray castMany(Node node, Object[] args) {
        return createArray(node, args);
    }

}
