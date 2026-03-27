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
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;

/** Not inlined since the usage is in {@link WriteClassVariableNode} or {@link ReadClassVariableNode} which are not DSL
 * nodes. */
@GenerateInline(false)
public abstract class ResolveTargetModuleForClassVariablesNode extends RubyBaseNode {

    public static ResolveTargetModuleForClassVariablesNode create() {
        return ResolveTargetModuleForClassVariablesNodeGen.create();
    }

    public abstract RubyModule execute(LexicalScope lexicalScope);

    @Specialization(guards = { "isSingleContext()", "lexicalScope == cachedLexicalScope" },
            limit = "getDefaultCacheLimit()")
    RubyModule cached(LexicalScope lexicalScope,
            @Cached("lexicalScope") LexicalScope cachedLexicalScope,
            @Cached("uncached(lexicalScope)") RubyModule cachedModule) {
        return cachedModule;
    }

    @Specialization(replaces = "cached")
    RubyModule uncached(LexicalScope lexicalScope) {
        LexicalScope scope = lexicalScope;

        // MRI logic: ignore lexical scopes (cref) referring to singleton classes
        while (RubyGuards.isSingletonClass(scope.getLiveModule()) || scope.ignoreForClassVariablesLookup) {
            scope = scope.getParent();
        }

        return scope.getLiveModule();
    }

}
