/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.supercall;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.language.SpecialVariablesSendingNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

public final class CallSuperMethodNode extends SpecialVariablesSendingNode {

    private final ConditionProfile missingProfile = ConditionProfile.create();

    @Child private CallInternalMethodNode callMethodNode;
    @Child private DispatchNode callMethodMissingNode;
    @Child private GetSpecialVariableStorage readingNode;

    @CompilationFinal private Assumption specialVariableAssumption;

    public static CallSuperMethodNode create() {
        return new CallSuperMethodNode();
    }

    private CallSuperMethodNode() {
    }

    public Object execute(
            VirtualFrame frame,
            Object self,
            InternalMethod superMethod,
            ArgumentsDescriptor descriptor,
            Object[] arguments,
            Object block) {

        if (missingProfile.profile(superMethod == null)) {
            final String name = RubyArguments.getMethod(frame).getSharedMethodInfo().getMethodName(); // use the original name
            final Object[] methodMissingArguments = ArrayUtils.unshift(arguments, getSymbol(name));
            return callMethodMissing(self, block, descriptor, methodMissingArguments);
        }

        SpecialVariableStorage callerSpecialVariables = null;
        if (!getSpecialVariableStorageAssumption(frame).isValid()) {
            callerSpecialVariables = getReadingNode().executeCached(frame);
        }

        final Object[] rubyArgs = RubyArguments.pack(
                null, callerSpecialVariables, superMethod, null, self, block, descriptor, arguments);

        return getCallMethodNode().execute(frame, superMethod, self, rubyArgs);
    }

    private CallInternalMethodNode getCallMethodNode() {
        if (callMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callMethodNode = insert(CallInternalMethodNode.create());
        }
        return callMethodNode;
    }

    private GetSpecialVariableStorage getReadingNode() {
        if (readingNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readingNode = insert(GetSpecialVariableStorage.create());
        }
        return readingNode;
    }

    private Assumption getSpecialVariableStorageAssumption(Frame frame) {
        if (specialVariableAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            specialVariableAssumption = getSpecialVariableAssumption(frame);
        }
        return specialVariableAssumption;
    }


    private Object callMethodMissing(Object receiver, Object block, ArgumentsDescriptor descriptor,
            Object[] arguments) {
        if (callMethodMissingNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callMethodMissingNode = insert(DispatchNode.create());
        }
        return callMethodMissingNode.callWithDescriptor(receiver, "method_missing", block, descriptor, arguments);
    }
}
