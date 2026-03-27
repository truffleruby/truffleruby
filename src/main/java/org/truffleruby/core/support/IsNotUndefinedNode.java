/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.support;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;

@NodeChild(value = "valueNode", type = RubyNode.class)
public final class IsNotUndefinedNode extends RubyContextSourceNode {

    @Child RubyNode valueNode;

    public IsNotUndefinedNode(RubyNode valueNode) {
        this.valueNode = valueNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = valueNode.execute(frame);
        return value != NotProvided.INSTANCE;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new IsNotUndefinedNode(valueNode.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
