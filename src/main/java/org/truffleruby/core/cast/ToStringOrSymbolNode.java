/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/** Convert objects to a String by calling #to_str, but leave existing Strings or Symbols as they are. */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class ToStringOrSymbolNode extends RubyBaseNode {

    public abstract Object execute(Node node, Object value);

    @Specialization
    static RubySymbol coerceRubySymbol(RubySymbol symbol) {
        return symbol;
    }

    @Specialization
    static RubyString coerceRubyString(RubyString string) {
        return string;
    }

    @Specialization
    static ImmutableRubyString coerceRubyString(ImmutableRubyString string) {
        return string;
    }

    @Specialization(guards = { "!isRubySymbol(object)", "isNotRubyString(object)" })
    static Object coerceObject(Node node, Object object,
            @Cached(inline = false) DispatchNode toStrNode) {
        return toStrNode.call(
                coreLibrary(node).truffleTypeModule,
                "rb_convert_type_fallback",
                object,
                coreLibrary(node).stringClass,
                coreSymbols(node).TO_STR);
    }
}
