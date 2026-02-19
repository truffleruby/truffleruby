/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.method;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.ProcCallTargets;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.ArgumentDescriptorUtils;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.CallInternalMethodNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.parser.ArgumentDescriptor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreModule(value = "Method", isClass = true)
public abstract class MethodNodes {

    public static boolean areInternalMethodEqual(InternalMethod a, InternalMethod b) {
        if (a == b || a.getSharedMethodInfo() == b.getSharedMethodInfo()) {
            return true;
        }

        // For builtin aliases to be == such as String#size and String#length, even though they have
        // different CallTarget, InternalMethod and SharedMethodInfo.
        return a.getSharedMethodInfo().getArity() == b.getSharedMethodInfo().getArity();
    }

    public static int hashInternalMethod(InternalMethod internalMethod) {
        // Hash the Arity object to guarantee same hash values for areInternalMethodEqual() methods.
        return internalMethod.getSharedMethodInfo().getArity().hashCode();
    }

    @Primitive(name = "same_methods?")
    public abstract static class SameMethodsNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        boolean same(RubyMethod self, RubyMethod other) {
            return MethodNodes.areInternalMethodEqual(self.method, other.method);
        }

        @Specialization
        boolean same(RubyMethod self, RubyUnboundMethod other) {
            return MethodNodes.areInternalMethodEqual(self.method, other.method);
        }

        @Specialization
        boolean same(RubyUnboundMethod self, RubyMethod other) {
            return MethodNodes.areInternalMethodEqual(self.method, other.method);
        }

