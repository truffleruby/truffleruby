/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreMethodNodeManager;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.truffleruby.language.RubyCheckArityRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

@ReportPolymorphism // inline cache
@GenerateUncached
@ImportStatic(RubyArguments.class)
public abstract class CallInternalMethodNode extends RubyBaseNode {

    @NeverDefault
    public static CallInternalMethodNode create() {
        return CallInternalMethodNodeGen.create();
    }

    /** Callers should use {@link RubyArguments#assertFrameArguments} unless they use {@code RubyArguments#pack} */
    public abstract Object execute(Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs);

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "method.getCallTarget() == cachedCallTarget",
                    "!cachedMethod.alwaysInlined()" },
            assumptions = "getMethodAssumption(cachedMethod)", // to remove the inline cache entry when the method is redefined or removed
            limit = "getCacheLimit()")
    Object callCached(InternalMethod method, Object receiver, Object[] rubyArgs,
            @Cached("method.getCallTarget()") RootCallTarget cachedCallTarget,
            @Cached("method") InternalMethod cachedMethod,
            @Cached("createCall(cachedMethod.getName(), cachedCallTarget)") DirectCallNode callNode) {
        return callNode.call(RubyArguments.repackForCall(rubyArgs));
    }

    @InliningCutoff
    @Specialization(guards = "!method.alwaysInlined()", replaces = "callCached")
    Object callUncached(InternalMethod method, Object receiver, Object[] rubyArgs,
            @Cached IndirectCallNode indirectCallNode) {
        return indirectCallNode.call(method.getCallTarget(), RubyArguments.repackForCall(rubyArgs));
    }

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "method.getCallTarget() == cachedCallTarget",
                    "cachedMethod.alwaysInlined()" },
            assumptions = "getMethodAssumption(cachedMethod)", // to remove the inline cache entry when the method is redefined or removed
            limit = "getCacheLimit()")
    static Object alwaysInlined(Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs,
            @Cached("method.getCallTarget()") RootCallTarget cachedCallTarget,
            @Cached("method") InternalMethod cachedMethod,
            @Cached("createAlwaysInlinedMethodNode(cachedMethod)") AlwaysInlinedMethodNode alwaysInlinedNode,
            @Cached("cachedMethod.getSharedMethodInfo().getArity()") Arity cachedArity,
            @Cached InlinedBranchProfile checkArityProfile,
            @Cached InlinedBranchProfile exceptionProfile,
            @Bind Node node) {
        assert !cachedArity
                .acceptsKeywords() : "AlwaysInlinedMethodNodes are currently assumed to not use keyword arguments, the arity check depends on this";
        assert RubyArguments.getSelf(rubyArgs) == receiver;

        try {
            int given = RubyArguments.getPositionalArgumentsCount(rubyArgs);
            if (!cachedArity.checkPositionalArguments(given)) {
                checkArityProfile.enter(node);
                throw RubyCheckArityRootNode.checkArityError(cachedArity, given, alwaysInlinedNode);
            }

            return alwaysInlinedNode.execute(frame, receiver, RubyArguments.repackForCall(rubyArgs), cachedCallTarget);
        } catch (RaiseException e) {
            exceptionProfile.enter(node);
            return alwaysInlinedException(node, e, alwaysInlinedNode, cachedCallTarget);
        }
    }

    @InliningCutoff
    private static Object alwaysInlinedException(Node node, RaiseException e, AlwaysInlinedMethodNode alwaysInlinedNode,
            RootCallTarget cachedCallTarget) {
        final Node location = e.getLocation();
        final Node adoptedInlinedNode = getAdoptedNode(alwaysInlinedNode);
        final var isErrorFromInlineNode = location != null && adoptedInlinedNode != null &&
                location.getRootNode() == adoptedInlinedNode.getRootNode();
        /* In the cached scenario the getAdoptedNode method call will have no effect. In the uncached scenario the
         * alwaysInlinedNode is uncached, therefore no RootNode so that's why getAdoptedNode is called to determine
         * whether the exception was thrown by the alwaysInlineNode. */
        if (isErrorFromInlineNode) {
            // if the error originates from the inlined node, rethrow it through the CallTarget to get a proper backtrace
            return RubyContext.indirectCallWithCallNode(node, cachedCallTarget, e);
        } else {
            throw e;
        }
    }

    @Specialization(guards = "method.alwaysInlined()", replaces = "alwaysInlined")
    Object alwaysInlinedUncached(Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs) {
        return alwaysInlinedBoundary(
                frame == null ? null : frame.materialize(),
                method,
                receiver,
                rubyArgs,
                isAdoptable());
    }

    @TruffleBoundary // getUncachedAlwaysInlinedMethodNode(method) and arity are not PE constants
    private Object alwaysInlinedBoundary(
            MaterializedFrame frame, InternalMethod method, Object receiver, Object[] rubyArgs,
            boolean cachedToUncached) {
        EncapsulatingNodeReference encapsulating = null;
        Node prev = null;
        if (cachedToUncached) {
            encapsulating = EncapsulatingNodeReference.getCurrent();
            prev = encapsulating.set(this);
        }
        try {
            return alwaysInlined(
                    frame,
                    method,
                    receiver,
                    rubyArgs,
                    method.getCallTarget(),
                    method,
                    getUncachedAlwaysInlinedMethodNode(method),
                    method.getSharedMethodInfo().getArity(),
                    InlinedBranchProfile.getUncached(),
                    InlinedBranchProfile.getUncached(),
                    this);
        } finally {
            if (cachedToUncached) {
                encapsulating.set(prev);
            }
        }
    }

    protected AlwaysInlinedMethodNode createAlwaysInlinedMethodNode(InternalMethod method) {
        return (AlwaysInlinedMethodNode) CoreMethodNodeManager
                .createNodeFromFactory(method.alwaysInlinedNodeFactory, RubyNode.EMPTY_ARRAY);
    }

    /** Asserted in {@link CoreMethodNodeManager#createCoreMethodCallTarget} */
    protected AlwaysInlinedMethodNode getUncachedAlwaysInlinedMethodNode(InternalMethod method) {
        return (AlwaysInlinedMethodNode) method.alwaysInlinedNodeFactory.getUncachedInstance();
    }

    protected Assumption getMethodAssumption(InternalMethod method) {
        return isSingleContext()
                ? method.getDeclaringModule().fields.getOrCreateMethodAssumption(method.getName())
                : Assumption.ALWAYS_VALID;
    }

    protected int getCacheLimit() {
        return getLanguage().options.DISPATCH_CACHE;
    }

    protected DirectCallNode createCall(String methodName, RootCallTarget callTarget) {
        final DirectCallNode callNode = DirectCallNode.create(callTarget);
        final DispatchNode dispatch = NodeUtil.findParent(this, DispatchNode.class);
        if (dispatch != null) {
            dispatch.applySplittingInliningStrategy(callTarget, methodName, callNode);
        }
        return callNode;
    }
}
