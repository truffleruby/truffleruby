/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
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

@NodeChild(value = "receiver", type = RubyNode.class)
@NodeChild(value = "operand1Node", type = RubyNode.class)
@NodeChild(value = "operand2Node", type = RubyNode.class)
public abstract class TernaryInlinedOperationNode extends InlinedOperationNode {

    public TernaryInlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption... assumptions) {
        super(language, callNodeParameters, assumptions);
    }

    protected abstract RubyNode getReceiver();

    protected abstract RubyNode getOperand1Node();

    protected abstract RubyNode getOperand2Node();

    @Override
    protected RubyNode getReceiverNode() {
        return getReceiver();
    }

    @Override
    protected RubyNode[] getArgumentNodes() {
        return new RubyNode[]{ getOperand1Node(), getOperand2Node() };
    }

}
