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

public final class SetOutputPositionNode extends FormatNode {

    private final int position;

    public SetOutputPositionNode(int position) {
        this.position = position;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        setOutputPosition(frame, position);
        return null;
    }

}
