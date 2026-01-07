/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.basicobject;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import org.truffleruby.Layouts;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.ExceptionOperations.ExceptionFormatter;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.objectspace.ObjectSpaceManager;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.loader.EvalLoader;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.DeclarationContext.SingletonClassOfSelfDefaultDefinee;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.CanHaveSingletonClassNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.ObjectIDOperations;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.supercall.SuperCallNode;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;

@CoreModule(value = "BasicObject", isClass = true)
public abstract class BasicObjectNodes {

    @CoreMethod(names = "!")
    public abstract static class NotNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean not(Object value,
                @Cached BooleanCastNode cast) {
            return !cast.execute(this, value);
        }

    }

    // Needed to split since it calls `==`. But seems a bit expensive to split, so it should be AlwaysInlinedMethodNode.
    @GenerateUncached
    @CoreMethod(names = "!=", required = 1, alwaysInlined = true)
    public abstract static class NotEqualNode extends AlwaysInlinedMethodNode {

        @Specialization
        boolean equal(Frame frame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached BooleanCastNode booleanCastNode,
                @Cached DispatchNode equalNode) {
            final Object object = RubyArguments.getArgument(rubyArgs, 0);
            return !booleanCastNode.execute(this, equalNode.call(self, "==", object));
        }

    }

    /** This node is not trivial because primitives must be compared by value and never by identity. Also, this node
     * must consider (byte) n and (short) n and (int) n and (long) n equal, as well as (float) n and (double) n. So even
     * if a and b have different classes they might still be equal if they are primitives. */
    @GenerateNodeFactory
    @CoreMethod(names = { "equal?", "==" }, required = 1)
    public abstract static class BasicObjectEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean equal(Object a, Object b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            return referenceEqualNode.execute(this, a, b);
        }
    }

    @CoreMethod(names = "__id__")
    @NodeChild(value = "valueNode", type = RubyNode.class)
    public abstract static class BasicObjectObjectIDNode extends CoreMethodNode {

        @Specialization
        Object objectIDNode(Object value,
                @Cached ObjectIDNode objectIDNode) {
            return objectIDNode.execute(value);
        }
    }

    @GenerateUncached
    public abstract static class ObjectIDNode extends RubyBaseNode {

        public static ObjectIDNode getUncached() {
            return BasicObjectNodesFactory.ObjectIDNodeGen.getUncached();
        }

        public abstract Object execute(Object value);

        public abstract long execute(RubyDynamicObject value);

        @Specialization
        long objectIDNil(Nil nil) {
            return ObjectIDOperations.NIL;
        }

        @Specialization(guards = "value")
        long objectIDTrue(boolean value) {
            return ObjectIDOperations.TRUE;
        }

        @Specialization(guards = "!value")
        long objectIDFalse(boolean value) {
            return ObjectIDOperations.FALSE;
        }

        @Specialization
        long objectID(int value) {
            return ObjectIDOperations.smallFixnumToID(value);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long objectIDSmallFixnumOverflow(long value) throws ArithmeticException {
            return ObjectIDOperations.smallFixnumToIDOverflow(value);
        }

        @Specialization(replaces = "objectIDSmallFixnumOverflow")
        static Object objectIDLong(long value,
                @Cached InlinedCountingConditionProfile smallProfile,
                @Bind Node node) {
            if (smallProfile.profile(node, ObjectIDOperations.isSmallFixnum(value))) {
                return ObjectIDOperations.smallFixnumToID(value);
            } else {
                return ObjectIDOperations.largeFixnumToID(value);
            }
        }

        @Specialization
        RubyBignum objectID(double value) {
            return ObjectIDOperations.floatToID(value);
        }

        @Specialization(guards = "!isNil(object)")
        long objectIDImmutable(ImmutableRubyObject object) {
            final long id = object.getObjectId();

            if (id == 0) {
                final long newId = getLanguage().getNextObjectID();
                object.setObjectId(newId);
                return newId;
            }

            return id;
        }

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        long objectID(RubyDynamicObject object,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
            // Using the context here has the desirable effect that it checks the context is entered on this thread,
            // which is necessary to safely mutate DynamicObjects.
            final long id = ObjectSpaceManager.readObjectID(object, objectLibrary);

            if (id == 0L) {
                if (objectLibrary.isShared(object)) {
                    synchronized (object) {
                        final long existingID = ObjectSpaceManager.readObjectID(object, objectLibrary);
                        if (existingID != 0L) {
                            return existingID;
                        } else {
                            final long newId = getContext().getObjectSpaceManager().getNextObjectID();
                            objectLibrary.putLong(object, Layouts.OBJECT_ID_IDENTIFIER, newId);
                            return newId;
                        }
                    }
                } else {
                    final long newId = getContext().getObjectSpaceManager().getNextObjectID();
                    objectLibrary.putLong(object, Layouts.OBJECT_ID_IDENTIFIER, newId);
                    return newId;
                }
            }

            return id;
        }

        @Specialization(guards = "isForeignObject(value)", limit = "getInteropCacheLimit()")
        static int objectIDForeign(Object value,
                @CachedLibrary("value") InteropLibrary interop,
                @Cached TranslateInteropExceptionNode translateInteropException,
                @Bind Node node) {
            if (interop.hasIdentity(value)) {
                try {
                    return interop.identityHashCode(value);
                } catch (UnsupportedMessageException e) {
                    throw translateInteropException.execute(node, e);
                }
            } else {
                return System.identityHashCode(value);
            }
        }
    }

    @GenerateUncached
    @CoreMethod(names = "initialize", alwaysInlined = true)
    public abstract static class InitializeNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object initialize(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target) {
            return nil;
        }
    }

    @GenerateUncached
    @CoreMethod(names = "instance_eval", needsBlock = true, optional = 3, alwaysInlined = true)
    public abstract static class InstanceEvalNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "isBlockProvided(rubyArgs)")
        Object evalWithBlock(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached InstanceExecBlockNode instanceExecNode,
                @Cached @Exclusive InlinedBranchProfile wrongNumberOfArgumentsProfile) {
            final int count = RubyArguments.getPositionalArgumentsCount(rubyArgs);

            if (count > 0) {
                wrongNumberOfArgumentsProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().argumentError(count, 0, this));
            }

            final Object block = RubyArguments.getBlock(rubyArgs);
            return instanceExecNode.execute(this, NoKeywordArgumentsDescriptor.INSTANCE, self, new Object[]{ self },
                    (RubyProc) block);
        }

        @Specialization(guards = "!isBlockProvided(rubyArgs)")
        static Object evalWithString(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached @Exclusive InlinedBranchProfile zeroNumberOfArguments,
                @Cached RubyStringLibrary strings,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached ToStrNode toStrNode,
                @Cached ToIntNode toIntNode,
                @Cached IndirectCallNode callNode,
                @Bind Node node) {
            final Object sourceCode;
            String fileName = coreStrings(node).EVAL_FILENAME_STRING.toString();
            int line = 1;
            int count = RubyArguments.getPositionalArgumentsCount(rubyArgs);

            if (count == 0) {
                zeroNumberOfArguments.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError(0, 1, 2, node));
            }

            sourceCode = toStrNode.execute(node, RubyArguments.getArgument(rubyArgs, 0));

            if (count >= 2) {
                fileName = toJavaStringNode
                        .execute(node, toStrNode.execute(node, RubyArguments.getArgument(rubyArgs, 1)));
            }

            if (count >= 3) {
                line = toIntNode.execute(RubyArguments.getArgument(rubyArgs, 2));
            }

            needCallerFrame(node, callerFrame, target);
            return instanceEvalHelper(
                    node,
                    callerFrame.materialize(),
                    self,
                    strings.getTString(node, sourceCode),
                    strings.getEncoding(node, sourceCode),
                    fileName,
                    line,
                    callNode);
        }

        @TruffleBoundary
        private static Object instanceEvalHelper(Node node, MaterializedFrame callerFrame, Object receiver,
                AbstractTruffleString code,
                RubyEncoding encoding,
                String fileNameString, int line, IndirectCallNode callNode) {
            final RubySource source = EvalLoader
                    .createEvalSource(getContext(node), code, encoding, "instance_eval", fileNameString, line, node);
            final LexicalScope callerLexicalScope = RubyArguments.getMethod(callerFrame).getLexicalScope();

            LexicalScope lexicalScope = prependReceiverClassToScope(callerLexicalScope, receiver);

            final RootCallTarget callTarget = getContext(node).getCodeLoader().parse(
                    source,
                    ParserContext.INSTANCE_EVAL,
                    callerFrame.getFrameDescriptor(),
                    lexicalScope,
                    node);

            final DeclarationContext declarationContext = new DeclarationContext(
                    Visibility.PUBLIC,
                    new SingletonClassOfSelfDefaultDefinee(receiver),
                    DeclarationContext.NO_REFINEMENTS);

            final CodeLoader.DeferredCall deferredCall = getContext(node).getCodeLoader().prepareExecute(
                    callTarget,
                    ParserContext.INSTANCE_EVAL,
                    declarationContext,
                    callerFrame,
                    receiver,
                    lexicalScope);

            return deferredCall.call(callNode);
        }

        private static LexicalScope prependReceiverClassToScope(LexicalScope callerLexicalScope, Object receiver) {
            final RubyClass logicalClass = LogicalClassNode.getUncached().execute(receiver);
            // For BasicObject#instance_eval, the new scopes SHOULD affect constant lookup but SHOULD NOT affect class variables lookup
            LexicalScope lexicalScope = new LexicalScope(callerLexicalScope, logicalClass, true);

            if (CanHaveSingletonClassNode.executeUncached(receiver)) {
                final RubyClass singletonClass = SingletonClassNode.getUncached().execute(receiver);

                // For true/false/nil Ruby objects #singleton_class (and SingletonClassNode as well) returns
                // a logical class (e.g. TrueClass etc). Ignore duplicate in this case.
                if (singletonClass != logicalClass) {
                    lexicalScope = new LexicalScope(lexicalScope, singletonClass, true);
                }
            }

            return lexicalScope;
        }

    }

    @CoreMethod(names = "instance_exec", needsBlock = true, rest = true)
    public abstract static class InstanceExecNode extends CoreMethodArrayArgumentsNode {

        @Child private CallBlockNode callBlockNode = CallBlockNode.create();

        @Specialization
        Object instanceExec(VirtualFrame frame, Object receiver, Object[] arguments, RubyProc block) {
            final DeclarationContext declarationContext = new DeclarationContext(
                    Visibility.PUBLIC,
                    new SingletonClassOfSelfDefaultDefinee(receiver),
                    block.declarationContext.getRefinements());
            var descriptor = RubyArguments.getDescriptor(frame);
            return callBlockNode.executeCallBlock(this, declarationContext, block, receiver, nil, descriptor,
                    arguments);
        }

        @Specialization
        Object instanceExec(Object receiver, Object[] arguments, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().localJumpError("no block given", this));
        }

    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    public abstract static class InstanceExecBlockNode extends RubyBaseNode {

        public abstract Object execute(Node node, ArgumentsDescriptor descriptor, Object self, Object[] args,
                RubyProc block);

        @Specialization
        static Object instanceExec(
                Node node, ArgumentsDescriptor descriptor, Object self, Object[] arguments, RubyProc block,
                @Cached CallBlockNode callBlockNode) {
            final DeclarationContext declarationContext = new DeclarationContext(
                    Visibility.PUBLIC,
                    new SingletonClassOfSelfDefaultDefinee(self),
                    block.declarationContext.getRefinements());

            return callBlockNode.executeCallBlock(node, declarationContext, block, self, nil, descriptor, arguments);
        }

    }

    @CoreMethod(names = "method_missing", needsBlock = true, rest = true, optional = 1, visibility = Visibility.PRIVATE,
            split = Split.NEVER)
    public abstract static class MethodMissingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object methodMissingNoName(Object self, NotProvided name, Object[] args, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("no id given", this));
        }

        @Specialization(guards = "wasProvided(name)")
        Object methodMissing(Object self, Object name, Object[] args, Object block) {
            throw new RaiseException(getContext(), buildMethodMissingException(self, name, args, block));
        }

        private static final class FrameAndCallNode {
            final Frame frame;
            final Node callNode;

            private FrameAndCallNode(Frame frame, Node callNode) {
                this.frame = frame;
                this.callNode = callNode;
            }
        }

        @TruffleBoundary
        private RubyException buildMethodMissingException(Object self, Object nameObject, Object[] args, Object block) {
            final String name;
            if (nameObject instanceof RubySymbol) {
                name = ((RubySymbol) nameObject).getString();
            } else {
                name = nameObject.toString();
            }
            final FrameAndCallNode relevantCallerFrame = getRelevantCallerFrame();
            Visibility visibility;

            if (lastCallWasSuper(relevantCallerFrame)) {
                return coreExceptions()
                        .noMethodErrorFromMethodMissing(ExceptionFormatter.SUPER_METHOD_ERROR, self, name, args, this);
            } else if ((visibility = lastCallWasCallingPrivateOrProtectedMethod(
                    self,
                    name,
                    relevantCallerFrame)) != null) {
                if (visibility == Visibility.PRIVATE) {
                    return coreExceptions().noMethodErrorFromMethodMissing(
                            ExceptionFormatter.PRIVATE_METHOD_ERROR,
                            self,
                            name,
                            args,
                            this);
                } else {
                    return coreExceptions().noMethodErrorFromMethodMissing(
                            ExceptionFormatter.PROTECTED_METHOD_ERROR,
                            self,
                            name,
                            args,
                            this);
                }
            } else if (lastCallWasVCall(relevantCallerFrame)) {
                return coreExceptions().nameErrorFromMethodMissing(
                        ExceptionFormatter.NO_LOCAL_VARIABLE_OR_METHOD_ERROR,
                        self,
                        name,
                        this);
            } else {
                return coreExceptions()
                        .noMethodErrorFromMethodMissing(ExceptionFormatter.NO_METHOD_ERROR, self, name, args, this);
            }
        }

        private FrameAndCallNode getRelevantCallerFrame() {
            return Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Node callNode = frameInstance.getCallNode();
                if (callNode == null) {
                    // skip current frame
                    return null;
                }

                final SuperCallNode superCallNode = NodeUtil.findParent(callNode, SuperCallNode.class);
                final Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);

                final InternalMethod method = RubyArguments.tryGetMethod(frame);

                final String superMethodName;

                if (method == null) {
                    superMethodName = "(unknown)";
                } else {
                    superMethodName = method.getName();
                }

                if (superCallNode != null && superMethodName.equals("method_missing")) {
                    // skip super calls of method_missing itself
                    return null;
                }

                return new FrameAndCallNode(frame, callNode);
            });
        }

        private boolean lastCallWasSuper(FrameAndCallNode callerFrame) {
            final SuperCallNode superCallNode = NodeUtil.findParent(callerFrame.callNode, SuperCallNode.class);
            return superCallNode != null;
        }

        /** See {@link LookupMethodNode}. The only way to fail if method is not null and not undefined is visibility. */
        private Visibility lastCallWasCallingPrivateOrProtectedMethod(Object self, String name,
                FrameAndCallNode callerFrame) {
            final DeclarationContext declarationContext = RubyArguments.tryGetDeclarationContext(callerFrame.frame);
            final InternalMethod method = ModuleOperations
                    .lookupMethodUncached(MetaClassNode.executeUncached(self), name, declarationContext);
            if (method != null && !method.isUndefined()) {
                assert method.getVisibility() == Visibility.PRIVATE || method.getVisibility() == Visibility.PROTECTED;
                return method.getVisibility();
            }
            return null;
        }

        private boolean lastCallWasVCall(FrameAndCallNode callerFrame) {
            final RubyCallNode callNode = NodeUtil.findParent(callerFrame.callNode, RubyCallNode.class);
            return callNode != null && callNode.isVCall();
        }

    }

    @GenerateUncached
    @CoreMethod(names = "__send__", needsBlock = true, rest = true, required = 1, alwaysInlined = true)
    public abstract static class SendNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object send(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached DispatchNode dispatchNode,
                @Cached NameToJavaStringNode nameToJavaString) {
            Object name = RubyArguments.getArgument(rubyArgs, 0);
            return dispatchNode.execute(callerFrame, self, nameToJavaString.execute(this, name),
                    RubyArguments.repack(rubyArgs, self, 1), PRIVATE);
        }
    }

    // MRI names it the "allocator function" and it's associated per class and follows the ancestor
    // chain. We use a normal Ruby method, different than Class#allocate as Class#allocate
    // must be able to instantiate any Ruby object and should not be overridden.
    @GenerateUncached
    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE,
            alwaysInlined = true)
    public abstract static class AllocateNode extends AlwaysInlinedMethodNode {
        @Specialization(guards = "!rubyClass.isSingleton")
        RubyBasicObject allocate(Frame callerFrame, RubyClass rubyClass, Object[] rubyArgs, RootCallTarget target) {
            final RubyBasicObject instance = new RubyBasicObject(rubyClass, getLanguage().basicObjectShape);
            AllocationTracing.traceInlined(instance, "Class", "__allocate__", this);
            return instance;
        }

        @Specialization(guards = "rubyClass.isSingleton")
        Shape allocateSingleton(Frame callerFrame, RubyClass rubyClass, Object[] rubyArgs, RootCallTarget target) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().typeErrorCantCreateInstanceOfSingletonClass(this));
        }
    }
}
