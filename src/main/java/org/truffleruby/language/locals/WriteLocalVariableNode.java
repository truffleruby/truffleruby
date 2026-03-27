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
package org.truffleruby.language.locals;

import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class WriteLocalVariableNode extends WriteLocalNode {

    @Child private WriteFrameSlotNode writeFrameSlotNode;

    public WriteLocalVariableNode(int frameSlot, RubyNode valueNode) {
        super(frameSlot, valueNode);
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

        writeFrameSlotNode.executeWrite(frame, value);
    }

    @Override
    public AssignableNode toAssignableNode() {
        this.valueNode = null;
        return WriteFrameSlotNodeGen.create(frameSlot);
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    @Override
    protected String getVariableName() {
        return getRootNode().getFrameDescriptor().getSlotName(frameSlot).toString();
    }

    @Override
    public String toString() {
        return super.toString() + " " + getVariableName() + " = " + valueNode;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new WriteLocalVariableNode(
                frameSlot,
                cloneUninitialized(valueNode));
        return copy.copyFlags(this);
    }

}
