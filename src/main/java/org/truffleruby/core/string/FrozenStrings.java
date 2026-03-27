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
package org.truffleruby.core.string;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;

import java.util.ArrayList;
import java.util.List;

public final class FrozenStrings {

    static final List<TruffleString> TSTRINGS = new ArrayList<>();

    public static final ImmutableRubyString EMPTY_US_ASCII = ImmutableStrings.createAndCacheLater(
            TStringConstants.EMPTY_US_ASCII,
            Encodings.US_ASCII);

    public static final ImmutableRubyString YIELD = createFrozenStaticBinaryString("yield");
    public static final ImmutableRubyString ASSIGNMENT = createFrozenStaticBinaryString("assignment");
    public static final ImmutableRubyString CLASS_VARIABLE = createFrozenStaticBinaryString("class variable");
    public static final ImmutableRubyString CONSTANT = createFrozenStaticBinaryString("constant");
    public static final ImmutableRubyString EXPRESSION = createFrozenStaticBinaryString("expression");
    public static final ImmutableRubyString FALSE = createFrozenStaticBinaryString("false");
    public static final ImmutableRubyString GLOBAL_VARIABLE = createFrozenStaticBinaryString("global-variable");
    public static final ImmutableRubyString INSTANCE_VARIABLE = createFrozenStaticBinaryString("instance-variable");
    public static final ImmutableRubyString LOCAL_VARIABLE = createFrozenStaticBinaryString("local-variable");
    public static final ImmutableRubyString METHOD = createFrozenStaticBinaryString("method");
    public static final ImmutableRubyString NIL = createFrozenStaticBinaryString("nil");
    public static final ImmutableRubyString SELF = createFrozenStaticBinaryString("self");
    public static final ImmutableRubyString SUPER = createFrozenStaticBinaryString("super");
    public static final ImmutableRubyString TRUE = createFrozenStaticBinaryString("true");
    public static final ImmutableRubyString TZ = createFrozenStaticBinaryString("TZ");

    private static ImmutableRubyString createFrozenStaticBinaryString(String string) {
        // defined?(...) returns frozen strings with a binary encoding
        return createFrozenStaticString(string, Encodings.BINARY);
    }

    private static ImmutableRubyString createFrozenStaticString(String string, RubyEncoding encoding) {
        // defined?(...) returns frozen strings with a binary encoding
        var tstring = TStringUtils.fromJavaString(string, encoding);
        TSTRINGS.add(tstring);
        return ImmutableStrings.createAndCacheLater(tstring, encoding);
    }

}
