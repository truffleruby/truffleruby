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
package org.truffleruby.language.literal;

import org.truffleruby.core.CoreLibrary;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class LongFixnumLiteralNode extends RubyContextSourceNode {

    private final long value;

    public LongFixnumLiteralNode(long value) {
        assert !CoreLibrary.fitsIntoInteger(value) : "long in int range : " + value;
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return value;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new LongFixnumLiteralNode(value);
        return copy.copyFlags(this);
    }

}
