/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class ShouldDestructureNode extends RubyContextSourceNode {

    @Child RubyNode splatAndWriteNode;

    private final boolean keywordArguments;

    public ShouldDestructureNode(boolean keywordArguments, RubyNode splatAndWriteNode) {
        this.keywordArguments = keywordArguments;
        this.splatAndWriteNode = splatAndWriteNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (RubyArguments.getDescriptor(frame) instanceof KeywordArgumentsDescriptor) {
            return false;
        }

        if (RubyArguments.getPositionalArgumentsCount(frame, keywordArguments) != 1) {
            return false;
        }

        return splatAndWriteNode.execute(frame) != nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ShouldDestructureNode(keywordArguments, splatAndWriteNode.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
