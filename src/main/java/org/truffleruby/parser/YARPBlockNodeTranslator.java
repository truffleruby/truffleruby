/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import org.prism.Nodes;
import org.prism.Nodes.Node;
import org.prism.Nodes.ParametersNode;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Split;
import org.truffleruby.core.cast.SplatCastNode;
import org.truffleruby.core.cast.SplatCastNodeGen;
import org.truffleruby.core.proc.ProcCallTargets;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyProcRootNode;
import org.truffleruby.language.arguments.MissingArgumentBehavior;
import org.truffleruby.language.arguments.ReadPreArgumentNode;
import org.truffleruby.language.arguments.ShouldDestructureNode;
import org.truffleruby.language.control.DynamicReturnNode;
import org.truffleruby.language.control.IfElseNodeGen;
import org.truffleruby.language.control.InvalidReturnNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.locals.LocalVariableType;
import org.truffleruby.language.locals.ReadLocalVariableNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.BlockDefinitionNodeGen;

import java.util.function.Supplier;

public final class YARPBlockNodeTranslator extends YARPTranslator {

    private final Arity arity;
    /** Whether a block itself accepts a block parameter (&block). The Arity class does not contain this information. */
    private final boolean acceptsBlockParameter;

    public YARPBlockNodeTranslator(
            TranslatorEnvironment environment,
            Arity arity,
            boolean acceptsBlockParameter,
            RubyDeferredWarnings rubyWarnings) {
        super(environment, rubyWarnings);
        this.arity = arity;
        this.acceptsBlockParameter = acceptsBlockParameter;
    }

    public RubyNode compileBlockNode(Node body, ParametersNode parameters, Node parametersNode, String[] locals,
            boolean isStabbyLambda, SourceSection sourceSection) {
        declareLocalVariables(locals);
        if (parametersNode instanceof Nodes.ItParametersNode) {
            environment.declareVar(IT_PARAMETER_NAME);
        }

        final RubyNode loadArguments = new YARPLoadArgumentsTranslator(
                environment,
                parameters,
                arity,
                !isStabbyLambda,
                false,
                this).translate();

        final RubyNode preludeProc = !isStabbyLambda
                ? preludeProc(arity, loadArguments, parameters)
                : null; // proc will never be compiled for stabby lambdas

        final RubyNode bodyNode = translateNodeOrNil(body).simplifyAsTailExpression();

        final boolean isLambdaMethodCall = !isStabbyLambda && environment.literalBlockPassedToMethod.equals("lambda");
        final boolean emitLambda = isStabbyLambda || isLambdaMethodCall;

        final Supplier<RootCallTarget> procCompiler = procCompiler(
                arity,
                preludeProc,
                bodyNode,
                isLambdaMethodCall,
                language,
                environment,
                sourceSection);

        final Supplier<RootCallTarget> lambdaCompiler = lambdaCompiler(
                isStabbyLambda,
                arity,
                loadArguments,
                bodyNode,
                emitLambda,
                language,
                environment,
                sourceSection);

        int frameOnStackMarkerSlot;

        if (emitLambda || frameOnStackMarkerSlotStack.isEmpty()) {
            frameOnStackMarkerSlot = -1;
        } else {
            frameOnStackMarkerSlot = frameOnStackMarkerSlotStack.peek();
        }

        final ProcCallTargets callTargets;
        if (isStabbyLambda) {
            // 100% lambda

            // use only lambda compiler as no conversions to proc are expected
            final RootCallTarget callTarget = lambdaCompiler.get();
            callTargets = new ProcCallTargets(callTarget);
        } else if (isLambdaMethodCall) {
            // is supposed to be lambda, that will be checked in runtime

            // a "lambda" method call is treated as lambda (a method is supposed to be a Kernel#lambda)
            // but could be converted to a proc in runtime in case it isn't Kernel#lambda or it's overridden
            callTargets = new ProcCallTargets(null, lambdaCompiler.get(), procCompiler);
        } else {
            // 100% proc

            // it could be converted later to lambda, e.g. if passed as argument to Module#define_method
            callTargets = new ProcCallTargets(procCompiler.get(), null, lambdaCompiler);
        }

        final RubyNode rubyNode = BlockDefinitionNodeGen.create(
                emitLambda ? ProcType.LAMBDA : ProcType.PROC,
                environment.getSharedMethodInfo(),
                callTargets,
                environment.getBreakID(),
                frameOnStackMarkerSlot);
        return rubyNode;
    }

