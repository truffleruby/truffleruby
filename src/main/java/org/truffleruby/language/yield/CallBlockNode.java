/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.yield;

import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.DeclarationContext;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

@ReportPolymorphism // inline cache
@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class CallBlockNode extends RubyBaseNode {

    @NeverDefault
    public static CallBlockNode create() {
        return CallBlockNodeGen.create();
    }

    public static Object executeUncached(DeclarationContext declarationContext, RubyProc block, Object self,
            Object blockArgument, ArgumentsDescriptor descriptor, Object[] arguments) {
        return CallBlockNodeGen.getUncached().executeCallBlock(null, declarationContext, block, self, blockArgument,
                descriptor, arguments);
    }

    public static Object yieldUncached(RubyProc block, Object... args) {
        return CallBlockNodeGen.getUncached().executeCallBlock(null, block.declarationContext, block,
                ProcOperations.getSelf(block), nil, NoKeywordArgumentsDescriptor.INSTANCE, args);
    }

    public final Object yieldCached(RubyProc block, ArgumentsDescriptor descriptor, Object... args) {
        return executeCallBlock(this, block.declarationContext, block, ProcOperations.getSelf(block), nil, descriptor,
                args);
    }

    public final Object yield(Node node, RubyProc block, Object... args) {
        return executeCallBlock(node, block.declarationContext, block, ProcOperations.getSelf(block), nil,
                NoKeywordArgumentsDescriptor.INSTANCE, args);
    }

    public final Object yieldCached(RubyProc block, Object... args) {
        return executeCallBlock(this, block.declarationContext, block, ProcOperations.getSelf(block), nil,
                NoKeywordArgumentsDescriptor.INSTANCE, args);
    }

    public abstract Object executeCallBlock(Node node, DeclarationContext declarationContext, RubyProc block,
            Object self,
            Object blockArgument, ArgumentsDescriptor descriptor, Object[] arguments);

    @Specialization(guards = "block.callTarget == cachedCallTarget", limit = "getCacheLimit()")
    static Object callBlockCached(
            Node node,
            DeclarationContext declarationContext,
            RubyProc block,
            Object self,
            Object blockArgument,
            ArgumentsDescriptor descriptor,
            Object[] arguments,
            @Cached("block.callTarget") RootCallTarget cachedCallTarget,
            @Cached("createBlockCallNode(node, cachedCallTarget)") DirectCallNode callNode) {
        final Object[] frameArguments = packArguments(declarationContext, block, self, blockArgument, descriptor,
                arguments);
        return callNode.call(frameArguments);
    }

    @Specialization(replaces = "callBlockCached")
    static Object callBlockUncached(
            DeclarationContext declarationContext,
            RubyProc block,
            Object self,
            Object blockArgument,
            ArgumentsDescriptor descriptor,
            Object[] arguments,
            @Cached(inline = false) IndirectCallNode callNode) {
        final Object[] frameArguments = packArguments(declarationContext, block, self, blockArgument, descriptor,
                arguments);
        return callNode.call(block.callTarget, frameArguments);
    }

    private static Object[] packArguments(DeclarationContext declarationContext, RubyProc block, Object self,
            Object blockArgument, ArgumentsDescriptor descriptor, Object[] arguments) {
        return RubyArguments.pack(
                block.declarationFrame,
                null,
                block.declaringMethod,
                declarationContext,
                block.frameOnStackMarker,
                self,
                blockArgument,
                descriptor,
                arguments);
    }

    protected static DirectCallNode createBlockCallNode(Node node, RootCallTarget callTarget) {
        final DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callTarget);

        if (callNode.isCallTargetCloningAllowed() && RubyRootNode.of(callTarget).shouldAlwaysClone()) {
            callNode.cloneCallTarget();
        }

        if (getContext(node).getOptions().YIELD_ALWAYS_INLINE && callNode.isInlinable()) {
            callNode.forceInlining();
        }

        return callNode;
    }

    protected int getCacheLimit() {
        return getLanguage().options.YIELD_CACHE;
    }

}
