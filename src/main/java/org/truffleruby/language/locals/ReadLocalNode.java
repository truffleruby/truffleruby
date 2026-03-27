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
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;

public abstract class ReadLocalNode extends RubyContextSourceNode {

    public static ReadLocalNode create(int frameSlot, int frameDepth) {
        if (frameDepth == 0) {
            return new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, frameSlot);
        } else {
            return new ReadDeclarationVariableNode(LocalVariableType.FRAME_LOCAL, frameDepth, frameSlot);
        }
    }

    protected final int frameSlot;
    protected final LocalVariableType type;

    @Child protected ReadFrameSlotNode readFrameSlotNode;

    public ReadLocalNode(int frameSlot, LocalVariableType type) {
        this.frameSlot = frameSlot;
        this.type = type;
    }

    protected abstract Object readFrameSlot(VirtualFrame frame);

    protected abstract String getVariableName();

    public int getFrameSlot() {
        return frameSlot;
    }

    public abstract int getFrameDepth();

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        switch (type) {
            case FRAME_LOCAL:
                return FrozenStrings.LOCAL_VARIABLE;

            case FRAME_LOCAL_GLOBAL:
                if (readFrameSlot(frame) != nil) {
                    return FrozenStrings.GLOBAL_VARIABLE;
                } else {
                    return nil;
                }

            default:
                throw Utils.unsupportedOperation("didn't expect local type ", type);
        }
    }


    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == ReadVariableTag.class || super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return new SingleMemberDescriptor(ReadVariableTag.NAME, getVariableName());
    }

}
