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
package org.truffleruby.language.objects;

import com.oracle.truffle.api.object.HiddenKey;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.locals.ReadFrameSlotNode;
import org.truffleruby.language.locals.ReadFrameSlotNodeGen;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SelfNode extends RubyContextSourceNode {

    public static final int SELF_INDEX = 0;
    public static final HiddenKey SELF_IDENTIFIER = new HiddenKey("(self)");

    @Child private ReadFrameSlotNode readSelfSlotNode;

    public SelfNode() {
    }

    public static ReadFrameSlotNode createReadSelfFrameSlotNode() {
        return ReadFrameSlotNodeGen.create(SELF_INDEX);
    }

    public static Object readSelf(VirtualFrame frame, ReadFrameSlotNode readSelfSlotNode) {
        assert frame.getFrameDescriptor().getSlotName(SELF_INDEX) == SELF_IDENTIFIER;
        return readSelfSlotNode.executeRead(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (readSelfSlotNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readSelfSlotNode = insert(createReadSelfFrameSlotNode());
        }

        return readSelf(frame, readSelfSlotNode);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.SELF;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new SelfNode();
        return copy.copyFlags(this);
    }

}
