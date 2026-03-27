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
package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.CountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayAppendOneNode;
import org.truffleruby.core.array.ArrayConcatNode;
import org.truffleruby.core.array.ArrayLiteralNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.inlined.LambdaToProcNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.arguments.SplatToArgsNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;
import org.truffleruby.parser.DeadNode;

import java.util.Map;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PROTECTED;

public final class RubyCallNode extends LiteralCallAssignableNode {

    private final String methodName;

    @Child private RubyNode receiver;
    @Child private RubyNode block;
    @Children private final RubyNode[] arguments;

    private final DispatchConfiguration dispatchConfig;
    private final boolean isVCall;
    private final boolean isSafeNavigation;
    private final boolean isAttrAssign;

    @Child private DispatchNode dispatch;
    @Child private DefinedNode definedNode;

    private final CountingConditionProfile nilProfile;

    @Child private SplatToArgsNode splatToArgs;

    public RubyCallNode(RubyCallNodeParameters parameters) {
        this(
                parameters.isSplatted(),
                parameters.getDescriptor(),
                parameters.getMethodName(),
                parameters.getReceiver(),
                parameters.getArguments(),
                parameters.getBlock(),
                parameters.isIgnoreVisibility() ? PRIVATE : PROTECTED,
                parameters.isVCall(),
                parameters.isSafeNavigation(),
                parameters.isAttrAssign());
    }

    public RubyCallNode(
            boolean isSplatted,
            ArgumentsDescriptor descriptor,
            String methodName,
            RubyNode receiver,
            RubyNode[] arguments,
            RubyNode block,
            DispatchConfiguration dispatchConfig,
            boolean isVCall,
            boolean isSafeNavigation,
            boolean isAttrAssign) {
        super(isSplatted, descriptor);

        this.methodName = methodName;
        this.receiver = receiver;
        this.arguments = arguments;
        this.block = block;
        this.dispatchConfig = dispatchConfig;
        this.isVCall = isVCall;
        this.isSafeNavigation = isSafeNavigation;
        this.isAttrAssign = isAttrAssign;

        if (isSafeNavigation) {
            nilProfile = CountingConditionProfile.create();
        } else {
            nilProfile = null;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);
        if (isSafeNavigation && nilProfile.profile(receiverObject == nil)) {
            return nil;
        }
        Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);

        final ArgumentsDescriptor descriptor;
        executeArguments(frame, rubyArgs);
        if (isSplatted) {
            rubyArgs = splatArgs(receiverObject, rubyArgs);
            descriptor = getArgumentsDescriptorAndCheckRuby2KeywordsHash(rubyArgs,
                    RubyArguments.getRawArgumentsCount(rubyArgs));
        } else {
            descriptor = this.descriptor;
        }

        RubyArguments.setBlock(rubyArgs, executeBlock(frame));

