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
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.interop.ForeignToRubyNode;
import org.truffleruby.interop.InteropNodes;
import org.truffleruby.interop.InteropNodes.InvokeMemberNode;
import org.truffleruby.interop.InteropNodes.WriteMemberWithoutConversionNode;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

/** foreign.some_method(*args), when #some_method is not defined in Polyglot::Foreign* */
@GenerateUncached
public abstract class CallForeignMethodNode extends RubyBaseNode {

    @NeverDefault
    public static CallForeignMethodNode create() {
        return CallForeignMethodNodeGen.create();
    }

    public static CallForeignMethodNode getUncached() {
        return CallForeignMethodNodeGen.getUncached();
    }

    public abstract Object execute(Object receiver, String methodName, Object block, Object[] arguments);

    @Specialization
    Object call(Object receiver, String methodName, Object block, Object[] arguments,
            @Cached ForeignInvokeNode foreignInvokeNode,
            @Cached TranslateExceptionNode translateException,
            @Cached InlinedConditionProfile hasBlock,
            @Cached InlinedBranchProfile errorProfile) {
        assert block instanceof Nil || block instanceof RubyProc : block;

        Object[] newArguments = arguments;
        if (hasBlock.profile(this, block != nil)) {
            newArguments = ArrayUtils.append(arguments, block);
        }

        try {
            return foreignInvokeNode.execute(this, receiver, methodName, newArguments);
        } catch (Throwable t) {
            errorProfile.enter(this);
            throw translateException.execute(this, t);
        }
    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    @ReportPolymorphism // inline cache
    public abstract static class ForeignInvokeNode extends RubyBaseNode {

        public abstract Object execute(Node node, Object receiver, String name, Object[] args);

        @Specialization(
                guards = { "name == cachedName", "!isOperatorMethod(cachedName)", "!isAssignmentMethod(cachedName)" },
                limit = "1")
        static Object invokeOrRead(Node node, Object receiver, String name, Object[] args,
                @Cached("name") String cachedName,
                @Cached InvokeOrReadMemberNode invokeOrReadMemberNode) {
            return invokeOrReadMemberNode.execute(node, receiver, name, args);
        }

        @Specialization(guards = { "name == cachedName", "isOperatorMethod(cachedName)" }, limit = "1")
        static Object operatorMethod(Node node, Object receiver, String name, Object[] args,
                @Cached("name") String cachedName,
                @Cached ConvertForOperatorAndReDispatchNode operatorNode) {
            return operatorNode.execute(node, receiver, name, args);
        }

        @Specialization(guards = { "name == cachedName", "isAssignmentMethod(cachedName)" }, limit = "1")
        static Object assignmentMethod(Node node, Object receiver, String name, Object[] args,
                @Cached("name") String cachedName,
                @Cached(value = "getPropertyFromName(cachedName)", allowUncached = true) String propertyName,
                @Cached WriteMemberWithoutConversionNode writeMemberNode,
                @Cached InlinedBranchProfile errorProfile) {
            if (args.length == 1) {
                return writeMemberNode.execute(node, receiver, propertyName, args[0]);
            } else {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError(args.length, 1, node));
            }
        }

        @Idempotent
        @TruffleBoundary
        protected static boolean isOperatorMethod(String name) {
            return !name.isEmpty() && !Character.isLetter(name.charAt(0));
        }

        @Idempotent
        @TruffleBoundary
        protected static boolean isAssignmentMethod(String name) {
            return !name.isEmpty() && Character.isLetter(name.charAt(0)) && name.charAt(name.length() - 1) == '=';
        }

        @TruffleBoundary
        protected static String getPropertyFromName(String name) {
            return name.substring(0, name.length() - 1);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InvokeOrReadMemberNode extends RubyBaseNode {

        public abstract Object execute(Node node, Object receiver, String identifier, Object[] args);

        @Specialization(limit = "getInteropCacheLimit()")
        static Object readOrInvoke(Node node, Object receiver, String name, Object[] args,
                @Cached InlinedConditionProfile hasArguments,
                @Cached InlinedConditionProfile invocable,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached ForeignToRubyNode foreignToRubyNode,
                @Cached TranslateInteropExceptionNode translateInteropException) {
            final Object foreign;
            if (hasArguments.profile(node, args.length != 0) ||
                    invocable.profile(node, receivers.isMemberInvocable(receiver, name))) {
                foreign = InteropNodes.invokeMember(node, receivers, receiver, name, args, translateInteropException);
            } else {
                foreign = InteropNodes.readMember(node, receivers, receiver, name, translateInteropException);
            }
            return foreignToRubyNode.execute(node, foreign);
        }
    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    protected abstract static class ConvertForOperatorAndReDispatchNode extends RubyBaseNode {

        protected abstract Object execute(Node node, Object receiver, String name, Object[] args);

        @Specialization(guards = "receivers.isBoolean(receiver)", limit = "getInteropCacheLimit()")
        static Object callBoolean(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached @Shared TranslateInteropExceptionNode translateInteropException,
                @Cached(inline = false) @Shared DispatchNode dispatch) {
            try {
                return dispatch.call(receivers.asBoolean(receiver), name, args);
            } catch (InteropException e) {
                throw translateInteropException.execute(node, e);
            }
        }

        @Specialization(
                guards = { "receivers.isNumber(receiver)", "receivers.fitsInInt(receiver)" },
                limit = "getInteropCacheLimit()")
        static Object callInt(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached @Shared TranslateInteropExceptionNode translateInteropException,
                @Cached(inline = false) @Shared DispatchNode dispatch) {
            try {
                return dispatch.call(receivers.asInt(receiver), name, args);
            } catch (InteropException e) {
                throw translateInteropException.execute(node, e);
            }
        }

        @Specialization(
                guards = {
                        "receivers.isNumber(receiver)",
                        "!receivers.fitsInInt(receiver)",
                        "receivers.fitsInLong(receiver)" },
                limit = "getInteropCacheLimit()")
        static Object callLong(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached @Shared TranslateInteropExceptionNode translateInteropException,
                @Cached(inline = false) @Shared DispatchNode dispatch) {
            try {
                return dispatch.call(receivers.asLong(receiver), name, args);
            } catch (InteropException e) {
                throw translateInteropException.execute(node, e);
            }
        }

        @Specialization(
                guards = {
                        "receivers.isNumber(receiver)",
                        "!receivers.fitsInLong(receiver)",
                        "receivers.fitsInBigInteger(receiver)" },
                limit = "getInteropCacheLimit()")
        static Object callBigInteger(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached @Shared TranslateInteropExceptionNode translateInteropException,
                @Cached(inline = false) @Shared DispatchNode dispatch) {
            try {
                return dispatch.call(new RubyBignum(receivers.asBigInteger(receiver)), name, args);
            } catch (InteropException e) {
                throw translateInteropException.execute(node, e);
            }
        }

        @Specialization(
                guards = {
                        "receivers.isNumber(receiver)",
                        "!receivers.fitsInBigInteger(receiver)",
                        "receivers.fitsInDouble(receiver)" },
                limit = "getInteropCacheLimit()")
        static Object callDouble(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached @Shared TranslateInteropExceptionNode translateInteropException,
                @Cached(inline = false) @Shared DispatchNode dispatch) {
            try {
                return dispatch.call(receivers.asDouble(receiver), name, args);
            } catch (InteropException e) {
                throw translateInteropException.execute(node, e);
            }
        }

        @Specialization(guards = { "!receivers.isBoolean(receiver)", "!receivers.isNumber(receiver)" },
                limit = "getInteropCacheLimit()")
        static Object call(Node node, Object receiver, String name, Object[] args,
                @CachedLibrary("receiver") InteropLibrary receivers,
                @Cached InvokeMemberNode invokeNode) {
            return invokeNode.execute(node, receiver, name, args);
        }
    }
}
