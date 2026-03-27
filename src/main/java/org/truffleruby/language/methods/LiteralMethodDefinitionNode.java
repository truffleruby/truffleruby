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
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Set;

/** Define a method from a method literal (def mymethod ... end). That is, create an InternalMethod and add it to the
 * current module (default definee). */
public final class LiteralMethodDefinitionNode extends RubyContextSourceNode {

    private final String name;
    private final SharedMethodInfo sharedMethodInfo;
    private final boolean isDefSingleton;
    private final CachedLazyCallTargetSupplier callTargetSupplier;

    @Child private RubyNode moduleNode;

    public LiteralMethodDefinitionNode(
            RubyNode moduleNode,
            String name,
            SharedMethodInfo sharedMethodInfo,
            boolean isDefSingleton,
            CachedLazyCallTargetSupplier callTargetSupplier) {
        this.name = name;
        this.sharedMethodInfo = sharedMethodInfo;
        this.isDefSingleton = isDefSingleton;
        this.callTargetSupplier = callTargetSupplier;
        this.moduleNode = moduleNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyModule module = moduleNode == null
                ? RubyArguments.getDeclarationContext(frame).getModuleToDefineMethods()
                : (RubyModule) moduleNode.execute(frame);

        final Visibility visibility;
        if (isDefSingleton) {
            visibility = Visibility.PUBLIC;
        } else {
            visibility = DeclarationContext.findVisibility(frame);
        }

        final InternalMethod currentMethod = RubyArguments.getMethod(frame);
        final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(frame);

        return addMethod(module, visibility, currentMethod, declarationContext);
    }

    @TruffleBoundary
    private Object addMethod(RubyModule module, Visibility visibility, InternalMethod currentMethod,
            DeclarationContext declarationContext) {
        final InternalMethod method = new InternalMethod(
                getContext(),
                sharedMethodInfo,
                currentMethod.getLexicalScope(),
                declarationContext.withVisibility(Visibility.PUBLIC),
                name,
                module,
                visibility,
                false,
                null,
                null,
                null,
                callTargetSupplier);

        if (isDefSingleton) {
            module.addMethodIgnoreNameVisibility(getContext(), method, visibility, this);
        } else {
            module.addMethodConsiderNameVisibility(getContext(), method, visibility, this);
        }

        return getSymbol(name);
    }

    /** When the debugger is searching for a node at a given file:line to put a breakpoint, it needs to look at nodes of
     * the method CallTarget, therefore we compute the method CallTarget here when instrumentation requests it. */
    @Override
    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        if (materializedTags.contains(StandardTags.StatementTag.class)) {
            callTargetSupplier.get(); // force computation of the call target
        }
        return this;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new LiteralMethodDefinitionNode(
                cloneUninitialized(moduleNode),
                name,
                sharedMethodInfo,
                isDefSingleton,
                callTargetSupplier);
        return copy.copyFlags(this);
    }

}
