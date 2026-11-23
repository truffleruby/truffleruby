/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.TranslateExceptionNode;

/** A node replacing a {@link org.truffleruby.language.dispatch.RubyCallNode} to execute a core method directly at the
 * call site, as long as that core method is not redefined.
 * <p>
 * This should only be used for methods on primitive receivers ({@code int}, {@code long}, {@code double},
 * {@code boolean}). For those the class of the receiver is known from its Java type, so checking that the core method
 * is not redefined is just an {@link Assumption} and the method lookup is avoided entirely.
 * <p>
 * For any other receiver, the method needs to be looked up anyway to know whether it is the core method (e.g. with
 * {@link org.truffleruby.language.methods.LookupMethodOnSelfNode}), so there is no such gain over a regular call. In
 * that case use an {@link AlwaysInlinedMethodNode} instead, which is also executed at the call site without going
 * through a CallTarget. That also avoids duplicating the logic of the core method in two places: in the
 * {@code InlinedOperationNode} and in the {@link org.truffleruby.annotations.CoreMethod} node needed for all other
 * ways to call the method ({@code send}, {@code Method#call}, after {@link #rewriteAndCall}, etc). */
public abstract class InlinedOperationNode extends InlinedReplaceableNode {

    public InlinedOperationNode(
            RubyLanguage language,
            RubyCallNodeParameters callNodeParameters,
            Assumption... assumptions) {
        super(language, callNodeParameters, assumptions);
    }

    protected Object rewriteAndCall(VirtualFrame frame, Object receiver, Object... arguments) {
        return rewriteAndCallWithBlock(frame, receiver, nil, arguments);
    }

    protected Object rewriteAndCallWithBlock(VirtualFrame frame, Object receiver, Object block,
            Object... arguments) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (getContext().getOptions().BASICOPS_LOG_REWRITE) {
            RubyLanguage.LOGGER.info(
                    "Rewrite " + this + " with arguments" + TranslateExceptionNode
                            .argumentsToString(new StringBuilder(), ArrayUtils.unshift(arguments, receiver)));
        }
        return rewriteToCallNode().executeWithArgumentsEvaluated(frame, receiver, block, arguments);
    }

    protected CoreMethods coreMethods() {
        return getContext().getCoreMethods();
    }
}
