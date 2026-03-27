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
package org.truffleruby.language.arguments;

import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class ArrayIsAtLeastAsLargeAsNode extends RubyContextSourceNode {

    private final int requiredSize;

    @Child private RubyNode child;

    public ArrayIsAtLeastAsLargeAsNode(int requiredSize, RubyNode child) {
        this.requiredSize = requiredSize;
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int actualSize = ((RubyArray) child.execute(frame)).size;
        return actualSize >= requiredSize;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ArrayIsAtLeastAsLargeAsNode(
                requiredSize,
                child.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