        @Specialization
        boolean same(RubyUnboundMethod self, RubyUnboundMethod other) {
            return MethodNodes.areInternalMethodEqual(self.method, other.method);
        }
    }

    @CoreMethod(names = { "==", "eql?" }, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean equal(RubyMethod a, RubyMethod b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            return referenceEqualNode
                    .execute(this, a.receiver, b.receiver) &&
                    a.method.getDeclaringModule() == b.method.getDeclaringModule() &&
                    MethodNodes.areInternalMethodEqual(a.method, b.method);
        }

        @Specialization(guards = "!isRubyMethod(b)")
        boolean equal(RubyMethod a, Object b) {
            return false;
        }

    }

    @CoreMethod(names = "arity")
    public abstract static class ArityNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int arity(RubyMethod method) {
            return method.method.getArityNumber();
        }

    }

    @GenerateUncached
    @CoreMethod(names = { "call", "[]", "===" }, needsBlock = true, rest = true, alwaysInlined = true)
    public abstract static class CallNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object call(Frame callerFrame, RubyMethod method, Object[] rubyArgs, RootCallTarget target,
                @Cached CallInternalMethodNode callInternalMethodNode) {
            final InternalMethod internalMethod = method.method;
            final Object receiver = method.receiver;
            return callBoundMethod(callerFrame, internalMethod, receiver, rubyArgs, callInternalMethodNode);
        }

        static Object callBoundMethod(Frame frame, InternalMethod internalMethod, Object receiver,
                Object[] callerRubyArgs, CallInternalMethodNode callInternalMethodNode) {
            final Object[] newArgs = RubyArguments.repack(callerRubyArgs, receiver);
            RubyArguments.setMethod(newArgs, internalMethod);
            assert RubyArguments.assertFrameArguments(newArgs);
            return callInternalMethodNode.execute(frame, internalMethod, receiver, newArgs);
        }
    }

    @CoreMethod(names = "name")
    public abstract static class NameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol name(RubyMethod method) {
            return getSymbol(method.method.getName());
        }

    }

    @CoreMethod(names = "original_name")
    public abstract static class OriginalNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol originalName(RubyMethod method,
                @Cached ToSymbolNode toSymbolNode) {
            String originalName = method.method.getOriginalName();
            return toSymbolNode.execute(this, originalName);
        }
    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long hash(RubyMethod rubyMethod) {
            final InternalMethod method = rubyMethod.method;
            long h = getContext().getHashing(this).start(method.getDeclaringModule().hashCode());
            h = Hashing.update(h, rubyMethod.receiver.hashCode());
            h = Hashing.update(h, hashInternalMethod(method));
            return Hashing.end(h);
        }

    }

    @CoreMethod(names = "owner")
    public abstract static class OwnerNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule owner(RubyMethod method) {
            return method.method.getOwner();
        }

    }

    @CoreMethod(names = "parameters")
    public abstract static class ParametersNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray parameters(RubyMethod method) {
            final ArgumentDescriptor[] argsDesc = method.method
                    .getSharedMethodInfo()
                    .getArgumentDescriptors();

            return ArgumentDescriptorUtils.argumentDescriptorsToParameters(getLanguage(), getContext(), argsDesc, true);
        }

    }

    @CoreMethod(names = "private?")
    public abstract static class IsPrivateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isPrivate(RubyMethod method) {
            return method.method.isPrivate();
        }
    }

    @CoreMethod(names = "protected?")
    public abstract static class IsProtectedNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isProtected(RubyMethod method) {
            return method.method.isProtected();
        }
    }

    @CoreMethod(names = "public?")
    public abstract static class IsPublicNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean isPublic(RubyMethod method) {
            return method.method.isPublic();
        }
    }

    @CoreMethod(names = "receiver")
    public abstract static class ReceiverNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object receiver(RubyMethod method) {
            return method.receiver;
        }
    }

    @CoreMethod(names = "source_location")
    public abstract static class SourceLocationNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object sourceLocation(RubyMethod method,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var sourceSection = method.method.getSharedMethodInfo().getSourceSection();
            return getLanguage().rubySourceLocation(sourceSection, fromJavaStringNode, this);
        }
    }

    @CoreMethod(names = "super_method")
    public abstract static class SuperMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object superMethod(RubyMethod method,
                @Cached MetaClassNode metaClassNode) {
            Object receiver = method.receiver;
            InternalMethod internalMethod = method.method;
            RubyClass selfMetaClass = metaClassNode.execute(this, receiver);
            MethodLookupResult superMethod = ModuleOperations.lookupSuperMethod(internalMethod, selfMetaClass);
            if (!superMethod.isDefined()) {
                return nil;
            } else {
                final RubyMethod instance = new RubyMethod(
                        coreLibrary().methodClass,
                        getLanguage().methodShape,
                        receiver,
                        superMethod.getMethod());
                AllocationTracing.trace(instance, this);
                return instance;
            }
        }

    }

    @CoreMethod(names = "unbind")
    public abstract static class UnbindNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyUnboundMethod unbind(RubyMethod method,
                @Cached MetaClassNode metaClassNode) {
            final RubyClass receiverClass = metaClassNode.execute(this, method.receiver);
            final RubyUnboundMethod instance = new RubyUnboundMethod(
                    coreLibrary().unboundMethodClass,
                    getLanguage().unboundMethodShape,
                    receiverClass,
                    method.method);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "to_proc")
    public abstract static class ToProcNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc toProc(RubyMethod methodObject) {
            final InternalMethod method = methodObject.method;
            final Object receiver = methodObject.receiver;
            RootCallTarget toProcCallTarget = method.toProcCallTarget;

            if (toProcCallTarget == null) {
                toProcCallTarget = createToProcCallTarget(method.getCallTarget());
                method.toProcCallTarget = toProcCallTarget;
            }

            return createProc(toProcCallTarget, method, receiver);
        }

        private RubyProc createProc(RootCallTarget callTarget, InternalMethod method, Object receiver) {
            final Object[] packedArgs = RubyArguments
                    .pack(null, null, method, null, receiver, nil, NoKeywordArgumentsDescriptor.INSTANCE,
                            EMPTY_ARGUMENTS);
            final var variables = new SpecialVariableStorage();
            final MaterializedFrame declarationFrame = getLanguage().createEmptyDeclarationFrame(packedArgs, variables);
            return ProcOperations.createRubyProc(
                    coreLibrary().procClass,
                    getLanguage().procShape,
                    ProcType.LAMBDA,
                    method.getSharedMethodInfo(),
                    new ProcCallTargets(callTarget),
                    declarationFrame,
                    variables,
                    method,
                    null,
                    method.getDeclarationContext());
        }

        @TruffleBoundary
        protected RootCallTarget createToProcCallTarget(RootCallTarget callTarget) {
            // translate to something like:
            // lambda { |same args list| method.call(args) }
            // We need to preserve the method receiver and we want to have the same argument list.
            // We create a new CallTarget for the Proc that calls the method CallTarget and passes the correct receiver.

            final RubyRootNode methodRootNode = RubyRootNode.of(callTarget);
            final SharedMethodInfo sharedMethodInfo = methodRootNode.getSharedMethodInfo();

            var callWithRubyMethodReceiverNode = new CallWithRubyMethodReceiverNode();
            final RubyLambdaRootNode wrapRootNode = new RubyLambdaRootNode(
                    getLanguage(),
                    sharedMethodInfo.getSourceSection(),
                    methodRootNode.getFrameDescriptor(),
                    sharedMethodInfo,
                    callWithRubyMethodReceiverNode,
                    methodRootNode.getSplit(),
                    methodRootNode.returnID,
                    BreakID.INVALID,
                    sharedMethodInfo.getArity());
            return wrapRootNode.getCallTarget();
        }

        private static final class CallWithRubyMethodReceiverNode extends RubyContextSourceNode {
            @Child private CallInternalMethodNode callInternalMethodNode = CallInternalMethodNode.create();

            @Override
            public Object execute(VirtualFrame frame) {
                final MaterializedFrame declarationFrame = RubyArguments.getDeclarationFrame(frame);
                final Object receiver = RubyArguments.getSelf(declarationFrame);
                final InternalMethod method = RubyArguments.getMethod(declarationFrame);
                return CallNode.callBoundMethod(frame, method, receiver, frame.getArguments(), callInternalMethodNode);
            }

            @Override
            public RubyNode cloneUninitialized() {
                var copy = new CallWithRubyMethodReceiverNode();
                return copy.copyFlags(this);
            }

        }
    }

    @Primitive(name = "method_unimplement")
    public abstract static class MethodUnimplementNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object bound(RubyMethod rubyMethod) {
            unimplement(rubyMethod.method);
            return nil;
        }

        @Specialization
        Object unbound(RubyUnboundMethod rubyMethod) {
            unimplement(rubyMethod.method);
            return nil;
        }

        @TruffleBoundary
        private void unimplement(InternalMethod method) {
            method.getDeclaringModule().fields.addMethod(getContext(), this, method.unimplemented());
        }
    }

    @Primitive(name = "method_unimplemented?")
    public abstract static class MethodIsUnimplementedNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean bound(RubyMethod rubyMethod) {
            return rubyMethod.method.isUnimplemented();
        }

        @Specialization
        boolean unbound(RubyUnboundMethod rubyMethod) {
            return rubyMethod.method.isUnimplemented();
        }

    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }

    }

}
