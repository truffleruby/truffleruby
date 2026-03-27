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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.dispatch.DispatchNode;

@GenerateCached(false)
@GenerateInline
public abstract class ToAryNode extends RubyBaseNode {

    public abstract RubyArray execute(Node node, Object object);

    @Specialization
    static RubyArray coerceRubyArray(RubyArray array) {
        return array;
    }

    @Specialization(guards = "!isRubyArray(object)")
    static RubyArray coerceObject(Node node, Object object,
            @Cached(inline = false) DispatchNode toAryNode) {
        return (RubyArray) toAryNode.call(
                coreLibrary(node).truffleTypeModule,
                "rb_convert_type_fallback",
                object,
                coreLibrary(node).arrayClass,
                coreSymbols(node).TO_ARY);
    }
}
