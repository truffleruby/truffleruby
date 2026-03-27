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
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.utils.RunTwiceBranchProfile;

/** OrLazyValueDefinedNode is used as the 'or' node for ||=, because we know from idiomatic Ruby usage that this is
 * often used to lazy initialize a value. In that case normal counting profiling gives a misleading result. With the RHS
 * having been executed once (the lazy initialization) it will be compiled expecting it to be used again. We know that
 * it's unlikely to be used again, so only compile it in when it's been used more than once, by using a
 * {@link RunTwiceBranchProfile}. */
public abstract class OrLazyValueDefinedNode extends RubyContextSourceNode {

    @Child private RubyNode left;
    @Child private RubyNode right;

    private final RunTwiceBranchProfile rightTwiceProfile = new RunTwiceBranchProfile();

    public OrLazyValueDefinedNode(RubyNode left, RubyNode right) {
        this.left = left;
        this.right = right;
    }

    @Specialization
    Object doOrLazyValueDefined(VirtualFrame frame,
            @Cached BooleanCastNode leftCast,
            @Cached InlinedCountingConditionProfile countingProfile) {
        final Object leftValue = left.execute(frame);

        if (countingProfile.profile(this, leftCast.execute(this, leftValue))) {
            return leftValue;
        } else {
            rightTwiceProfile.enter();
            return right.execute(frame);
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = OrLazyValueDefinedNodeGen.create(
                left.cloneUninitialized(),
                right.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
