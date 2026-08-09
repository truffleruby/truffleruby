/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.StringGuards;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.library.RubyStringLibrary;

@GenerateUncached
@GenerateCached(false)
@GenerateInline
@ImportStatic({ StringGuards.class, StringOperations.class })
public abstract class StringToSymbolNode extends RubyBaseNode {

    public abstract RubySymbol execute(Node node, Object string, boolean preserveSymbol);

    @Specialization(
            guards = {
                    "equalNode.execute(node, tstring, encoding, cachedTString, cachedEncoding)",
                    "preserveSymbol == cachedPreserveSymbol",
                    "!isBrokenCodeRange(cachedTString, cachedEncoding, codeRangeNode)" },
            limit = "getDefaultCacheLimit()")
    static RubySymbol toSymbolCached(Node node, Object string, boolean preserveSymbol,
            @Cached @Exclusive RubyStringLibrary strings,
            @Cached StringHelperNodes.EqualSameEncodingNode equalNode,
            @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
            @Bind("strings.getEncoding(node, string)") RubyEncoding encoding,
            @Cached("asTruffleStringUncached(string)") TruffleString cachedTString,
            @Cached("strings.getEncoding(node, string)") RubyEncoding cachedEncoding,
            @Cached("preserveSymbol") boolean cachedPreserveSymbol,
            @Cached @Shared TruffleString.GetByteCodeRangeNode codeRangeNode,
            @Cached("getSymbol(node, cachedTString, cachedEncoding, cachedPreserveSymbol)") RubySymbol cachedSymbol) {
        return cachedSymbol;
    }

    @Specialization(replaces = "toSymbolCached")
    static RubySymbol toSymbol(Node node, Object string, boolean preserveSymbol,
            @Cached @Exclusive RubyStringLibrary strings,
            @Bind("strings.getTString(node, string)") AbstractTruffleString tstring,
            @Bind("strings.getEncoding(node, string)") RubyEncoding encoding,
            @Cached @Shared TruffleString.GetByteCodeRangeNode codeRangeNode,
            @Cached InlinedBranchProfile errorProfile) {
        if (StringGuards.isBrokenCodeRange(tstring, encoding, codeRangeNode)) {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).encodingError(string, encoding, node));
        }
        return getSymbol(node, tstring, encoding, preserveSymbol);
    }
}
