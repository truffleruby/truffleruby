/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.collections.Pair;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.ProcCallTargets;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.methods.SymbolProcNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.parser.ArgumentDescriptor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

import java.util.Map;

@CoreModule(value = "Symbol", isClass = true)
public abstract class SymbolNodes {

    @CoreMethod(names = "all_symbols", onSingleton = true)
    public abstract static class AllSymbolsNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyArray allSymbols() {
            return createArray(getLanguage().symbolTable.allSymbols().toArray());
        }
    }

    @CoreMethod(names = { "==", "eql?" }, required = 1)
    public abstract static class EqualNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean equal(RubySymbol a, Object b) {
            return a == b;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashSymbolNode extends RubyBaseNode {

        public abstract long execute(Node node, RubySymbol rubySymbol);

        // Cannot cache a Symbol's hash while pre-initializing, as it will change in SymbolTable#rehash()
        @Specialization(
                guards = { "isSingleContext()", "symbol == cachedSymbol", "!preInitializing" },
                limit = "1")
        static long hashCached(Node node, RubySymbol symbol,
                @Cached("isPreInitializing(getContext())") boolean preInitializing,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("hash(node, cachedSymbol)") long cachedHash) {
            return cachedHash;
        }

        @Specialization(replaces = "hashCached")
        static long hash(Node node, RubySymbol symbol) {
            return symbol.computeHashCode(getContext(node).getHashing());
        }

        protected boolean isPreInitializing(RubyContext context) {
            return context.isPreInitializing();
        }
    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        public static HashNode create() {
            return SymbolNodesFactory.HashNodeFactory.create(null);
        }

        public abstract long execute(RubySymbol rubySymbol);

        @Specialization
        long hash(RubySymbol symbol,
                @Cached HashSymbolNode hash) {
            return hash.execute(this, symbol);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "to_proc", alwaysInlined = true)
    @ImportStatic(DeclarationContext.class)
    public abstract static class ToProcNode extends AlwaysInlinedMethodNode {

        public static ToProcNode create() {
            return SymbolNodesFactory.ToProcNodeFactory.create();
        }

        @Specialization(
                guards = {
                        "isSingleContext()",
                        "symbol == cachedSymbol",
                        "getRefinements(callerFrame) == cachedRefinements" },
                limit = "1")
        RubyProc toProcCached(Frame callerFrame, RubySymbol symbol, Object[] rubyArgs, RootCallTarget target,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("getRefinements(callerFrame)") Map<RubyModule, RubyModule[]> cachedRefinements,
                @Cached("getOrCreateCallTarget(getContext(), getLanguage(), cachedSymbol, cachedRefinements)") RootCallTarget callTarget,
                @Cached("createProc(getContext(), getLanguage(), cachedRefinements, callTarget)") RubyProc cachedProc) {
            return cachedProc;
        }

        @Specialization(
                guards = {
                        "symbol == cachedSymbol",
                        "getRefinements(callerFrame) == NO_REFINEMENTS" },
                limit = "1")
        RubyProc toProcCachedNoRefinements(
                Frame callerFrame, RubySymbol symbol, Object[] rubyArgs, RootCallTarget target,
                @Cached("symbol") RubySymbol cachedSymbol,
                @Cached("getOrCreateCallTarget(getContext(), getLanguage(), cachedSymbol, NO_REFINEMENTS)") RootCallTarget callTarget) {
            return createProc(getContext(), getLanguage(), DeclarationContext.NO_REFINEMENTS, callTarget);
        }

        @Specialization(replaces = { "toProcCached", "toProcCachedNoRefinements" })
        RubyProc toProcUncached(Frame callerFrame, RubySymbol symbol, Object[] rubyArgs, RootCallTarget target) {
            final Map<RubyModule, RubyModule[]> refinements = getRefinements(callerFrame);
            final RootCallTarget callTarget = getOrCreateCallTarget(getContext(), getLanguage(), symbol, refinements);
            return createProc(getContext(), getLanguage(), refinements, callTarget);
        }

        @TruffleBoundary
        public static RootCallTarget getOrCreateCallTarget(RubyContext context, RubyLanguage language,
                RubySymbol symbol, Map<RubyModule, RubyModule[]> refinements) {
            if (refinements == DeclarationContext.NO_REFINEMENTS) {
                return symbol.getCallTargetNoRefinements(language);
            } else {
                // TODO (eregon, 23 Sep 2020): this should ideally cache on the refinements by comparing classes, and not by identity.
                return ConcurrentOperations.getOrCompute(
                        context.cachedSymbolToProcTargetsWithRefinements,
                        Pair.create(symbol, refinements),
                        key -> createCallTarget(language, symbol, refinements));
            }
        }

        public static RubyProc createProc(RubyContext context, RubyLanguage language,
                Map<RubyModule, RubyModule[]> refinements, RootCallTarget callTarget) {
            final InternalMethod method = context.getCoreMethods().SYMBOL_TO_PROC;
            final DeclarationContext declarationContext = refinements == DeclarationContext.NO_REFINEMENTS
                    ? DeclarationContext.NONE
                    : new DeclarationContext(Visibility.PUBLIC, null, refinements);

            final Object[] args = RubyArguments
                    .pack(null, null, method, declarationContext, null, nil, nil,
                            NoKeywordArgumentsDescriptor.INSTANCE, EMPTY_ARGUMENTS);
            // MRI raises an error on Proc#binding if you attempt to access the binding of a Proc generated
            // by Symbol#to_proc. We generate a declaration frame here so that all procedures will have a
            // binding as this simplifies the logic elsewhere in the runtime.
            final var variables = new SpecialVariableStorage();
            final MaterializedFrame declarationFrame = language.createEmptyDeclarationFrame(args, variables);

            return ProcOperations.createRubyProc(
                    context.getCoreLibrary().procClass,
                    language.procShape,
                    ProcType.LAMBDA,
                    RubyRootNode.of(callTarget).getSharedMethodInfo(),
                    new ProcCallTargets(callTarget),
                    declarationFrame,
                    variables,
                    method,
                    null,
                    declarationContext);
        }

        public static RootCallTarget createCallTarget(RubyLanguage language, RubySymbol symbol,
                // unused but the CallTarget will capture the refinements in the DispatchNode on first call
                Map<RubyModule, RubyModule[]> refinements) {
            final SourceSection sourceSection = CoreLibrary.UNAVAILABLE_SOURCE_SECTION;

            final SharedMethodInfo sharedMethodInfo = SharedMethodInfo.forMethod(
                    sourceSection,
                    LexicalScope.IGNORE,
                    Arity.SYMBOL_TO_PROC_ARITY,
                    symbol.getString(),
                    "&:" + symbol.getString(),
                    "Symbol#to_proc",
                    ArgumentDescriptor.AT_LEAST_ONE_UNNAMED);

            // ModuleNodes.DefineMethodNode relies on the lambda CallTarget to always use a RubyLambdaRootNode,
            // and we want to use a single CallTarget for both proc and lambda.
            final RubyLambdaRootNode rootNode = new RubyLambdaRootNode(
                    language,
                    sourceSection,
                    new FrameDescriptor(nil),
                    sharedMethodInfo,
                    new SymbolProcNode(symbol.getString()),
                    Split.HEURISTIC,
                    ReturnID.INVALID,
                    BreakID.INVALID,
                    Arity.SYMBOL_TO_PROC_ARITY);

            return rootNode.getCallTarget();
        }

        protected Map<RubyModule, RubyModule[]> getRefinements(Frame callerFrame) {
            final DeclarationContext declarationContext = RubyArguments.tryGetDeclarationContext(callerFrame);
            return declarationContext != null
                    ? declarationContext.getRefinements()
                    : DeclarationContext.NONE.getRefinements();
        }

        protected int getCacheLimit() {
            return getLanguage().options.SYMBOL_TO_PROC_CACHE;
        }

    }

    @CoreMethod(names = "to_s")
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyString toS(RubySymbol symbol) {
            return createString(symbol.tstring, symbol.encoding);
        }
    }

    @CoreMethod(names = "name")
    public abstract static class NameNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        ImmutableRubyString toS(RubySymbol symbol) {
            return symbol.getName(getLanguage());
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }
    }

}
