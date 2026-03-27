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
package org.truffleruby.language.arguments;

import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.RubyNode;

/** Assumes no keyword parameters */
public final class ReadRemainingArgumentsNode extends RubyContextSourceNode {

    private final int start;
    private final ConditionProfile remainingArguments = ConditionProfile.create();

    public ReadRemainingArgumentsNode(int start) {
        this.start = start;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int count = RubyArguments.getPositionalArgumentsCount(frame.getArguments());

        if (remainingArguments.profile(start < count)) {
            return RubyArguments.getRawArguments(frame, start, count - start);
        } else {
            return EMPTY_ARGUMENTS;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadRemainingArgumentsNode(start);
        return copy.copyFlags(this);
    }

}
