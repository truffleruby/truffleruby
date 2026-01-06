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
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.parser.FrameDescriptorInfo;

public final class WriteDeclarationVariableNode extends WriteLocalNode {

    private final int frameDepth;

    @Child private WriteFrameSlotNode writeFrameSlotNode;

    public WriteDeclarationVariableNode(int frameSlot, int frameDepth, RubyNode valueNode) {
        super(frameSlot, valueNode);
        this.frameDepth = frameDepth;
    }

    public int getFrameDepth() {
        return frameDepth;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object value = valueNode.execute(frame);
        assign(frame, value);
        return value;
    }

    @Override
    public void assign(VirtualFrame frame, Object value) {
        if (writeFrameSlotNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeFrameSlotNode = insert(WriteFrameSlotNodeGen.create(frameSlot));
        }

        final Frame declarationFrame = RubyArguments.getDeclarationFrame(frame, frameDepth);
        writeFrameSlotNode.executeWrite(declarationFrame, value);
    }

    @Override
    public AssignableNode toAssignableNode() {
        this.valueNode = null;
        return this;
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    @Override
    protected String getVariableName() {
        var descriptor = FrameDescriptorInfo.getDeclarationFrameDescriptor(
                getRootNode().getFrameDescriptor(), frameDepth);
        return descriptor.getSlotName(frameSlot).toString();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new WriteDeclarationVariableNode(
                frameSlot,
                frameDepth,
                cloneUninitialized(valueNode));
        return copy.copyFlags(this);
    }

}