    private void declareLocalVariables(String[] locals) {
        // YARP doesn't add hidden locals for rest/keyrest/block anonymous parameters or ...
        for (String name : locals) {
            environment.declareVar(name);
        }
    }

    private RubyNode preludeProc(
            Arity arity,
            RubyNode loadArguments,
            Nodes.ParametersNode parameters) {
        final RubyNode preludeNode;

        // as opposed to lambdas and methods, procs may destructure a single Array argument
        if (shouldConsiderDestructuringArrayArg(arity)) {
            final RubyNode readFirstArgumentNode = YARPTranslator.profileArgument(
                    language,
                    new ReadPreArgumentNode(0, arity.acceptsKeywords(), MissingArgumentBehavior.RUNTIME_ERROR));
            final SplatCastNode castArrayNode = SplatCastNodeGen
                    .create(language, SplatCastNode.NilBehavior.NIL, true, readFirstArgumentNode);
            castArrayNode.doNotCopy();

            final int arraySlot = environment.declareLocalTemp("destructure");
            final RubyNode writeArrayNode = new WriteLocalVariableNode(arraySlot, castArrayNode);
            final RubyNode readArrayNode = new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, arraySlot);

            final var translator = new YARPParametersNodeToDestructureTranslator(
                    environment,
                    parameters,
                    readArrayNode,
                    this);
            final RubyNode newDestructureArguments = translator.translate();

            preludeNode = IfElseNodeGen.create(
                    new ShouldDestructureNode(arity.acceptsKeywords(), writeArrayNode),
                    newDestructureArguments,
                    loadArguments);
        } else {
            preludeNode = loadArguments;
        }