        return doCall(frame, receiverObject, descriptor, rubyArgs);
    }

    // Assignment in context of method call means implicit assignment:
    // - of attribute (a.b = c) or
    // - element reference (a[:b] = c)
    // e.g. in the following cases:
    // - multiple assignment (a.b, c = 1, 2)
    // - exception in rescue (rescue => a.b)
    @Override
    public void assign(VirtualFrame frame, Object value) {
        assert ((getLastArgumentNode() instanceof NilLiteralNode) ||
                getLastArgumentNode() instanceof DeadNode) : getLastArgumentNode();

        final Object receiverObject = receiver.execute(frame);
        if (isSafeNavigation && nilProfile.profile(receiverObject == nil)) {
            return;
        }
        Object[] rubyArgs = RubyArguments.allocate(arguments.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);

        executeArgumentsToAssign(frame, rubyArgs);
        if (isSplatted) {
            rubyArgs = splatArgs(receiverObject, rubyArgs);
        }

        assert RubyArguments.getLastArgument(rubyArgs) == nil;
        RubyArguments.setLastArgument(rubyArgs, value);

        RubyArguments.setBlock(rubyArgs, executeBlock(frame));

        // no ruby2_keywords behavior for assign
        doCall(frame, receiverObject, descriptor, rubyArgs);
    }

    public Object doCall(VirtualFrame frame, Object receiverObject, ArgumentsDescriptor descriptor, Object[] rubyArgs) {
        // Remove empty kwargs in the caller, so the callee does not need to care about this special case
        if (descriptor instanceof KeywordArgumentsDescriptor && emptyKeywordArguments(rubyArgs)) {
            rubyArgs = removeEmptyKeywordArguments(rubyArgs);
            descriptor = NoKeywordArgumentsDescriptor.INSTANCE;
        }
        RubyArguments.setDescriptor(rubyArgs, descriptor);

        if (dispatch == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatch = insert(DispatchNode.create());
        }

        final Object returnValue = dispatch.execute(frame, receiverObject, methodName, rubyArgs, dispatchConfig);
        if (isAttrAssign) {
            final Object value = ArrayUtils.getLast(rubyArgs);
            assert RubyGuards.assertIsValidRubyValue(value);
            return value;
        } else {
            assert RubyGuards.assertIsValidRubyValue(returnValue);
            return returnValue;
        }
    }

    public Object executeWithArgumentsEvaluated(VirtualFrame frame, Object receiverObject, Object blockObject,
            Object[] argumentsObjects) {
        assert !isSplatted;
        Object[] rubyArgs = RubyArguments.allocate(argumentsObjects.length);
        RubyArguments.setSelf(rubyArgs, receiverObject);
        RubyArguments.setBlock(rubyArgs, blockObject);
        RubyArguments.setArguments(rubyArgs, argumentsObjects);
        return doCall(frame, receiverObject, descriptor, rubyArgs);
    }

    private Object executeBlock(VirtualFrame frame) {
        if (block != null) {
            return block.execute(frame);
        } else {
            return nil;
        }
    }

    @ExplodeLoop
    private void executeArguments(VirtualFrame frame, Object[] rubyArgs) {
        for (int i = 0; i < arguments.length; i++) {
            RubyArguments.setArgument(rubyArgs, i, arguments[i].execute(frame));
        }
    }

    @ExplodeLoop
    private void executeArgumentsToAssign(VirtualFrame frame, Object[] rubyArgs) {
        // BodyTranslator-specific logic: the last element could be DeadNode that is disallowed to be executed
        // TODO: use #executeArguments method instead after complete switching to YARP

        // execute all arguments but the last one
        for (int i = 0; i < arguments.length - 1; i++) {
            RubyArguments.setArgument(rubyArgs, i, arguments[i].execute(frame));
        }

        // execute the last argument
        final int lastIndex = arguments.length - 1;
        if (arguments[lastIndex] instanceof DeadNode) {
            RubyArguments.setArgument(rubyArgs, lastIndex, nil);
        } else {
            RubyArguments.setArgument(rubyArgs, lastIndex, arguments[lastIndex].execute(frame));
        }
    }

    private Object[] splatArgs(Object receiverObject, Object[] rubyArgs) {
        if (splatToArgs == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            splatToArgs = createSplatToArgsNode();
        }

        return splatToArgs.execute(receiverObject, (RubyArray) RubyArguments.getArgument(rubyArgs, 0));
    }

    private SplatToArgsNode createSplatToArgsNode() {
        return insert(new SplatToArgsNode(getLanguage()));
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        if (definedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            definedNode = insert(
                    RubyCallNodeFactory.DefinedNodeGen.create(methodName, receiver, arguments, dispatchConfig));
        }

        return definedNode.execute(frame, context);
    }

    public String getName() {
        return methodName;
    }

    public boolean isVCall() {
        return isVCall;
    }

    public boolean hasLiteralBlock() {
        RubyNode unwrappedBlock = RubyNode.unwrapNode(block);
        return unwrappedBlock instanceof BlockDefinitionNode || unwrappedBlock instanceof LambdaToProcNode;
    }

    public RubyNode[] getArguments() {
        return arguments;
    }

    private RubyNode getLastArgumentNode() {
        final RubyNode lastArg = RubyNode.unwrapNode(ArrayUtils.getLast(arguments));

        // BodyTranslator-specific condition
        if (isSplatted && lastArg instanceof ArrayAppendOneNode arrayAppendOneNode) {
            return RubyNode.unwrapNode(arrayAppendOneNode.getValueNode());
        }

        // YARP-specific condition
        // In case of splat argument (e.g. for code `a[*b], c = 1, 2`) a method (e.g. `#[]=`) has extra argument - value.
        // Arguments are supposed to have the following structure - ArrayConcatNode(... ArrayLiteralNode([placeholder])).
        // So return this placeholder node.
        if (isSplatted && lastArg instanceof ArrayConcatNode arrayConcatNode) {
            RubyNode[] elements = arrayConcatNode.getElements();
            assert elements.length > 0;

            RubyNode last = RubyNode.unwrapNode(ArrayUtils.getLast(elements));

            if (last instanceof ArrayLiteralNode arrayLiteralNode) {
                RubyNode[] values = arrayLiteralNode.getValues();
                assert values.length > 0;

                return RubyNode.unwrapNode(ArrayUtils.getLast(values));
            } else {
                return last;
            }
        }

        return lastArg;
    }

    @Override
    public AssignableNode toAssignableNode() {
        return this;
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    @Override
    public Map<String, Object> getDebugProperties() {
        final Map<String, Object> map = super.getDebugProperties();
        map.put("methodName", methodName);
        return map;
    }

    @Override
    public RubyNode cloneUninitialized() {
        RubyCallNodeParameters parameters = new RubyCallNodeParameters(
                receiver.cloneUninitialized(),
                methodName,
                cloneUninitialized(block),
                descriptor,
                cloneUninitialized(arguments),
                isSplatted,
                dispatchConfig == PRIVATE,
                isVCall,
                isSafeNavigation,
                isAttrAssign);
        var copy = getLanguage().coreMethodAssumptions.createCallNode(parameters);
        return copy.copyFlags(this);
    }

    abstract static class DefinedNode extends RubyBaseNode {

        private final RubySymbol methodNameSymbol;
        private final String methodName;
        private final DispatchConfiguration dispatchConfig;

        @Child private RubyNode receiver;
        @Children private final RubyNode[] arguments;
        @Child private DispatchNode respondToMissing = DispatchNode.create();

        public DefinedNode(
                String methodName,
                RubyNode receiver,
                RubyNode[] arguments,
                DispatchConfiguration dispatchConfig) {
            this.methodName = methodName;
            this.methodNameSymbol = getSymbol(methodName);
            this.receiver = receiver;
            this.arguments = arguments;
            this.dispatchConfig = dispatchConfig;

        }

        public abstract Object execute(VirtualFrame frame, RubyContext context);

        @Specialization
        @ExplodeLoop
        Object isDefined(VirtualFrame frame, RubyContext context,
                @Cached LookupMethodOnSelfNode lookupMethodNode,
                @Cached BooleanCastNode respondToMissingCast,
                @Cached InlinedConditionProfile receiverDefinedProfile,
                @Cached InlinedBranchProfile allArgumentsDefinedProfile,
                @Cached InlinedBranchProfile receiverExceptionProfile,
                @Cached InlinedConditionProfile methodNotFoundProfile,
                @Cached InlinedBranchProfile argumentNotDefinedProfile) {
            if (receiverDefinedProfile.profile(this, receiver.isDefined(frame, getLanguage(), context) == nil)) {
                return nil;
            }

            for (RubyNode argument : arguments) {
                if (argument.isDefined(frame, getLanguage(), context) == nil) {
                    argumentNotDefinedProfile.enter(this);
                    return nil;
                }
            }

            allArgumentsDefinedProfile.enter(this);

            final Object receiverObject;
            try {
                receiverObject = receiver.execute(frame);
            } catch (RaiseException e) {
                receiverExceptionProfile.enter(this);
                return nil;
            }

            final InternalMethod method = lookupMethodNode.execute(frame, receiverObject, methodName, dispatchConfig);

            if (methodNotFoundProfile.profile(this, method == null)) {
                final Object r = respondToMissing.call(PRIVATE_RETURN_MISSING, receiverObject, "respond_to_missing?",
                        methodNameSymbol, false);

                if (r != DispatchNode.MISSING && !respondToMissingCast.execute(this, r)) {
                    return nil;
                }
            }

            return FrozenStrings.METHOD;
        }
    }
}
