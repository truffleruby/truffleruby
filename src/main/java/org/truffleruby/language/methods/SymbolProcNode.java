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
package org.truffleruby.language.methods;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

public final class SymbolProcNode extends RubyContextSourceNode {

    private final String symbol;

    @Child private DispatchNode callNode;

    public SymbolProcNode(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert RubyArguments.getRawArgumentsCount(frame) >= 1 : "guaranteed from arity check";

        final Object receiver = RubyArguments.getArgument(frame, 0);

        return getCallNode().execute(frame, receiver, symbol,
                RubyArguments.repack(frame.getArguments(), receiver, 1), PUBLIC);
    }

    private DispatchNode getCallNode() {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callNode = insert(DispatchNode.create());
        }

        return callNode;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new SymbolProcNode(symbol);
        return copy.copyFlags(this);
    }

}