        return preludeNode;
    }

    private static Supplier<RootCallTarget> procCompiler(
            Arity arityForCheck,
            RubyNode preludeProc,
            RubyNode body,
            boolean isLambdaMethodCall,
            RubyLanguage language,
            TranslatorEnvironment environment,
            SourceSection sourceSection) {

        // We construct the supplier in a static method to make sure we do not accidentally capture the
        // translator and other unwanted objects.

        return () -> {
            final RubyNode bodyForProc = isLambdaMethodCall
                    ? body.cloneUninitialized() // previously compiled as lambda, must copy
                    : body;

            final RubyNode bodyProc = composeBody(environment, preludeProc, bodyForProc);

            final RubyProcRootNode newRootNodeForProcs = new RubyProcRootNode(
                    language,
                    sourceSection,
                    environment.computeFrameDescriptor(),
                    environment.getSharedMethodInfo(),
                    bodyProc,
                    Split.HEURISTIC,
                    environment.getReturnID(),
                    arityForCheck);

            final RootCallTarget callTarget = newRootNodeForProcs.getCallTarget();

            if (isLambdaMethodCall) {
                // The block was previously compiled as lambda, we must rewrite the return nodes to InvalidReturnNode,
                // but only if the proc is within a lambda body (otherwise the returns are still valid, but return from
                // the surrounding function instead of returning from the lambda).
                //
                // Note that the compilation to lambda does not alter the original returnID (instead it's "hijacked"
                // and used in RubyLambdaRootNode).
                //
                // replace() should be called **after** nodes are adopted (in RootNode#getCallTarget() using adoptChildren())
                // so nodes know their parent.
                for (DynamicReturnNode returnNode : NodeUtil
                        .findAllNodeInstances(bodyForProc, DynamicReturnNode.class)) {
                    if (returnNode.returnID == ReturnID.MODULE_BODY) {
                        returnNode.replace(new InvalidReturnNode(returnNode.value));
                    }
                }
            }

            return callTarget;
        };
    }

    private static Supplier<RootCallTarget> lambdaCompiler(
            boolean isStabbyLambda,
            Arity arityForCheck,
            RubyNode loadArguments,
            RubyNode body,
            boolean emitLambda,
            RubyLanguage language,
            TranslatorEnvironment environment,
            SourceSection sourceSection) {

        // We construct the supplier in a static method to make sure we do not accidentally capture the
        // translator and other unwanted objects.

        return () -> {
            // Clone arguments/body nodes as far as they might be already adopted for a proc case (a node cannot have two parents).
            // Skip cloning as a performance optimisation if we are sure
            // that nodes were initially "compiled" for lambda and weren't used for a proc case earlier.
            final RubyNode bodyForLambda = emitLambda
                    // Stabby lambda: the proc compiler will never be called, safe to not copy.
                    // Method named lambda: if conversion to proc needed, will copy in the proc compiler & reverse the
                    // return transformation.
                    ? body
                    : body.cloneUninitialized();

            final RubyNode preludeLambda = isStabbyLambda ? loadArguments : loadArguments.cloneUninitialized();

            final RubyNode bodyLambda = composeBody(environment, preludeLambda, bodyForLambda);

            final RubyLambdaRootNode newRootNodeForLambdas = new RubyLambdaRootNode(
                    language,
                    sourceSection,
                    environment.computeFrameDescriptor(),
                    environment.getSharedMethodInfo(),
                    bodyLambda,
                    Split.HEURISTIC,
                    environment.getReturnID(), // "hijack" return ID
                    environment.getBreakID(),
                    arityForCheck);

            final RootCallTarget callTarget = newRootNodeForLambdas.getCallTarget();

            if (!isStabbyLambda) {
                // If we end up executing this block as a lambda, but don't know it statically, e.g., `lambda {}` or
                // `define_method(:foo, proc {})`), then returns are always valid and return from that lambda.
                // This needs to run after nodes are adopted for replace() (during callTarget initialisation)
                // to work and nodes to know their parent.
                for (InvalidReturnNode returnNode : NodeUtil
                        .findAllNodeInstances(bodyForLambda, InvalidReturnNode.class)) {
                    returnNode.replace(new DynamicReturnNode(environment.getReturnID(), returnNode.value));
                }
            }

            return callTarget;
        };
    }

    private static RubyNode composeBody(TranslatorEnvironment environment, RubyNode prelude, RubyNode body) {
        body = YARPTranslator.sequence(prelude, body);

        if (environment.getFlipFlopStates().size() > 0) {
            body = YARPTranslator.sequence(YARPTranslator.initFlipFlopStates(environment), body);
        }

        return body;
    }

    private boolean shouldConsiderDestructuringArrayArg(Arity arity) {
        if (!arity.hasRest() && arity.getRequired() + arity.getOptional() <= 1) {
            // If we accept at most 0 or 1 arguments, there's never any need to destructure
            return false;
        } else if (arity.hasRest() && arity.getRequired() == 0) {
            // If there are only a rest argument and optional arguments, there is no need to destructure.
            // Because the first optional argument (or the rest if no optional) will take the whole array.
            return false;
        } else {
            return true;
        }
    }

    @Override
    public RubyNode visitCallNode(Nodes.CallNode node) {
        // emit a warning if `it` is called without arguments in a block without ordinal parameters
        boolean noBlockParameters = !arity.acceptsKeywords() && !arity.acceptsPositionalArguments() &&
                !acceptsBlockParameter;
        if (node.name.equals("it") && node.receiver == null && node.isVariableCall() && noBlockParameters) {
            SourceSection section = rubySource.getSource().createSection(node.startOffset, node.length);

            String sourcePath = rubySource.getSourcePath(language).intern();
            int lineNumber = section.getStartLine() + rubySource.getLineOffset();
            String message = "`it` calls without arguments will refer to the first block param in Ruby 3.4; use it() or self.it";

            rubyWarnings.warn(sourcePath, lineNumber, message);
        }

        return super.visitCallNode(node);
    }

}
