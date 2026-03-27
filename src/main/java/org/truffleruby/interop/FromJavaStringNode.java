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
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
@ReportPolymorphism // inline cache
public abstract class FromJavaStringNode extends RubyBaseNode {

    public abstract RubyString executeFromJavaString(Node node, String value);

    public static RubyString executeUncached(String value) {
        return FromJavaStringNodeGen.getUncached().executeFromJavaString(null, value);
    }

    @Specialization(guards = "stringsEquals(cachedValue, value)", limit = "getLimit()")
    static RubyString doCached(Node node, String value,
            @Cached("value") String cachedValue,
            @Cached(inline = false) @Shared TruffleString.FromJavaStringNode tstringFromJavaStringNode,
            @Cached("getTString(cachedValue, tstringFromJavaStringNode)") TruffleString cachedTString) {
        var rubyString = createString(node, cachedTString, Encodings.UTF_8);
        rubyString.freeze();
        return rubyString;
    }

    @Specialization(replaces = "doCached")
    static RubyString doGeneric(Node node, String value,
            @Cached(inline = false) @Shared TruffleString.FromJavaStringNode tstringFromJavaStringNode) {
        var rubyString = createString(node, tstringFromJavaStringNode, value, Encodings.UTF_8);
        rubyString.freeze();
        return rubyString;
    }

    protected static boolean stringsEquals(String a, String b) {
        return a.equals(b);
    }

    protected static TruffleString getTString(String value,
            TruffleString.FromJavaStringNode tstringFromJavaStringNode) {
        return tstringFromJavaStringNode.execute(value, TruffleString.Encoding.UTF_8);
    }

    protected int getLimit() {
        return getLanguage().options.INTEROP_CONVERT_CACHE;
    }

}
