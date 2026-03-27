/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.debug.SingleMemberDescriptor;
import org.truffleruby.language.RubyContextSourceAssignableNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;

public abstract class WriteLocalNode extends RubyContextSourceAssignableNode {

    public static WriteLocalNode create(int frameSlot, int frameDepth, RubyNode valueNode) {
        if (frameDepth == 0) {
            return new WriteLocalVariableNode(frameSlot, valueNode);
        } else {
            return new WriteDeclarationVariableNode(frameSlot, frameDepth, valueNode);
        }
    }

    protected final int frameSlot;

    @Child protected RubyNode valueNode;

    public WriteLocalNode(int frameSlot, RubyNode valueNode) {
        this.frameSlot = frameSlot;
        this.valueNode = valueNode;
    }

    protected abstract String getVariableName();

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    public void setValueNode(RubyNode valueNode) {
        this.valueNode = valueNode;
    }

    public int getFrameSlot() {
        return frameSlot;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == WriteVariableTag.class || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        String name = getVariableName();
        return new SingleMemberDescriptor(WriteVariableTag.NAME, name);
    }
}
