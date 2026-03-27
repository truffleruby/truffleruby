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

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class ReadLocalVariableNode extends ReadLocalNode {

    public ReadLocalVariableNode(LocalVariableType type, int frameSlot) {
        super(frameSlot, type);
    }

    @Override
    public int getFrameDepth() {
        return 0;
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

        return readFrameSlotNode.executeRead(frame);
    }

    @Override
    protected String getVariableName() {
        return getRootNode().getFrameDescriptor().getSlotName(frameSlot).toString();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadLocalVariableNode(type, frameSlot);
        return copy.copyFlags(this);
    }

}
