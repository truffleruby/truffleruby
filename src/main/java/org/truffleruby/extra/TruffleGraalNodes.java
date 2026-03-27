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
package org.truffleruby.extra;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.cast.ToCallTargetNode;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.ProcCallTargets;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.locals.ReadDeclarationVariableNode;
import org.truffleruby.language.locals.WriteDeclarationVariableNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.NodeUtil;

import java.lang.invoke.VarHandle;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

@CoreModule("Truffle::Graal")
public abstract class TruffleGraalNodes {

    @Primitive(name = "always_split", isPublic = true)
    public abstract static class AlwaysSplitNode extends PrimitiveArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object alwaysSplit(RubyModule module, RubySymbol instanceMethod) {
            String name = instanceMethod.getString();
            var method = ModuleOperations.lookupMethodUncached(module, name, null);

            if (method == null || method.isUndefined()) {
                throw new RaiseException(getContext(), coreExceptions().nameErrorUndefinedMethod(name, module, this));
            }

            final RootCallTarget callTarget = method.getCallTarget();
            if (getContext().getOptions().ALWAYS_SPLIT_HONOR) {
                RubyRootNode.of(callTarget).setSplit(Split.ALWAYS);
            }
            return nil;
        }
    }

    @CoreMethod(names = "never_split", onSingleton = true, required = 1)
    public abstract static class NeverSplitNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object neverSplit(Object executable,
                @Cached ToCallTargetNode toCallTargetNode) {
            final RootCallTarget callTarget = toCallTargetNode.execute(this, executable);
            if (getContext().getOptions().NEVER_SPLIT_HONOR) {
                RubyRootNode.of(callTarget).setSplit(Split.NEVER);
            }
            return executable;
        }
    }

    /** This method creates a new Proc for an existing <b>lambda</b> proc with a copy of the captured variables' values,
     * which is correct if these variables are not changed in the parent scope later on. It works by replacing
     * {@link ReadDeclarationVariableNode} with the captured variables' values. This avoids constantly reading from the
     * declaration frame (which always escapes in a define_method) and folds many checks on these captured variables
     * since their values become compilation constants.
     * <p>
     * Similar to Smalltalk's fixTemps, but not mutating the Proc. */
    @CoreMethod(names = "copy_captured_locals", onSingleton = true, required = 1)
    public abstract static class CopyCapturedLocalsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "proc.isLambda()")
        RubyProc copyCapturedLocals(RubyProc proc) {
            final RubyLambdaRootNode rootNode = RubyLambdaRootNode.of(proc.callTarget);
            final RubyNode newBody = rootNode.copyBody();

            assert NodeUtil.findAllNodeInstances(newBody, WriteDeclarationVariableNode.class).isEmpty();

            for (ReadDeclarationVariableNode readNode : NodeUtil
                    .findAllNodeInstances(newBody, ReadDeclarationVariableNode.class)) {
                final MaterializedFrame frame = RubyArguments
                        .getDeclarationFrame(proc.declarationFrame, readNode.getFrameDepth() - 1);
                final Object value = frame.getValue(readNode.getFrameSlot());
                readNode.replace(new ObjectLiteralNode(value));
            }

            final RubyLambdaRootNode newRootNode = rootNode.copyRootNode(rootNode.getSharedMethodInfo(), newBody);
            final RootCallTarget newCallTarget = newRootNode.getCallTarget();

            final SpecialVariableStorage variables = proc.declarationVariables;

            final Object[] args = RubyArguments
                    .pack(
                            null,
                            null,
                            RubyArguments.getMethod(proc.declarationFrame),
                            null,
                            nil,
                            nil,
                            NoKeywordArgumentsDescriptor.INSTANCE,
                            EMPTY_ARGUMENTS);

            // The Proc no longer needs the original declaration frame. However, all procs must have a
            // declaration frame (to allow Proc#binding) so we shall create an empty one.
            final MaterializedFrame newDeclarationFrame = getLanguage().createEmptyDeclarationFrame(args, variables);

            return new RubyProc(
                    coreLibrary().procClass,
                    getLanguage().procShape,
                    ProcType.LAMBDA,
                    proc.arity,
                    proc.argumentDescriptors,
                    new ProcCallTargets(newCallTarget),
                    newCallTarget,
                    newDeclarationFrame,
                    variables,
                    proc.declaringMethod,
                    proc.frameOnStackMarker,
                    proc.declarationContext);

            // TODO(norswap): trace allocation?
        }

    }

    @Primitive(name = "assert_compilation_constant")
    public abstract static class AssertCompilationConstantNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object assertCompilationConstant(Object value) {
            if (!CompilerDirectives.isCompilationConstant(value)) {
                notConstantBoundary();
            }

            return value;
        }

        @TruffleBoundary
        private void notConstantBoundary() {
            throw new RaiseException(getContext(), coreExceptions().graalErrorAssertConstantNotConstant(this));
        }
    }

    @Primitive(name = "assert_not_compiled")
    public abstract static class AssertNotCompiledNode extends PrimitiveNode {

        @Specialization
        Object assertNotCompiled() {
            if (CompilerDirectives.inCompiledCode()) {
                compiledBoundary();
            }

            return nil;
        }

        @TruffleBoundary
        private void compiledBoundary() {
            throw new RaiseException(getContext(), coreExceptions().graalErrorAssertNotCompiledCompiled(this));
        }
    }

    @Primitive(name = "compiler_bailout")
    public abstract static class BailoutNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "strings.isRubyString(this, message)", limit = "1")
        static Object bailout(Object message,
                @Cached RubyStringLibrary strings,
                @Cached ToJavaStringNode toJavaStringNode,
                @Bind Node node) {
            CompilerDirectives.bailout(toJavaStringNode.execute(node, message));
            return nil;
        }
    }

    @Primitive(name = "blackhole", isPublic = true)
    public abstract static class BlackholeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object blackhole(boolean value) {
            CompilerDirectives.blackhole(value);
            return nil;
        }

        @Specialization
        Object blackhole(int value) {
            CompilerDirectives.blackhole(value);
            return nil;
        }

        @Specialization
        Object blackhole(long value) {
            CompilerDirectives.blackhole(value);
            return nil;
        }

        @Specialization
        Object blackhole(double value) {
            CompilerDirectives.blackhole(value);
            return nil;
        }

        @Specialization
        Object blackhole(Object value) {
            CompilerDirectives.blackhole(value);
            return nil;
        }

    }

    @CoreMethod(names = "boundary", onSingleton = true)
    public abstract static class BoundaryNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary(allowInlining = false)
        @Specialization
        Object boundary() {
            return nil;
        }
    }

    @CoreMethod(names = "boundary_allow_inlining", onSingleton = true)
    public abstract static class BoundaryAllowInliningNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary(allowInlining = true)
        @Specialization
        Object boundaryAllowInlining() {
            return nil;
        }
    }

    @CoreMethod(names = "total_compilation_time", onSingleton = true)
    public abstract static class TotalCompilationTimeNode extends CoreMethodArrayArgumentsNode {

        private static CompilationMXBean bean;

        @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
        @TruffleBoundary
        @Specialization
        long totalCompilationTime() {
            if (bean == null) {
                var compilationMXBean = ManagementFactory.getCompilationMXBean();
                VarHandle.storeStoreFence();
                bean = compilationMXBean;
            }

            return bean.getTotalCompilationTime();
        }
    }
}
