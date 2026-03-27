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
package org.truffleruby.core.format.control;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.OutsideOfStringException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class ReverseOutputPositionNode extends FormatNode {

    private final ConditionProfile rangeProfile = ConditionProfile.create();

    @Override
    public Object execute(VirtualFrame frame) {
        final int position = getOutputPosition(frame);

        if (rangeProfile.profile(position == 0)) {
            throw new OutsideOfStringException();
        }

        setOutputPosition(frame, position - 1);
        return null;
    }

}
