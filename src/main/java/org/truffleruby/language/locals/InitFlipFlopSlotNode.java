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

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyNode;

public final class InitFlipFlopSlotNode extends RubyContextSourceNodeCustomExecuteVoid {

    private final int frameSlot;

    public InitFlipFlopSlotNode(int frameSlot) {
        this.frameSlot = frameSlot;
    }

    @Override
    public Nil executeVoid(VirtualFrame frame) {
        frame.setBoolean(frameSlot, false);
        return nil;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        executeVoid(frame);
        return null;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new InitFlipFlopSlotNode(frameSlot);
        return copy.copyFlags(this);
    }

}
