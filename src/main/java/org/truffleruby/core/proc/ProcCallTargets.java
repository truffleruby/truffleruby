/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.proc;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.RootNode;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyProcRootNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.parser.YARPBlockNodeTranslator;

import java.util.function.Supplier;

/** Holds compiled call target for a {@link BlockDefinitionNode} (one instance per node). Each {@link RubyProc} created
 * by evaluating the definition has a reference to one of these. The point is that there are two possible call targets
 * for blocks: the proc and the lambda one. We only eagerly compile one in
 * {@link YARPBlockNodeTranslator#compileBlockNode}, but we might need to later compile the other, given a
 * {@link RubyProc} instance. This class thus acts as a shared cache for the call targets. */
public final class ProcCallTargets {

    // At least one of those two call targets won't be null.
    @CompilationFinal private RootCallTarget callTargetForProc;
    @CompilationFinal private RootCallTarget callTargetForLambda;

    // Non-null if one of the call targets is null.
    @CompilationFinal private Supplier<RootCallTarget> altCallTargetCompiler;

    public ProcCallTargets(
            RootCallTarget callTargetForProc,
            RootCallTarget callTargetForLambda,
            Supplier<RootCallTarget> altCallTargetCompiler) {
        // both can be set for lambda {} if #lambda is not Kernel#lambda
        assert callTargetForProc != null || callTargetForLambda != null;

        assert callTargetForProc == null || validProcRootNode(callTargetForProc);
        assert callTargetForLambda == null || validLambdaRootNode(callTargetForLambda);

        this.callTargetForProc = callTargetForProc;
        this.callTargetForLambda = callTargetForLambda;
        this.altCallTargetCompiler = altCallTargetCompiler;
    }

    public ProcCallTargets(RootCallTarget callTargetForLambda) {
        this(null, callTargetForLambda, null);
    }

    public RootCallTarget getCallTargetForProc() {
        if (callTargetForProc == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTargetForProc = altCallTargetCompiler.get();
            copySplit(callTargetForLambda, callTargetForProc);
            assert validProcRootNode(callTargetForProc);
            altCallTargetCompiler = null;
        }
        return callTargetForProc;
    }

    public RootCallTarget getCallTargetForLambda() {
        if (callTargetForLambda == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTargetForLambda = altCallTargetCompiler.get();
            copySplit(callTargetForProc, callTargetForLambda);
            assert validLambdaRootNode(callTargetForLambda);
            altCallTargetCompiler = null;
        }
        return callTargetForLambda;
    }

    public boolean hasCallTargetForProc() {
        return callTargetForProc != null;
    }

    public boolean hasCallTargetForLambda() {
        return callTargetForLambda != null;
    }

    private void copySplit(RootCallTarget src, RootCallTarget dst) {
        RubyRootNode.of(dst).setSplit(RubyRootNode.of(src).getSplit());
    }

    private boolean validProcRootNode(RootCallTarget callTarget) {
        final RootNode rootNode = callTarget.getRootNode();
        assert rootNode instanceof RubyProcRootNode : rootNode + " " + rootNode.getClass();
        return true;
    }

    /** {@link ModuleNodes.DefineMethodNode} relies on this always being a RubyLambdaRootNode */
    private boolean validLambdaRootNode(RootCallTarget callTarget) {
        final RootNode rootNode = callTarget.getRootNode();
        assert rootNode instanceof RubyLambdaRootNode : rootNode + " " + rootNode.getClass();
        return true;
    }

    @Override
    public String toString() {
        return StringUtils.format(
                "ProcCallTargets(callTargetForProc = %s, callTargetForLambda = %s, altCallTargetCompiler = %s)",
                callTargetForProc,
                callTargetForLambda,
                altCallTargetCompiler);
    }
}
