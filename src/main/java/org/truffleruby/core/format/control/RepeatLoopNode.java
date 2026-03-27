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

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class RepeatLoopNode extends FormatNode {

    private final int count;

    @Child private FormatNode child;
    private final LoopConditionProfile loopProfile = LoopConditionProfile.create();

    public RepeatLoopNode(int count, FormatNode child) {
        this.count = count;
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        int i = 0;
        try {
            for (; loopProfile.inject(i < count); i++) {
                child.execute(frame);
                TruffleSafepoint.poll(this);
            }
        } finally {
            profileAndReportLoopCount(loopProfile, i);
        }

        return null;
    }

}
