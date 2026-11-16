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

import static org.truffleruby.core.symbol.CoreSymbols.idToIndex;

import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.symbol.CoreSymbols;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/** Converts an ID (a static symbol ID or the handle of a dynamic Symbol) to its RubySymbol. There is an inline cache so
 * e.g. the method name of a rb_funcall() or the ivar name of a rb_ivar_get() is a constant in compiled code, like the
 * symmetric {@link SymbolToIDNode}. A cached RubySymbol is kept alive by the cache entry, and so is its ValueWrapper
 * for a dynamic symbol, so the cached handle cannot be freed and reused for another object. */
@GenerateUncached
public abstract class IDToSymbolNode extends RubyBaseNode {

    public abstract RubySymbol execute(long value);

    @Specialization(guards = "value == cachedValue", limit = "1")
    RubySymbol cached(long value,
            @Cached("value") long cachedValue,
            @Cached("idToSymbolUncached(cachedValue)") RubySymbol cachedSymbol) {
        return cachedSymbol;
    }

    @Specialization(guards = "isStaticSymbol(value)", replaces = "cached")
    RubySymbol unwrapStaticSymbol(long value,
            @Cached InlinedBranchProfile errorProfile) {
        final int index = idToIndex(value);
        final RubySymbol symbol = getLanguage().coreSymbols.STATIC_SYMBOLS[index];
        if (symbol == null) {
            errorProfile.enter(this);
            throw new RaiseException(
                    getContext(),
                    coreExceptions().runtimeError(
                            StringUtils.format("invalid static ID2SYM id: %d", value),
                            this));
        }
        return symbol;
    }

    @Specialization(guards = "!isStaticSymbol(value)", replaces = "cached")
    RubySymbol unwrapDynamicSymbol(long value,
            @Cached UnwrapNode unwrapNode) {
        return (RubySymbol) unwrapNode.execute(this, value);
    }

    public static boolean isStaticSymbol(long value) {
        return CoreSymbols.isStaticSymbol(value);
    }

    protected static RubySymbol idToSymbolUncached(long value) {
        return IDToSymbolNodeGen.getUncached().execute(value);
    }

}
