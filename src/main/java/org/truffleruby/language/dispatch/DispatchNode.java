/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.SpecialVariablesSendingNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.options.Options;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;

@ImportStatic(Assumption.class)
@GenerateUncached
public abstract class DispatchNode extends SpecialVariablesSendingNode {

    private static final class Missing implements TruffleObject {
    }

    public static final Missing MISSING = new Missing();

    @NeverDefault
    public static DispatchNode create() {
        return DispatchNodeGen.create();
    }

    @NeverDefault
    public static DispatchNode getUncached() {
        return DispatchNodeGen.getUncached();
    }


    public abstract Object execute(Frame frame, Object receiver, String methodName, Object[] rubyArgs,
            DispatchConfiguration config);

    public Object call(DispatchConfiguration config, Object receiver, String method) {
        final Object[] rubyArgs = RubyArguments.allocate(0);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object call(Object receiver, String method) {
        final Object[] rubyArgs = RubyArguments.allocate(0);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object call(DispatchConfiguration config, Object receiver, String method, Object arg1) {
        final Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object call(Object receiver, String method, Object arg1) {
        final Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object call(DispatchConfiguration config, Object receiver, String method, Object arg1, Object arg2) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object call(Object receiver, String method, Object arg1, Object arg2) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object call(Object receiver, String method, Object arg1, Object arg2, Object arg3) {
        final Object[] rubyArgs = RubyArguments.allocate(3);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        RubyArguments.setArgument(rubyArgs, 2, arg3);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object call(DispatchConfiguration config, Object receiver, String method, Object[] arguments) {
        final Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArguments(rubyArgs, arguments);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object call(Object receiver, String method, Object[] arguments) {
        final Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArguments(rubyArgs, arguments);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithKeywords(Object receiver, String method, Object arg1,
            RubyHash keywords) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, KeywordArgumentsDescriptor.EMPTY);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, keywords);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithBlock(Object receiver, String method, Object block) {
        final Object[] rubyArgs = RubyArguments.allocate(0);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithBlock(Object receiver, String method, Object block, Object arg1) {
        final Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithBlock(Object receiver, String method, Object block, Object arg1, Object arg2) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithBlock(DispatchConfiguration config, Object receiver, String method, Object block, Object arg1,
            Object arg2) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object callWithBlock(DispatchConfiguration config, Object receiver, String method, Object block, Object arg1,
            Object arg2, Object arg3) {
        final Object[] rubyArgs = RubyArguments.allocate(3);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        RubyArguments.setArgument(rubyArgs, 2, arg3);
        return execute(null, receiver, method, rubyArgs, config);
    }

    public Object callWithDescriptor(Object receiver, String method, Object block,
            ArgumentsDescriptor descriptor, Object[] arguments) {
        final Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, descriptor);
        RubyArguments.setArguments(rubyArgs, arguments);
        return execute(null, receiver, method, rubyArgs, PRIVATE);
    }

    public Object callWithFrame(Frame frame, Object receiver, String method) {
        return callWithFrame(PRIVATE, frame, receiver, method);
    }

    public Object callWithFrame(DispatchConfiguration config, Frame frame, Object receiver, String method) {
        final Object[] rubyArgs = RubyArguments.allocate(0);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        return execute(frame, receiver, method, rubyArgs, config);
    }

    public Object callWithFrame(DispatchConfiguration config, Frame frame, Object receiver, String method,
            Object arg1) {
        final Object[] rubyArgs = RubyArguments.allocate(1);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        return execute(frame, receiver, method, rubyArgs, config);
    }

    public Object callWithFrame(DispatchConfiguration config, Frame frame, Object receiver, String method, Object arg1,
            Object arg2) {
        final Object[] rubyArgs = RubyArguments.allocate(2);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        return execute(frame, receiver, method, rubyArgs, config);
    }

    public Object callWithFrame(DispatchConfiguration config, Frame frame, Object receiver, String method, Object arg1,
            Object arg2, Object arg3) {
        final Object[] rubyArgs = RubyArguments.allocate(3);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArgument(rubyArgs, 0, arg1);
        RubyArguments.setArgument(rubyArgs, 1, arg2);
        RubyArguments.setArgument(rubyArgs, 2, arg3);
        return execute(frame, receiver, method, rubyArgs, config);
    }

    public Object callWithFrame(DispatchConfiguration config, Frame frame, Object receiver, String method,
            Object[] arguments) {
        final Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, nil);
        RubyArguments.setDescriptor(rubyArgs, NoKeywordArgumentsDescriptor.INSTANCE);
        RubyArguments.setArguments(rubyArgs, arguments);
        return execute(frame, receiver, method, rubyArgs, config);
    }

    public final Object callWithFrameAndBlock(DispatchConfiguration config, Frame frame, Object receiver,
            String methodName, Object block, ArgumentsDescriptor descriptor, Object[] arguments) {
        final Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiver);
        RubyArguments.setBlock(rubyArgs, block);
        RubyArguments.setDescriptor(rubyArgs, descriptor);
        RubyArguments.setArguments(rubyArgs, arguments);
        return execute(frame, receiver, methodName, rubyArgs, config);
    }

    @Specialization
    Object dispatch(Frame frame, Object receiver, String methodName, Object[] rubyArgs, DispatchConfiguration config,
            @Cached(value = "getSpecialVariableAssumption(frame)",
                    uncached = "ALWAYS_VALID") Assumption specialVariableAssumption,
            @Cached MetaClassNode metaClassNode,
            @Cached LookupMethodNode lookupMethodNode,
            @Cached InlinedConditionProfile methodMissing,
            @Cached CallInternalMethodNode callNode,
            @Cached GetSpecialVariableStorage readingNode,
            @Cached LazyDispatchMethodMissingNode lazyDispatchMethodMissingNode) {
        assert RubyArguments.getSelf(rubyArgs) == receiver;
        CompilerAsserts.partialEvaluationConstant(config);

        final RubyClass metaclass = metaClassNode.execute(this, receiver);
        final InternalMethod method = lookupMethodNode.execute(frame, metaclass, methodName, config);

        if (methodMissing.profile(this, method == null || method.isUndefined())) {
            return lazyDispatchMethodMissingNode.get(this).execute(frame, receiver, methodName, rubyArgs, config);
        }

        RubyArguments.setMethod(rubyArgs, method);

        if (!specialVariableAssumption.isValid()) {
            RubyArguments.setCallerSpecialVariables(rubyArgs, readingNode.execute(frame, this));
        }
        assert RubyArguments.assertFrameArguments(rubyArgs);

        return callNode.execute(frame, method, receiver, rubyArgs);
    }

    /** This will be called from the {@link CallInternalMethodNode} child whenever it creates a new
     * {@link DirectCallNode}. */
    public final void applySplittingInliningStrategy(RootCallTarget callTarget, String methodName,
            DirectCallNode callNode) {

        final Options options = getContext().getOptions();

        // The way that #method_missing is used is usually as an indirection to call some other method, and possibly to
        // modify the arguments. In both cases, but especially the latter, it makes a lot of sense to manually clone the
        // call target and to inline it.
        final boolean isMethodMissing = methodName.equals("method_missing");

        if (callNode.isCallTargetCloningAllowed() &&
                (RubyRootNode.of(callTarget).shouldAlwaysClone() ||
                        isMethodMissing && options.METHODMISSING_ALWAYS_CLONE)) {
            callNode.cloneCallTarget();
        }

        if (callNode.isInlinable() && isMethodMissing && options.METHODMISSING_ALWAYS_INLINE) {
            callNode.forceInlining();
        }
    }
}
