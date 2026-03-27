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
package org.truffleruby.language;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.AssignableNode;

// Checkstyle: stop
@GenerateWrapper
public abstract class RubyContextSourceAssignableNode extends RubyContextSourceNode implements AssignableNode {
    // Checkstyle: resume

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new RubyContextSourceAssignableNodeWrapper(this, probeNode);
    }

    // Declared abstract here so the instrumentation wrapper delegates it
    @Override
    public abstract Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context);

}
