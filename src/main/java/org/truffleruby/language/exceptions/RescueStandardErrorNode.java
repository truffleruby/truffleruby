/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.exceptions;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class RescueStandardErrorNode extends RescueNode {

    public RescueStandardErrorNode(RubyNode rescueBody, boolean canOmitBacktrace) {
        super(rescueBody, canOmitBacktrace);
    }

    @Override
    public boolean canHandle(Node inliningContext, VirtualFrame frame, Object exceptionObject,
            BooleanCastNode booleanCastNode) {
        return matches(inliningContext, exceptionObject, coreLibrary().standardErrorClass, booleanCastNode);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return RubyNode.defaultIsDefined(this);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new RescueStandardErrorNode(getRescueBody().cloneUninitialized(), canOmitBacktrace);
        return copy.copyFlags(this);
    }

}
