/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.RubySymbol;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

@GenerateUncached
@GenerateCached
@GenerateInline(inlineByDefault = true)
@ImportStatic(StringOperations.class)
public abstract class ToSymbolNode extends RubyBaseNode {

    public final RubySymbol executeCached(Object object) {
        return execute(this, object);
    }

    public static RubySymbol executeUncached(Object object) {
        return ToSymbolNodeGen.getUncached().execute(null, object);
    }

    public abstract RubySymbol execute(Node node, Object object);

    @Specialization
    static RubySymbol symbol(RubySymbol symbol) {
        return symbol;
    }

    @Specialization(guards = "str == cachedStr", limit = "getCacheLimit()")
    static RubySymbol javaString(String str,
            @Cached("str") String cachedStr,
            @Cached("getSymbol(cachedStr)") RubySymbol rubySymbol) {
        return rubySymbol;
    }

    @Specialization(replaces = "javaString")
    static RubySymbol javaStringUncached(Node node, String str) {
        return getSymbol(node, str);
    }

    @Specialization(guards = "strings.isRubyString(this, str)", limit = "1")
    static RubySymbol rubyString(Node node, Object str,
            @Cached @Exclusive RubyStringLibrary strings,
            @Cached @Exclusive StringToSymbolNode stringToSymbolNode) {
        return stringToSymbolNode.execute(node, str, false);
    }

    @Specialization(guards = { "!isRubySymbol(object)", "!isString(object)", "isNotRubyString(object)" })
    static RubySymbol toStr(Node node, Object object,
            @Cached InlinedBranchProfile errorProfile,
            @Cached(inline = false) DispatchNode toStrNode,
            @Cached @Exclusive RubyStringLibrary strings,
            @Cached @Exclusive StringToSymbolNode stringToSymbolNode) {
        var coerced = toStrNode.call(
                coreLibrary(node).truffleTypeModule,
                "rb_convert_type_fallback",
                object,
                coreLibrary(node).stringClass,
                coreSymbols(node).TO_STR);

        if (strings.isRubyString(node, coerced)) {
            return stringToSymbolNode.execute(node, coerced, false);
        } else {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).typeErrorBadCoercion(
                    object,
                    "String",
                    "to_str",
                    coerced,
                    node));
        }
    }

    protected int getCacheLimit() {
        return getLanguage().options.DISPATCH_CACHE;
    }
}
