/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.FrameOnStackMarker;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

@NodeChild(value = "block", type = RubyNode.class)
public abstract class InlinedLambdaNode extends UnaryInlinedOperationNode {

    protected static final String METHOD = "lambda";

    private final int frameOnStackMarkerSlot;

    public InlinedLambdaNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            int frameOnStackMarkerSlot) {
        super(language, callNodeParameters);
        this.frameOnStackMarkerSlot = frameOnStackMarkerSlot;
    }

    protected abstract RubyNode getBlock();

    @Specialization(
            guards = { "lookupNode.lookupIgnoringVisibility(frame, self, METHOD) == coreMethods().LAMBDA", },
            assumptions = "assumptions",
            limit = "1")
    RubyProc lambda(VirtualFrame frame, Object self, RubyProc block,
            @Cached LookupMethodOnSelfNode lookupNode) {
        // NOTE(norswap): A lambda call target was created by default in MethodTranslator.
        assert block.type == ProcType.LAMBDA;
        return block;
    }

    // The lambda method might have been overriden, undefined, redefined, ...
    @Specialization
    Object fallback(VirtualFrame frame, Object self, RubyProc block) {
        // NOTE(norswap): This can occur if the user overrides the lambda method and it does not resolve to
        //   Kernel#lambda anymore. A lambda call target for the block was created by default in MethodTranslator,
        //   and must be converted to a proc block here, as the user-defined method should receive a proc block.
        assert block.type == ProcType.LAMBDA;
        FrameOnStackMarker frameOnStackMarker = (FrameOnStackMarker) frame.getObject(frameOnStackMarkerSlot);
        block = ProcOperations.createProcFromBlock(getContext(), getLanguage(), block, frameOnStackMarker);
        return rewriteAndCallWithBlock(frame, self, block);
    }

    @Override
    protected RubyNode getBlockNode() {
        return getBlock();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedLambdaNodeGen.create(
                getLanguage(),
                this.parameters,
                this.frameOnStackMarkerSlot,
                getSelfNode().cloneUninitialized(),
                getBlock().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
