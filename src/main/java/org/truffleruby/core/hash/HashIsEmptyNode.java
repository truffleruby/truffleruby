/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class HashIsEmptyNode extends RubyContextSourceNode {

    @Child RubyNode currentValueToMatch;

    public HashIsEmptyNode(RubyNode currentValueToMatch) {
        this.currentValueToMatch = currentValueToMatch;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RubyHash matchHash = (RubyHash) currentValueToMatch.execute(frame);
        return matchHash.empty();
    }

    @Override
    public RubyNode cloneUninitialized() {
        return new HashIsEmptyNode(currentValueToMatch.cloneUninitialized()).copyFlags(this);
    }
}
