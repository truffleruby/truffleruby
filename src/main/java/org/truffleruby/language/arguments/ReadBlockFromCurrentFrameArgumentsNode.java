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
package org.truffleruby.language.arguments;

import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

/** {@link SaveMethodBlockNode} should be preferred when writing the result to a frame slot. */
public final class ReadBlockFromCurrentFrameArgumentsNode extends RubyContextSourceNode {
    @Override
    public Object execute(VirtualFrame frame) {
        return RubyArguments.getBlock(frame);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadBlockFromCurrentFrameArgumentsNode();
        return copy.copyFlags(this);
    }

}
