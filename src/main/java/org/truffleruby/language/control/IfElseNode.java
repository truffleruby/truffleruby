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

public abstract class IfElseNode extends RubyContextSourceNode {

    @Child private RubyNode condition;
    @Child private RubyNode thenBody;
    @Child private RubyNode elseBody;

    public IfElseNode(RubyNode condition, RubyNode thenBody, RubyNode elseBody) {
        this.condition = condition;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
    }

    @Specialization
    Object doIfElse(VirtualFrame frame,
            @Cached InlinedCountingConditionProfile conditionProfile,
            @Cached BooleanCastNode booleanCastNode) {
        final var conditionAsBoolean = booleanCastNode.execute(this, condition.execute(frame));
        if (conditionProfile.profile(this, conditionAsBoolean)) {
            return thenBody.execute(frame);
        } else {
            return elseBody.execute(frame);
        }
    }

    @Override
    public boolean isContinuable() {
        return thenBody.isContinuable() || elseBody.isContinuable();
    }

    @Override
    public RubyNode simplifyAsTailExpression() {
        final RubyNode newThen = thenBody.simplifyAsTailExpression();
        final RubyNode newElse = elseBody.simplifyAsTailExpression();
        return IfElseNodeGen.create(condition, newThen, newElse).copySourceSection(this);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = IfElseNodeGen.create(
                condition.cloneUninitialized(),
                thenBody.cloneUninitialized(),
                elseBody.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
