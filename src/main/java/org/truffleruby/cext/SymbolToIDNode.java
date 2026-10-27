/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import org.truffleruby.cext.ValueWrapperManager.WrapperToHandleNode;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/** Converts a Symbol to its ID: the static ID for static symbols, and the handle of the ValueWrapper for dynamic
 * symbols. */
@GenerateUncached
@ReportPolymorphism // inline cache (but not working due to single call site in C)
public abstract class SymbolToIDNode extends RubyBaseNode {

    public abstract long execute(Object value);

    @Specialization(guards = { "symbol == cachedSymbol", "isStaticSymbol(cachedSymbol)" }, limit = "2")
    long getStaticIDCached(RubySymbol symbol,
            @Cached("symbol") RubySymbol cachedSymbol,
            @Cached("cachedSymbol.getId()") long cachedID) {
        return cachedID;
    }

    @Specialization(guards = "isStaticSymbol(symbol)", replaces = "getStaticIDCached")
    long getStaticID(RubySymbol symbol) {
        return symbol.getId();
    }

    /** Dynamic symbols are only weakly referenced by the SymbolTable, so this goes through {@link WrapperToHandleNode}
     * on every call to keep the wrapper (and the symbol) alive until the end of the current C extension call. */
    @Specialization(guards = "!isStaticSymbol(symbol)")
    static long getDynamicID(RubySymbol symbol,
            @Bind Node node,
            @Cached WrapNode wrapNode,
            @Cached WrapperToHandleNode wrapperToHandleNode) {
        return wrapperToHandleNode.execute(node, wrapNode.execute(symbol));
    }

    @Idempotent
    protected static boolean isStaticSymbol(RubySymbol symbol) {
        return symbol.getId() != RubySymbol.UNASSIGNED_ID;
    }
}
