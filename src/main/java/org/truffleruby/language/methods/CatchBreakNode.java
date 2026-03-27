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
package org.truffleruby.language.methods;

import com.oracle.truffle.api.profiles.CountingConditionProfile;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.BreakException;
import org.truffleruby.language.control.BreakID;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class CatchBreakNode extends RubyContextSourceNode {

    private final boolean isWhile;
    private final BreakID breakID;

    @Child private RubyNode body;

    private final CountingConditionProfile matchingBreakProfile = CountingConditionProfile.create();
    private final ConditionProfile anyBlockProfile;

    public CatchBreakNode(BreakID breakID, RubyNode body, boolean isWhile) {
        assert breakID != BreakID.INVALID;
        this.isWhile = isWhile;
        this.breakID = breakID;
        this.body = body;
        this.anyBlockProfile = isWhile ? null : ConditionProfile.create();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return body.execute(frame);
        } catch (BreakException e) {
            if (matchingBreakProfile.profile(e.getBreakID() == breakID)) {
                return e.getResult();
            } else if (!isWhile && anyBlockProfile.profile(e.getBreakID() == BreakID.ANY_BLOCK)) {
                return e.getResult();
            } else {
                throw e;
            }
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new CatchBreakNode(
                breakID,
                body.cloneUninitialized(),
                isWhile);
        return copy.copyFlags(this);
    }

}
