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
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateCached(false)
@GenerateInline
public abstract class ToPathNode extends RubyBaseNode {

    public abstract Object execute(Node node, Object value);

    @Specialization
    static RubyString coerceRubyString(RubyString path) {
        return path;
    }

    @Specialization
    static ImmutableRubyString coerceImmutableRubyString(ImmutableRubyString path) {
        return path;
    }

    @Specialization(guards = "isNotRubyString(object)")
    static RubyString coerceObject(Node node, Object object,
            @Cached(inline = false) DispatchNode toPathNode) {
        return (RubyString) toPathNode.call(coreLibrary(node).truffleTypeModule, "coerce_to_path", object);
    }
}
