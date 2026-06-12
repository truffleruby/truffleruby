/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PropagateSharingNode extends RubyBaseNode {

    public final Object propagate(Node node, RubyDynamicObject source, Object value) {
        execute(node, source, value);
        return value;
    }

    public abstract void execute(Node node, RubyDynamicObject source, Object value);

    @Specialization
    static void propagate(Node node, RubyDynamicObject source, Object value,
            @Cached IsSharedNode isSharedNode,
            @Cached WriteBarrierNode writeBarrierNode) {
        if (isSharedNode.execute(node, source)) {
            writeBarrierNode.execute(node, value);
        }
    }
}
