/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild(value = "valueNode", type = RubyBaseNodeWithExecute.class)
public abstract class ToSNode extends RubyBaseNodeWithExecute {

    @Child private KernelNodes.ToSNode kernelToSNode;

    public static ToSNode create(RubyBaseNodeWithExecute value) {
        return ToSNodeGen.create(value);
    }

    public abstract RubyBaseNodeWithExecute getValueNode();

    @Specialization
    RubyString toS(RubyString string) {
        return string;
    }

    @Specialization
    ImmutableRubyString toS(ImmutableRubyString string) {
        return string;
    }

    @Specialization(guards = "isNotRubyString(object)")
    Object toSFallback(VirtualFrame frame, Object object,
            @Cached DispatchNode callToSNode,
            @Cached RubyStringLibrary libString) {
        final Object value = callToSNode.callWithFrame(frame, object, "to_s");

        if (libString.isRubyString(this, value)) {
            return value;
        } else {
            return kernelToS(object);
        }
    }

    protected RubyString kernelToS(Object object) {
        if (kernelToSNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            kernelToSNode = insert(KernelNodes.ToSNode.create());
        }
        return kernelToSNode.execute(object);
    }

    @Override
    public RubyBaseNodeWithExecute cloneUninitialized() {
        return create(getValueNode().cloneUninitialized());
    }
}
