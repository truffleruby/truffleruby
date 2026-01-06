/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.proc;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.UnboundMethodNodes.MethodRuby2KeywordsNode;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.ArgumentDescriptorUtils;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.parser.ArgumentDescriptor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

@CoreModule(value = "Proc", isClass = true)
public abstract class ProcNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }
    }

    @CoreMethod(names = "new", constructor = true, needsBlock = true, rest = true, split = Split.HEURISTIC)
    public abstract static class ProcNewNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static ProcNewNode create() {
            return ProcNodesFactory.ProcNewNodeFactory.create(null);
        }

        public abstract RubyProc executeProcNew(VirtualFrame frame, RubyClass procClass, Object[] args, Object block);

        @Specialization
        RubyProc proc(VirtualFrame frame, RubyClass procClass, Object[] args, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorProcWithoutBlock(this));
        }

        @Specialization(guards = "procClass == metaClass(block)")
        RubyProc procNormal(RubyClass procClass, Object[] args, RubyProc block) {
            return block;
        }

        @Specialization(guards = "procClass != metaClass(block)")
        RubyProc procSpecial(VirtualFrame frame, RubyClass procClass, Object[] args, RubyProc block,
                @Cached DispatchNode initialize) {
            // Instantiate a new instance of procClass as classes do not correspond

            final RubyProc proc = block.duplicate(procClass, getLanguage().procShape, this);
            initialize.callWithDescriptor(proc, "initialize", block, RubyArguments.getDescriptor(frame), args);
            return proc;
        }

        protected RubyClass metaClass(RubyProc object) {
            return object.getMetaClass();
        }
    }

    @CoreMethod(names = "clone")
    public abstract static class CloneNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc clone(RubyProc proc,
                @Cached DispatchNode initializeCloneNode) {
            final RubyProc copy = proc.duplicate(getLanguage().procShape, this);
            initializeCloneNode.call(copy, "initialize_clone", proc);
            return copy;
        }
    }

    @CoreMethod(names = "dup")
    public abstract static class DupNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc dup(RubyProc proc,
                @Cached DispatchNode initializeDupNode) {
            final RubyProc copy = proc.duplicate(getLanguage().procShape, this);
            initializeDupNode.call(copy, "initialize_dup", proc);
            return copy;
        }
    }

    @CoreMethod(names = "arity")
    public abstract static class ArityNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int arity(RubyProc proc) {
            return proc.getArityNumber();
        }
    }

    @CoreMethod(names = "binding")
    public abstract static class BindingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyBinding binding(RubyProc proc) {
            final MaterializedFrame frame = proc.declarationFrame;
            final SourceSection sourceSection = proc.getSharedMethodInfo().getSourceSection();
            return BindingNodes.createBinding(getContext(), getLanguage(), frame, sourceSection);
        }
    }

    @GenerateUncached
    @CoreMethod(names = { "call", "[]", "yield" }, rest = true, needsBlock = true, alwaysInlined = true)
    public abstract static class CallNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object call(Frame callerFrame, RubyProc proc, Object[] rubyArgs, RootCallTarget target,
                @Cached CallBlockNode callBlockNode) {
            return callBlockNode.executeCallBlock(
                    this,
                    proc.declarationContext,
                    proc,
                    ProcOperations.getSelf(proc),
                    RubyArguments.getBlock(rubyArgs),
                    RubyArguments.getDescriptor(rubyArgs),
                    RubyArguments.getRawArguments(rubyArgs));
        }
    }

    @CoreMethod(names = { "==", "eql?" }, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean equal(RubyProc self, Object otherObj,
                @Cached LogicalClassNode logicalClassNode,
                @Cached InlinedConditionProfile classProfile,
                @Cached InlinedConditionProfile lambdaProfile) {
            if (classProfile.profile(this, logicalClassNode.execute(self) != logicalClassNode.execute(otherObj))) {
                return false;
            }
            final RubyProc other = (RubyProc) otherObj;

            if (lambdaProfile.profile(this, self.isLambda() != other.isLambda())) {
                return false;
            }

            return self.callTarget == other.callTarget &&
                    self.declarationFrame == other.declarationFrame;
        }
    }

    @CoreMethod(names = "lambda?")
    public abstract static class LambdaNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean lambda(RubyProc proc) {
            return proc.type == ProcType.LAMBDA;
        }

    }

    @Primitive(name = "proc_parameters")
    public abstract static class ParametersNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray parameters(RubyProc proc, boolean isLambda) {
            final ArgumentDescriptor[] argsDesc = proc.getArgumentDescriptors();
            return ArgumentDescriptorUtils
                    .argumentDescriptorsToParameters(getLanguage(), getContext(), argsDesc, isLambda);
        }

    }

    @CoreMethod(names = "source_location")
    public abstract static class SourceLocationNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        Object sourceLocation(RubyProc proc) {
            var sourceSection = proc.getSharedMethodInfo().getSourceSection();

            var source = sourceSection.getSource();
            if (!sourceSection.isAvailable() || RubyLanguage.getPath(source).endsWith("/lib/truffle/truffle/cext.rb")) {
                return nil;
            } else {
                return getLanguage().rubySourceLocation(sourceSection, fromJavaStringNode, this);
            }
        }

    }


    @Primitive(name = "proc_create_same_arity")
    public abstract static class ProcCreateSameArityNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyProc createSameArityProc(RubyProc userProc, RubyProc block) {
            final RubyProc composedProc = block.withArity(
                    userProc.arity,
                    userProc.argumentDescriptors,
                    coreLibrary().procClass,
                    getLanguage().procShape);
            AllocationTracing.trace(composedProc, this);
            return composedProc;
        }
    }

    @Primitive(name = "proc_specify_arity", lowerFixnum = 1)
    public abstract static class ProcSpecifyArityNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyProc specifyArityProc(RubyProc block, int argc) {
            final Arity newArity;
            if (argc <= -1) {
                newArity = new Arity(-(argc + 1), 0, true);
            } else {
                newArity = new Arity(argc, 0, false);
            }

            final RubyProc composedProc = block.withArity(
                    newArity,
                    null,
                    coreLibrary().procClass,
                    getLanguage().procShape);
            AllocationTracing.trace(composedProc, this);
            return composedProc;
        }
    }

    @Primitive(name = "proc_symbol_to_proc_symbol")
    public abstract static class ProcSymbolToProcSymbolNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object symbolToProcSymbol(RubyProc proc) {
            if (proc.arity == Arity.SYMBOL_TO_PROC_ARITY) {
                return getSymbol(proc.getSharedMethodInfo().getMethodName());
            } else {
                return nil;
            }
        }

    }

    @Primitive(name = "single_block_arg")
    public abstract static class SingleBlockArgNode extends PrimitiveNode {

        @Specialization
        Object singleBlockArg(VirtualFrame frame,
                @Cached InlinedConditionProfile emptyArgsProfile,
                @Cached InlinedConditionProfile singleArgProfile) {

            /* In Rubinius, this method inspects the values yielded to the block, regardless of whether the block
             * captures the values, and returns the first value in the list of values yielded to the block.
             *
             * NB: In our case the arguments have already been destructured by the time this node is encountered. Thus,
             * we don't need to do the destructuring work that Rubinius would do and in the case that we receive
             * multiple arguments we need to reverse the destructuring by collecting the values into an array. */
            int userArgumentCount = RubyArguments.getPositionalArgumentsCount(frame.getArguments());

            if (emptyArgsProfile.profile(this, userArgumentCount == 0)) {
                return nil;
            } else {
                if (singleArgProfile.profile(this, userArgumentCount == 1)) {
                    return RubyArguments.getArgument(frame, 0);
                } else {
                    Object[] extractedArguments = RubyArguments.getPositionalArguments(frame.getArguments());
                    return createArray(extractedArguments, userArgumentCount);
                }
            }
        }
    }

    @Primitive(name = "proc_ruby2_keywords", raiseIfFrozen = 0)
    public abstract static class ProcRuby2KeywordsNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object ruby2Keywords(RubyProc proc) {
            return MethodRuby2KeywordsNode.ruby2Keywords(proc.getSharedMethodInfo(), proc.callTarget);
        }
    }
}
