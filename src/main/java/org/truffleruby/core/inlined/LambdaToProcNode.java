/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.FrameOnStackMarker;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.parser.YARPBlockNodeTranslator;

/** Wraps a {@link BlockDefinitionNode} to convert the returned block (which should be a {@link ProcType#LAMBDA}) to be
 * a proc.
 *
 * <p>
 * When we encounter a function named {@code lambda} which is called with a block, we speculatively create a lambda call
 * target for that block in {@link YARPBlockNodeTranslator}. But if that method does not refer to {@code Kernel#lambda},
 * then a proc call target is needed instead. This node is thus needed to "deoptimize" such cases. */
public final class LambdaToProcNode extends RubyContextSourceNode {

    /** A {@link BlockDefinitionNode}, possibly wrapped in a RubyNodeWrapper by instrumentation */
    @Child private RubyNode blockNode;

    private final int frameOnStackMarkerSlot;

    public LambdaToProcNode(BlockDefinitionNode blockNode) {
        this.blockNode = blockNode;
        this.frameOnStackMarkerSlot = blockNode.getFrameOnStackMarkerSlot();
    }

    @Override
    public RubyProc execute(VirtualFrame frame) {
        final RubyProc block = (RubyProc) blockNode.execute(frame);
        assert block.type == ProcType.LAMBDA;
        FrameOnStackMarker frameOnStackMarker = (FrameOnStackMarker) frame.getObject(frameOnStackMarkerSlot);
        return ProcOperations.createProcFromBlock(getContext(), getLanguage(), block, frameOnStackMarker);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new LambdaToProcNode((BlockDefinitionNode) blockNode.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
