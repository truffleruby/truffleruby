/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyContextSourceNode;

import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;

@NodeChild(value = "expressionNode", type = RubyNode.class)
public abstract class NoMatchingPatternNode extends RubyContextSourceNode {

    protected abstract RubyNode getExpressionNode();

    @Specialization
    Object noMatchingPattern(Object expression,
            @Cached DispatchNode inspectNode) {
        Object inspected = inspectNode.call(coreLibrary().truffleTypeModule, "rb_inspect", expression);
        throw new RaiseException(getContext(), coreExceptions().noMatchingPatternError(inspected, this));
    }

    @Override
    public RubyNode cloneUninitialized() {
        return NoMatchingPatternNodeGen.create(getExpressionNode().cloneUninitialized()).copyFlags(this);
    }
}
