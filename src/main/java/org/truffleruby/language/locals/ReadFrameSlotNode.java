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

import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;

public abstract class ReadFrameSlotNode extends RubyBaseNode {

    protected final int frameSlot;

    public ReadFrameSlotNode(int slot) {
        this.frameSlot = slot;
    }

    public int getFrameSlot() {
        return frameSlot;
    }

    public abstract Object executeRead(Frame frame);

    @Specialization(guards = "frame.isBoolean(frameSlot)")
    boolean readBoolean(Frame frame) {
        return frame.getBoolean(frameSlot);
    }

    @Specialization(guards = "frame.isInt(frameSlot)")
    int readInt(Frame frame) {
        return frame.getInt(frameSlot);
    }

    @Specialization(guards = "frame.isLong(frameSlot)")
    long readLong(Frame frame) {
        return frame.getLong(frameSlot);
    }

    @Specialization(guards = "frame.isDouble(frameSlot)")
    double readDouble(Frame frame) {
        return frame.getDouble(frameSlot);
    }

    @Specialization(guards = "frame.isObject(frameSlot)")
    Object readObject(Frame frame) {
        return frame.getObject(frameSlot);
    }

}
