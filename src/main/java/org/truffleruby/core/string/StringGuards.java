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

package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Config;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.StringHelperNodes.SingleByteOptimizableNode;

import static com.oracle.truffle.api.strings.TruffleString.CodeRange.ASCII;
import static com.oracle.truffle.api.strings.TruffleString.CodeRange.BROKEN;
import static com.oracle.truffle.api.strings.TruffleString.CodeRange.VALID;

public final class StringGuards {

    private static final int CASE_FULL_UNICODE = 0;

    // Also known as isAsciiOnly()
    public static boolean is7Bit(AbstractTruffleString tstring, RubyEncoding encoding,
            TruffleString.GetByteCodeRangeNode codeRangeNode) {
        return codeRangeNode.execute(tstring, encoding.tencoding) == ASCII;
    }

    public static boolean is7BitUncached(AbstractTruffleString tstring, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.getByteCodeRangeUncached(encoding.tencoding) == ASCII;
    }

    public static boolean isValid(AbstractTruffleString tstring, RubyEncoding encoding,
            TruffleString.GetByteCodeRangeNode codeRangeNode) {
        return codeRangeNode.execute(tstring, encoding.tencoding) == VALID;
    }

    public static boolean isBrokenCodeRange(AbstractTruffleString string, RubyEncoding encoding,
            TruffleString.GetByteCodeRangeNode codeRangeNode) {
        return codeRangeNode.execute(string, encoding.tencoding) == BROKEN;
    }

    public static boolean isBrokenCodeRange(AbstractTruffleString string, TruffleString.Encoding encoding,
            TruffleString.GetByteCodeRangeNode codeRangeNode) {
        return codeRangeNode.execute(string, encoding) == BROKEN;
    }

    public static boolean isSingleByteOptimizable(Node node, AbstractTruffleString tString, RubyEncoding encoding,
            SingleByteOptimizableNode singleByteOptimizableNode) {
        return singleByteOptimizableNode.execute(node, tString, encoding);
    }

    public static boolean isAsciiCompatible(RubyEncoding encoding) {
        return encoding.isAsciiCompatible;
    }

    public static boolean isFixedWidthEncoding(RubyEncoding encoding) {
        return encoding.isFixedWidth;
    }

    /** The case mapping is simple (ASCII-only or full Unicode): no complex option like Turkic, case-folding, etc. */
    private static boolean isAsciiCompatMapping(int caseMappingOptions) {
        return caseMappingOptions == CASE_FULL_UNICODE || caseMappingOptions == Config.CASE_ASCII_ONLY;
    }

    /** The mapping is ASCII-only or effectively ASCII-only based on the string properties. */
    private static boolean isAsciiCodePointsMapping(Node node, AbstractTruffleString tstring, RubyEncoding encoding,
            int caseMappingOptions, SingleByteOptimizableNode singleByteOptimizableNode) {
        return isSingleByteOptimizable(node, tstring, encoding, singleByteOptimizableNode)
                ? isAsciiCompatMapping(caseMappingOptions)
                : caseMappingOptions == Config.CASE_ASCII_ONLY && isAsciiCompatible(encoding);
    }

    public static boolean isComplexCaseMapping(Node node, AbstractTruffleString tstring, RubyEncoding encoding,
            int caseMappingOptions, SingleByteOptimizableNode singleByteOptimizableNode) {
        return !isAsciiCodePointsMapping(node, tstring, encoding, caseMappingOptions, singleByteOptimizableNode);
    }
}
