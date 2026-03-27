/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
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

public final class ExecuteAndReturnTrueNode extends RubyContextSourceNode {

    @Child RubyNode child;

    public ExecuteAndReturnTrueNode(RubyNode child) {
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        child.executeVoid(frame);
        return true;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return new ExecuteAndReturnTrueNode(child.cloneUninitialized()).copyFlags(this);
    }
}
