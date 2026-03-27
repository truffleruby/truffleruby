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

import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@ReportPolymorphism // inline cache (but not working due to single call site in C)
public abstract class SymbolToIDNode extends RubyBaseNode {

    public abstract Object execute(Object value);

    @Specialization(guards = "symbol == cachedSymbol", limit = "2")
    Object getIDCached(RubySymbol symbol,
            @Cached @Shared WrapNode wrapNode,
            @Cached("symbol") RubySymbol cachedSymbol,
            @Cached("getID(cachedSymbol, wrapNode)") Object cachedID) {
        return cachedID;
    }

    @Specialization(replaces = "getIDCached")
    Object getIDUncached(RubySymbol symbol,
            @Cached @Shared WrapNode wrapNode,
            @Cached InlinedConditionProfile staticSymbolProfile) {
        if (staticSymbolProfile.profile(this, symbol.getId() != RubySymbol.UNASSIGNED_ID)) {
            return symbol.getId();
        }
        return wrapNode.execute(symbol);
    }

    protected Object getID(RubySymbol symbol,
            WrapNode wrapNode) {
        if (symbol.getId() != RubySymbol.UNASSIGNED_ID) {
            return symbol.getId();
        }
        return wrapNode.execute(symbol);
    }
}
