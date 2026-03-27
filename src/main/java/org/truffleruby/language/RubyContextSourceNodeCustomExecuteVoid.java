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
package org.truffleruby.language;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;

import com.oracle.truffle.api.frame.VirtualFrame;

/** See {@link RubyNode} */
public abstract class RubyContextSourceNodeCustomExecuteVoid extends RubyNode {

    private int sourceCharIndex = NO_SOURCE;
    private int sourceLength;
    private byte flags;

    /** See {@link RubyContextSourceNode#executeVoid(VirtualFrame)} */
    @Override
    public abstract Nil executeVoid(VirtualFrame frame);

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return RubyNode.defaultIsDefined(this);
    }

    @Override
    protected byte getFlags() {
        return flags;
    }

    @Override
    protected void setFlags(byte flags) {
        this.flags = flags;
    }

    @Override
    protected int getSourceCharIndex() {
        return sourceCharIndex;
    }

    @Override
    protected void setSourceCharIndex(int sourceCharIndex) {
        this.sourceCharIndex = sourceCharIndex;
    }

    @Override
    protected int getSourceLength() {
        return sourceLength;
    }

    @Override
    protected void setSourceLength(int sourceLength) {
        this.sourceLength = sourceLength;
    }

    public RubyContextSourceNodeCustomExecuteVoid copyFlags(RubyContextSourceNodeCustomExecuteVoid original) {
        this.sourceCharIndex = original.sourceCharIndex;
        this.sourceLength = original.sourceLength;
        this.flags = original.flags;
        return this;
    }
}
