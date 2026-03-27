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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.arguments.RubyArguments;

public abstract class FlipFlopNode extends RubyContextSourceNode {

    @Child private RubyNode begin;
    @Child private RubyNode end;
    private final boolean exclusive;
    private final int frameLevel;
    private final int frameSlot;

    public FlipFlopNode(
            RubyNode begin,
            RubyNode end,
            boolean exclusive,
            int frameLevel,
            int frameSlot) {
        this.begin = begin;
        this.end = end;
        this.exclusive = exclusive;
        this.frameLevel = frameLevel;
        this.frameSlot = frameSlot;
    }

    @Specialization
    Object doFlipFlop(VirtualFrame frame,
            @Cached BooleanCastNode beginCast,
            @Cached BooleanCastNode endCast) {

        if (exclusive) {
            if (getState(frame)) {
                if (endCast.execute(this, end.execute(frame))) {
                    setState(frame, false);
                }

                return true;
            } else {
                final boolean newState = beginCast.execute(this, begin.execute(frame));
                setState(frame, newState);
                return newState;
            }
        } else {
            if (getState(frame)) {
                if (endCast.execute(this, end.execute(frame))) {
                    setState(frame, false);
                }

                return true;
            } else {
                if (beginCast.execute(this, begin.execute(frame))) {
                    setState(frame, !endCast.execute(this, end.execute(frame)));
                    return true;
                }

                return false;
            }
        }
    }

    private boolean getState(VirtualFrame frame) {
        final Frame declarationFrame = RubyArguments.getDeclarationFrame(frame, frameLevel);
        return declarationFrame.getBoolean(frameSlot);
    }

    private void setState(VirtualFrame frame, boolean state) {
        final Frame declarationFrame = RubyArguments.getDeclarationFrame(frame, frameLevel);
        declarationFrame.setBoolean(frameSlot, state);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = FlipFlopNodeGen.create(
                begin.cloneUninitialized(),
                end.cloneUninitialized(),
                exclusive,
                frameLevel,
                frameSlot);
        return copy.copyFlags(this);
    }

}
