/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class FrameOnStackNode extends RubyContextSourceNode {

    @Child private RubyNode child;
    private final int frameOnStackMarkerSlot;

    public FrameOnStackNode(RubyNode child, int frameOnStackMarkerSlot) {
        assert frameOnStackMarkerSlot >= 0;
        this.child = child;
        this.frameOnStackMarkerSlot = frameOnStackMarkerSlot;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final FrameOnStackMarker marker = new FrameOnStackMarker();

        frame.setObject(frameOnStackMarkerSlot, marker);

        try {
            return child.execute(frame);
        } finally {
            marker.setNoLongerOnStack();
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new FrameOnStackNode(
                child.cloneUninitialized(),
                frameOnStackMarkerSlot);
        return copy.copyFlags(this);
    }

}
