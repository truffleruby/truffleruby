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
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/** See {@link ToIntNode} for a comparison of different integer conversion nodes. */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class ToRubyIntegerNode extends RubyBaseNode {

    public abstract Object execute(Node node, Object object);

    @Specialization
    static int coerceInt(int value) {
        return value;
    }

    @Specialization
    static long coerceLong(long value) {
        return value;
    }

    @Specialization
    static RubyBignum coerceRubyBignum(RubyBignum value) {
        return value;
    }

    @Specialization(guards = "!isRubyInteger(object)")
    static Object coerceObject(Node node, Object object,
            @Cached(inline = false) DispatchNode toIntNode) {
        return toIntNode.call(coreLibrary(node).truffleTypeModule, "rb_to_int_fallback", object);
    }
}
