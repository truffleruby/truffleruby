/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.rescue;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.globals.ReadGlobalVariableNode;
import org.truffleruby.language.globals.ReadGlobalVariableNodeGen;

public final class AssignRescueVariableNode extends RubyContextSourceNode {

    @Child private AssignableNode rescueVariableNode;
    @Child private ReadGlobalVariableNode readCurrentExceptionNode;

    public AssignRescueVariableNode(AssignableNode node) {
        this.rescueVariableNode = node;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (readCurrentExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readCurrentExceptionNode = insert(ReadGlobalVariableNodeGen.create("$!"));
        }

        Object value = readCurrentExceptionNode.execute(frame);
        rescueVariableNode.assign(frame, value);
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new AssignRescueVariableNode(rescueVariableNode.cloneUninitializedAssignable());
        return copy.copyFlags(this);
    }

}
