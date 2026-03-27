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
package org.truffleruby.language.control;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class OrNode extends RubyContextSourceNode {

    @Child private RubyNode left;
    @Child private RubyNode right;

    public OrNode(RubyNode left, RubyNode right) {
        this.left = left;
        this.right = right;
    }

    @Specialization
    Object doOr(VirtualFrame frame,
            @Cached BooleanCastNode leftCast,
            @Cached InlinedCountingConditionProfile conditionProfile) {
        final Object leftValue = left.execute(frame);

        if (conditionProfile.profile(this, leftCast.execute(this, leftValue))) {
            return leftValue;
        } else {
            return right.execute(frame);
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = OrNodeGen.create(
                left.cloneUninitialized(),
                right.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
