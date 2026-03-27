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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;

/** Define a method from a module body (module/class/class << self ... end). */
public final class ModuleBodyDefinition {

    private final String name;
    private final SharedMethodInfo sharedMethodInfo;
    private final RootCallTarget callTarget;

    private final LexicalScope staticLexicalScope;
    private final Map<RubyModule, LexicalScope> dynamicLexicalScopes;

    public ModuleBodyDefinition(
            String name,
            SharedMethodInfo sharedMethodInfo,
            RootCallTarget callTarget,
            LexicalScope staticLexicalScope) {
        this.name = name;
        this.sharedMethodInfo = sharedMethodInfo;
        this.callTarget = callTarget;
        this.staticLexicalScope = staticLexicalScope;
        this.dynamicLexicalScopes = staticLexicalScope != null ? null : new ConcurrentHashMap<>();
    }

    public InternalMethod createMethod(VirtualFrame frame, RubyModule module, RubyBaseNode node) {
        final LexicalScope parentLexicalScope = RubyArguments.getMethod(frame).getLexicalScope();
        final LexicalScope lexicalScope = prepareLexicalScope(staticLexicalScope, parentLexicalScope, module, node);
        final DeclarationContext declarationContext = new DeclarationContext(
                Visibility.PUBLIC,
                new DeclarationContext.FixedDefaultDefinee(module),
                RubyArguments.getDeclarationContext(frame).getRefinements());
        return new InternalMethod(
                RubyContext.get(node),
                sharedMethodInfo,
                lexicalScope,
                declarationContext,
                name,
                module,
                Visibility.PUBLIC,
                false,
                null,
                null,
                callTarget,
                null);
    }

    @TruffleBoundary
    private LexicalScope prepareLexicalScope(LexicalScope staticLexicalScope, LexicalScope parentLexicalScope,
            RubyModule module, RubyBaseNode node) {
        if (staticLexicalScope != null) {
            staticLexicalScope.unsafeSetLiveModule(module);
            return staticLexicalScope;
        } else if (RubyLanguage.get(node).singleContext) {
            // Cache the scope per module in case the module body is run multiple times.
            // This allows dynamic constant lookup to cache better.
            return ConcurrentOperations.getOrCompute(
                    dynamicLexicalScopes,
                    module,
                    k -> new LexicalScope(parentLexicalScope, module));
        } else {
            return new LexicalScope(parentLexicalScope, module);
        }
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    @Override
    public String toString() {
        return StringUtils.format(
                "ModuleBodyDefinition(name = %s, sharedMethodInfo = %s, callTarget = %s, staticLexicalScope = %s, dynamicLexicalScopes = %s)",
                this.name,
                this.sharedMethodInfo.toStringWithDetails(),
                this.callTarget,
                this.staticLexicalScope,
                this.dynamicLexicalScopes);
    }

}
