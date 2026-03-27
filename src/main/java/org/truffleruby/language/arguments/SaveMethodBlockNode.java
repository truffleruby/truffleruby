/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
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

public final class SaveMethodBlockNode extends RubyContextSourceNode {

    private final int slot;

    public SaveMethodBlockNode(int slot) {
        this.slot = slot;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        frame.setObject(slot, RubyArguments.getBlock(frame));
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new SaveMethodBlockNode(slot);
        return copy.copyFlags(this);
    }

}
