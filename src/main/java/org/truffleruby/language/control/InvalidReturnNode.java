/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
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

public final class InvalidReturnNode extends RubyContextSourceNode {

    @Child public RubyNode value;

    public InvalidReturnNode(RubyNode value) {
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        value.executeVoid(frame);
        throw new RaiseException(getContext(), coreExceptions().unexpectedReturn(this));
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new InvalidReturnNode(value.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
