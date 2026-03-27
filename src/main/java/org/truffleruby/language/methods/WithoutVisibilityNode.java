/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateCached(false)
@GenerateInline
public abstract class WithoutVisibilityNode extends RubyBaseNode {

    public abstract DeclarationContext executeWithoutVisibility(Node node, DeclarationContext declarationContext);

    @Specialization(guards = { "isSingleContext()", "declarationContext == cachedContext" },
            limit = "getDefaultCacheLimit()")
    static DeclarationContext cached(DeclarationContext declarationContext,
            @Cached("declarationContext") DeclarationContext cachedContext,
            @Cached("uncached(cachedContext)") DeclarationContext without) {
        return without;
    }

    @Specialization(replaces = "cached")
    static DeclarationContext uncached(DeclarationContext declarationContext) {
        return declarationContext.withVisibility(null);
    }

}
