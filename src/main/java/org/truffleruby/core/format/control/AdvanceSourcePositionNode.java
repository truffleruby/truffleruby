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
package org.truffleruby.core.format.control;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.OutsideOfStringException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class AdvanceSourcePositionNode extends FormatNode {

    private final boolean toEnd;

    private final ConditionProfile rangeProfile = ConditionProfile.create();

    public AdvanceSourcePositionNode(boolean toEnd) {
        this.toEnd = toEnd;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (toEnd) {
            setSourcePosition(frame, getSourceEnd(frame));
        } else {
            final int position = getSourcePosition(frame);

            if (rangeProfile.profile(position + 1 > getSourceEnd(frame))) {
                throw new OutsideOfStringException();
            }

            setSourcePosition(frame, getSourcePosition(frame) + 1);
        }

        return null;
    }

}
