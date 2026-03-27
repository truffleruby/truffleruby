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
package org.truffleruby.core.inlined;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.NodeChild;

@NodeChild(value = "leftNode", type = RubyNode.class)
@NodeChild(value = "rightNode", type = RubyNode.class)
public abstract class BinaryInlinedOperationNode extends InlinedOperationNode {

    public BinaryInlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption... assumptions) {
        super(language, callNodeParameters, assumptions);
    }

    protected abstract RubyNode getLeftNode();

    protected abstract RubyNode getRightNode();

    @Override
    protected RubyNode getReceiverNode() {
        return getLeftNode();
    }

    @Override
    protected RubyNode[] getArgumentNodes() {
        return new RubyNode[]{ getRightNode() };
    }

}
