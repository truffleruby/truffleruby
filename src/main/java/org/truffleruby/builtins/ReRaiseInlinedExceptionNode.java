/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.builtins;

import com.oracle.truffle.api.dsl.NodeFactory;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Arrays;

public final class ReRaiseInlinedExceptionNode extends RubyContextSourceNode {

    public final NodeFactory<? extends RubyBaseNode> nodeFactory;

    public ReRaiseInlinedExceptionNode(NodeFactory<? extends RubyBaseNode> nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof RaiseException)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(
                    "CallTarget of always-inlined builtin should be called with a single RaiseException argument" +
                            ", but was called with: " + Arrays.toString(arguments));
        }

        final RaiseException raiseException = (RaiseException) arguments[0];

        final RubyException rubyException = raiseException.getException();
        // We need a new Backtrace to reset the Backtrace#getLocation()
        rubyException.backtrace = getContext().getCallStack().getBacktrace(this);
        // We need a new RaiseException for the new Backtrace, so RaiseException#getLocation() is correct
        throw new RaiseException(getContext(), rubyException);
    }

    @Override
    public RubyNode cloneUninitialized() {
        throw CompilerDirectives.shouldNotReachHere(getClass() + " should never be split");
    }

}
