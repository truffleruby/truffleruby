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
package org.truffleruby.core.format.read.bytes;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadByteNode extends FormatNode {

    private final ConditionProfile rangeProfile = ConditionProfile.create();

    @Specialization(guards = "isNull(source)")
    void read(VirtualFrame frame, Object source) {
        advanceSourcePosition(frame, 1);
        throw new IllegalStateException();
    }

    @Specialization
    Object read(VirtualFrame frame, byte[] source) {
        int index = advanceSourcePositionNoThrow(frame);

        if (rangeProfile.profile(index == -1)) {
            return nil;
        }

        return source[index];
    }

}
