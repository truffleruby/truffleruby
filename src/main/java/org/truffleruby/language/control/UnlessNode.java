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

public abstract class UnlessNode extends RubyContextSourceNode {

    @Child private RubyNode condition;
    @Child private RubyNode thenBody;

    public UnlessNode(RubyNode condition, RubyNode thenBody) {
        this.condition = condition;
        this.thenBody = thenBody;
    }

    @Specialization
    Object doUnless(VirtualFrame frame,
            @Cached InlinedCountingConditionProfile conditionProfile,
            @Cached BooleanCastNode booleanCastNode) {
        if (!conditionProfile.profile(this, booleanCastNode.execute(this, condition.execute(frame)))) {
            return thenBody.execute(frame);
        } else {
            return nil;
        }
    }

    @Override
    public boolean canSubsumeFollowing() {
        return !thenBody.isContinuable();
    }

    @Override
    public RubyNode subsumeFollowing(RubyNode following) {
        return IfElseNodeGen.create(condition, following, thenBody).copySourceSection(this);
    }

    @Override
    public RubyNode simplifyAsTailExpression() {
        return UnlessNodeGen.create(condition, thenBody.simplifyAsTailExpression()).copySourceSection(this);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = UnlessNodeGen.create(
                condition.cloneUninitialized(),
                thenBody.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
