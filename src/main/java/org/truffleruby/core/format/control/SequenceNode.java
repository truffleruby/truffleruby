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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class SequenceNode extends FormatNode {

    @Children private final FormatNode[] children;

    public SequenceNode(FormatNode[] children) {
        this.children = children;
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        for (FormatNode child : children) {
            child.execute(frame);
        }

        return null;
    }

}
