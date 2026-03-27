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
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class NotNode extends RubyContextSourceNode {

    @Child private RubyNode child;

    public NotNode(RubyNode child) {
        this.child = child;
    }

    @Specialization
    Object doNot(VirtualFrame frame,
            @Cached BooleanCastNode booleanCastNode) {
        final var valueAsBoolean = booleanCastNode.execute(this, child.execute(frame));
        return !valueAsBoolean;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = NotNodeGen.create(child.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
