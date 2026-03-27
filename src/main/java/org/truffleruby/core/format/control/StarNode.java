/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.control;

import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;

public final class StarNode extends FormatNode {

    @Child private LoopNode loopNode;

    public StarNode(FormatNode child) {
        loopNode = Truffle.getRuntime().createLoopNode(new StarRepeatingNode(child));
    }

    @Override
    public Object execute(VirtualFrame frame) {
        loopNode.execute(frame);
        return null;
    }

    private final class StarRepeatingNode extends Node implements RepeatingNode {

        @Child private FormatNode child;

        public StarRepeatingNode(FormatNode child) {
            this.child = child;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            if (getSourcePosition(frame) >= getSourceEnd(frame)) {
                return false;
            }

            child.execute(frame);
            return true;
        }
    }

}
