/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.printf;

import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.language.WarningNode;

public final class CheckTooManyArgumentsNode extends FormatNode {

    private final int expectedArguments;
    @Child FormatNode child;
    @Child WarningNode warningNode = WarningNode.create();

    public CheckTooManyArgumentsNode(int expectedArguments, FormatNode child) {
        this.expectedArguments = expectedArguments;
        this.child = child;
    }

    private void checkTooManyArguments(Object[] arguments) {
        // don't warn if arguments are passed as a Hash:
        //   format("%<foo>d : %<bar>f", { :foo => 1, :bar => 2 })
        if (arguments.length == 1 && arguments[0] instanceof RubyHash) {
            return;
        }

        if (expectedArguments < arguments.length) {
            warningNode.warningMessage(
                    getContext().getCallStack().getTopMostUserSourceSection(),
                    "too many arguments for format string");
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (expectedArguments >= 0 && warningNode.shouldWarn()) {
            Object[] arguments = (Object[]) frame.getObject(FormatFrameDescriptor.SOURCE_SLOT);
            checkTooManyArguments(arguments);
        }
        return child.execute(frame);
    }

}
