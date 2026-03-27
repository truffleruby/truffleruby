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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import com.oracle.truffle.api.RootCallTarget;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolNodes;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.methods.DeclarationContext;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Map;

/** The `&` in `foo(&block)`. Converts the passed block to a RubyProc or nil. Must be a RubyNode because it's used in
 * the translator. */
@NodeChild(value = "childNode", type = RubyNode.class)
@ImportStatic(DeclarationContext.class)
public abstract class ToProcNode extends RubyContextSourceNode {

    abstract RubyNode getChildNode();

    @Specialization
    Nil doNil(Nil nil) {
        return nil;
    }

    @Specialization
    RubyProc doRubyProc(RubyProc proc) {
        return proc;
    }

    // AST-inlined version of Symbol#to_proc
    // No need to guard the refinements here since refinements are always the same in a given source location
    @Specialization(
            guards = { "isSingleContext()", "symbol == cachedSymbol" },
            assumptions = "getLanguage().coreMethodAssumptions.symbolToProcAssumption",
            limit = "1")
    Object doRubySymbolASTInlined(VirtualFrame frame, RubySymbol symbol,
            @Cached("symbol") RubySymbol cachedSymbol,
            @Cached("getProcForSymbol(getRefinements(frame), cachedSymbol)") RubyProc cachedProc) {
        return cachedProc;
    }

    @Specialization(
            guards = { "getRefinements(frame) == NO_REFINEMENTS", "symbol == cachedSymbol" },
            assumptions = "getLanguage().coreMethodAssumptions.symbolToProcAssumption",
            limit = "1")
    Object doRubySymbolASTInlined(VirtualFrame frame, RubySymbol symbol,
            @Cached("symbol") RubySymbol cachedSymbol,
            @Cached("getOrCreateCallTarget(getContext(), getLanguage(), cachedSymbol, NO_REFINEMENTS)") RootCallTarget callTarget) {
        return SymbolNodes.ToProcNode
                .createProc(getContext(), getLanguage(), DeclarationContext.NO_REFINEMENTS, callTarget);
    }

    @Specialization(guards = { "!isNil(object)", "!isRubyProc(object)" }, replaces = "doRubySymbolASTInlined")
    RubyProc doObject(VirtualFrame frame, Object object,
            @Cached DispatchNode toProc,
            @Cached InlinedBranchProfile errorProfile) {
        // The semantics are to call #method_missing here
        final Object coerced;
        try {
            coerced = toProc.callWithFrame(frame, object, "to_proc");
        } catch (RaiseException e) {
            errorProfile.enter(this);
            if (e.getException().getLogicalClass() == coreLibrary().noMethodErrorClass) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeErrorNoImplicitConversion(object, "Proc", this));
            } else {
                throw e;
            }
        }

        if (coerced instanceof RubyProc) {
            return (RubyProc) coerced;
        } else {
            errorProfile.enter(this);
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeErrorBadCoercion(object, "Proc", "to_proc", coerced, this));
        }
    }

    protected RootCallTarget getOrCreateCallTarget(RubyContext context, RubyLanguage language, RubySymbol symbol,
            Map<RubyModule, RubyModule[]> refinements) {
        return SymbolNodes.ToProcNode.getOrCreateCallTarget(getContext(), getLanguage(), symbol, refinements);
    }

    @NeverDefault
    protected RubyProc getProcForSymbol(Map<RubyModule, RubyModule[]> refinements, RubySymbol symbol) {
        final RootCallTarget callTarget = getOrCreateCallTarget(getContext(), getLanguage(), symbol, refinements);
        return SymbolNodes.ToProcNode.createProc(getContext(), getLanguage(), refinements, callTarget);
    }

    protected static Map<RubyModule, RubyModule[]> getRefinements(VirtualFrame frame) {
        return RubyArguments.getDeclarationContext(frame).getRefinements();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ToProcNodeGen.create(getChildNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
