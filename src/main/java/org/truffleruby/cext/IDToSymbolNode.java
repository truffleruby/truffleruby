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

@GenerateUncached
public abstract class IDToSymbolNode extends RubyBaseNode {

    public abstract RubySymbol execute(Object value);

    @Specialization(guards = "isStaticSymbol(value)")
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

    @Specialization(guards = "!isStaticSymbol(value)")
    RubySymbol unwrapDynamicSymbol(Object value,
            @Cached UnwrapNode unwrapNode) {
        return (RubySymbol) unwrapNode.execute(this, value);
    }

    public static boolean isStaticSymbol(Object value) {
        if (!(value instanceof Long)) {
            return false;
        }
        return CoreSymbols.isStaticSymbol((long) value);
    }

}
