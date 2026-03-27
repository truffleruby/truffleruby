/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.dispatch;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.AssignableNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;

@GenerateWrapper
public abstract class LiteralCallAssignableNode extends LiteralCallNode implements AssignableNode {

    protected LiteralCallAssignableNode(boolean isSplatted, ArgumentsDescriptor descriptor) {
        super(isSplatted, descriptor);
    }

    // Constructor for instrumentation
    protected LiteralCallAssignableNode() {
        this(false, null);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new LiteralCallAssignableNodeWrapper(this, probeNode);
    }

    // Declared abstract here so the instrumentation wrapper delegates it
    @Override
    public abstract Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context);

}
