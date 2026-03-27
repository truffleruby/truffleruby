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
package org.truffleruby.language.methods;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcCallTargets;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.FrameOnStackMarker;

import com.oracle.truffle.api.frame.VirtualFrame;

import static org.truffleruby.parser.YARPBaseTranslator.NO_FRAME_ON_STACK_MARKER;

/** Create a Ruby Proc to pass as a block to the called method. The literal block is represented as call targets and a
 * SharedMethodInfo. This is executed at the call site just before dispatch. */
public abstract class BlockDefinitionNode extends RubyContextSourceNode {

    private final ProcType type;
    private final SharedMethodInfo sharedMethodInfo;
    private final ProcCallTargets callTargets;
    private final BreakID breakID;
    private final int frameOnStackMarkerSlot;

    public BlockDefinitionNode(
            ProcType type,
            SharedMethodInfo sharedMethodInfo,
            ProcCallTargets callTargets,
            BreakID breakID,
            int frameOnStackMarkerSlot) {
        assert !(type == ProcType.PROC && frameOnStackMarkerSlot == NO_FRAME_ON_STACK_MARKER);
        this.type = type;
        this.sharedMethodInfo = sharedMethodInfo;
        this.callTargets = callTargets;
        this.breakID = breakID;
        this.frameOnStackMarkerSlot = frameOnStackMarkerSlot;
    }

    public BreakID getBreakID() {
        return breakID;
    }

    public int getFrameOnStackMarkerSlot() {
        return frameOnStackMarkerSlot;
    }

    @Override
    public abstract RubyProc execute(VirtualFrame virtualFrame);

    @Specialization
    RubyProc doBlockDefinition(VirtualFrame frame,
            @Cached GetSpecialVariableStorage readSpecialVariableStorageNode,
            @Cached WithoutVisibilityNode withoutVisibilityNode) {
        final FrameOnStackMarker frameOnStackMarker;
        if (type == ProcType.PROC) {
            frameOnStackMarker = (FrameOnStackMarker) frame.getObject(frameOnStackMarkerSlot);
            assert frameOnStackMarker != null;
        } else {
            frameOnStackMarker = null;
        }

        return ProcOperations.createRubyProc(
                coreLibrary().procClass,
                getLanguage().procShape,
                type,
                sharedMethodInfo,
                callTargets,
                frame.materialize(),
                readSpecialVariableStorageNode.execute(frame, this),
                RubyArguments.getMethod(frame),
                frameOnStackMarker,
                withoutVisibilityNode.executeWithoutVisibility(this, RubyArguments.getDeclarationContext(frame)));
    }


    @Override
    public RubyNode cloneUninitialized() {
        var copy = BlockDefinitionNodeGen.create(type, sharedMethodInfo, callTargets, breakID, frameOnStackMarkerSlot);
        return copy.copyFlags(this);
    }

}
