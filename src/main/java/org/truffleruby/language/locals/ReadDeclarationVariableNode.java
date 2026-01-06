/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.parser.FrameDescriptorInfo;

public final class ReadDeclarationVariableNode extends ReadLocalNode {

    private final int frameDepth;

    public ReadDeclarationVariableNode(LocalVariableType type, int frameDepth, int frameSlot) {
        super(frameSlot, type);
        this.frameDepth = frameDepth;
    }

    public int getFrameDepth() {
        return frameDepth;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return readFrameSlot(frame);
    }

    @Override
    protected Object readFrameSlot(VirtualFrame frame) {
        if (readFrameSlotNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readFrameSlotNode = insert(ReadFrameSlotNodeGen.create(frameSlot));
        }

        final Frame declarationFrame = RubyArguments.getDeclarationFrame(frame, frameDepth);
        return readFrameSlotNode.executeRead(declarationFrame);
    }

    @Override
    public WriteLocalNode makeWriteNode(RubyNode rhs) {
        return new WriteDeclarationVariableNode(frameSlot, frameDepth, rhs);
    }

    @Override
    protected String getVariableName() {
        var descriptor = FrameDescriptorInfo.getDeclarationFrameDescriptor(
                getRootNode().getFrameDescriptor(), frameDepth);
        return descriptor.getSlotName(frameSlot).toString();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadDeclarationVariableNode(type, frameDepth, frameSlot);
        return copy.copyFlags(this);
    }

}
