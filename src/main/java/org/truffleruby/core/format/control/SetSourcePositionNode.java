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
import org.truffleruby.core.format.exceptions.RangeException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class SetSourcePositionNode extends FormatNode {

    private final int position;

    private final ConditionProfile rangeProfile = ConditionProfile.create();

    public SetSourcePositionNode(int position) {
        this.position = position;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        int positionWithStartOffset = getSourceStart(frame) + position;

        if (rangeProfile.profile(positionWithStartOffset > getSourceEnd(frame))) {
            throw new OutsideOfStringException();
        }

        if (position < 0) {
            throw new RangeException("pack length too big");
        }

        setSourcePosition(frame, positionWithStartOffset);
        return null;
    }

}
