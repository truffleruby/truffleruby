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
package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.CallForeignMethodNode;

import static org.truffleruby.language.dispatch.DispatchNode.MISSING;

@GenerateInline(false)
@GenerateUncached
@ImportStatic(MissingBehavior.class)
public abstract class DispatchMethodMissingNode extends RubyBaseNode {

    public abstract Object execute(Frame frame, Object receiver, String methodName, Object[] rubyArgs,
            DispatchConfiguration config);

    @Specialization(guards = "config.missingBehavior == RETURN_MISSING")
    static Object dispatchReturnMissing(
            Frame frame, Object receiver, String methodName, Object[] rubyArgs, DispatchConfiguration config) {
        return MISSING;
    }

    @InliningCutoff
    @Specialization(guards = { "config.missingBehavior == CALL_METHOD_MISSING", "isForeignObject(receiver)" })
    static Object dispatchForeign(
            Frame frame, Object receiver, String methodName, Object[] rubyArgs, DispatchConfiguration config,
            @Cached CallForeignMethodNode callForeign) {
        final Object block = RubyArguments.getBlock(rubyArgs);
        final Object[] arguments = RubyArguments.getPositionalArguments(rubyArgs);
        return callForeign.execute(receiver, methodName, block, arguments);
    }

    @InliningCutoff
    @Specialization(guards = { "config.missingBehavior == CALL_METHOD_MISSING", "!isForeignObject(receiver)" })
    static Object dispatchMissingMethod(
            Frame frame, Object receiver, String methodName, Object[] rubyArgs, DispatchConfiguration config,
            @Cached ToSymbolNode toSymbol,
            @Cached DispatchNode callMethodMissing,
            @Cached InlinedBranchProfile methodMissingMissingProfile,
            @Bind Node node) {
        final RubySymbol symbolName = toSymbol.execute(node, methodName);

        final Object[] newArgs = RubyArguments.repack(rubyArgs, receiver, 0, 1);

        RubyArguments.setArgument(newArgs, 0, symbolName);
        final Object result = callMethodMissing.execute(frame, receiver, "method_missing", newArgs,
                DispatchConfiguration.PRIVATE_RETURN_MISSING_IGNORE_REFINEMENTS);

        if (result == MISSING) {
            methodMissingMissingProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).noMethodErrorFromMethodMissing(
                    ExceptionOperations.ExceptionFormatter.NO_METHOD_ERROR,
                    receiver,
                    methodName,
                    RubyArguments.getPositionalArguments(rubyArgs),
                    node));
        }

        return result;
    }
}
