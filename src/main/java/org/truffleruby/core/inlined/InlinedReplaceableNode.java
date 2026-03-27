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
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

public abstract class InlinedReplaceableNode extends RubyContextSourceNode {

    protected final RubyCallNodeParameters parameters;

    @CompilationFinal(dimensions = 1) protected final Assumption[] assumptions;

    private RubyCallNode replacedBy = null;

    protected InlinedReplaceableNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption... assumptions) {
        // nullify receiver, arguments and block for clarity
        // because they should be overridden in subclasses.
        this.parameters = callNodeParameters.withoutNodes();

        this.assumptions = new Assumption[1 + assumptions.length];
        this.assumptions[0] = language.traceFuncUnusedAssumption.getAssumption();
        ArrayUtils.arraycopy(assumptions, 0, this.assumptions, 1, assumptions.length);
    }

    protected RubyCallNode rewriteToCallNode() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return atomic(() -> {
            // Check if we are still in the AST
            boolean found = !NodeUtil.forEachChild(getParent(), node -> node != this);

            if (found) {
                // We need to pass the updated children of this node to the call node
                RubyCallNode callNode = new RubyCallNode(parameters.withReceiverAndArguments(
                        getReceiverNode(),
                        getArgumentNodes(),
                        getBlockNode()));
                callNode.copyFlags(this);
                replacedBy = callNode;
                return replace(callNode, this + " could not be executed inline");
            } else {
                return replacedBy;
            }
        });
    }

    protected abstract RubyNode getReceiverNode();

    protected abstract RubyNode[] getArgumentNodes();

    protected RubyNode getBlockNode() {
        assert parameters.getBlock() == null;
        return null;
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return rewriteToCallNode().isDefined(frame, language, context);
    }
}
