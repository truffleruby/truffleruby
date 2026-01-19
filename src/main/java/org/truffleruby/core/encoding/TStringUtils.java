/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is modified from org.jruby.runtime.encoding.EncodingService,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import org.graalvm.shadowed.org.jcodings.Encoding;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.string.CannotConvertBinaryRubyStringToJavaString;
import org.truffleruby.core.string.StringGuards;

import static com.oracle.truffle.api.strings.TruffleString.CodeRange.ASCII;

/** Helpers for {@link TruffleString} and general byte[]/java.lang.String/TruffleString conversion */
public final class TStringUtils {

    public static TruffleString fromByteArray(byte[] bytes, TruffleString.Encoding tencoding) {
        return fromByteArray(bytes, 0, bytes.length, tencoding);
    }

    public static TruffleString fromByteArray(byte[] bytes, int offset, int length, TruffleString.Encoding tencoding) {
        CompilerAsserts.neverPartOfCompilation(
                "Use createString(TruffleString.FromByteArrayNode, byte[], RubyEncoding) instead");
        return TruffleString.fromByteArrayUncached(bytes, offset, length, tencoding, false);
    }

    public static TruffleString fromByteArray(byte[] bytes, RubyEncoding rubyEncoding) {
        return fromByteArray(bytes, rubyEncoding.tencoding);
    }

    public static TruffleString utf8TString(String javaString) {
        return fromJavaString(javaString, TruffleString.Encoding.UTF_8);
    }

    public static TruffleString usAsciiString(String javaString) {
        return fromJavaString(javaString, TruffleString.Encoding.US_ASCII);
    }

    public static TruffleString fromJavaString(String javaString, TruffleString.Encoding encoding) {
        CompilerAsserts.neverPartOfCompilation(
                "Use createString(TruffleString.FromJavaStringNode, String, RubyEncoding) instead");
        return TruffleString.fromJavaStringUncached(javaString, encoding);
    }

    public static TruffleString fromJavaString(String javaString, RubyEncoding encoding) {
        return fromJavaString(javaString, encoding.tencoding);
    }

    // Should be avoided as much as feasible
    public static byte[] getBytesOrCopy(AbstractTruffleString tstring, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("uncached");
        var bytes = tstring.getInternalByteArrayUncached(encoding.tencoding);
        if (tstring.isImmutable() && bytes.getOffset() == 0 && bytes.getLength() == bytes.getArray().length) {
            return bytes.getArray();
        } else {
            return ArrayUtils.extractRange(bytes.getArray(), bytes.getOffset(), bytes.getEnd());
        }
    }

    // Should be avoided as much as feasible
    public static byte[] getBytesOrCopy(Node node, AbstractTruffleString tstring, TruffleString.Encoding encoding,
            TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
            InlinedConditionProfile noCopyProfile) {
        var bytes = getInternalByteArrayNode.execute(tstring, encoding);
        if (noCopyProfile.profile(node,
                tstring.isImmutable() && bytes.getOffset() == 0 && bytes.getLength() == bytes.getArray().length)) {
            return bytes.getArray();
        } else {
            return ArrayUtils.extractRange(bytes.getArray(), bytes.getOffset(), bytes.getEnd());
        }
    }

    private static final boolean DEBUG_NON_ZERO_OFFSET = Boolean
            .getBoolean("truffle.strings.debug-non-zero-offset-arrays");

    public static byte[] getBytesOrFail(AbstractTruffleString tstring, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("uncached");
        if (DEBUG_NON_ZERO_OFFSET) {
            return getBytesOrCopy(tstring, encoding);
        } else {
            var byteArray = tstring.getInternalByteArrayUncached(encoding.tencoding);
            if (byteArray.getOffset() != 0 || byteArray.getLength() != byteArray.getArray().length) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return byteArray.getArray();
        }
    }

    public static byte[] getBytesOrFail(AbstractTruffleString tstring, RubyEncoding encoding,
            TruffleString.GetInternalByteArrayNode byteArrayNode) {
        if (DEBUG_NON_ZERO_OFFSET) {
            return getBytesOrCopy(tstring, encoding);
        } else {
            var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);
            if (byteArray.getOffset() != 0 || byteArray.getLength() != byteArray.getArray().length) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return byteArray.getArray();
        }
    }

    public static byte[] javaStringToBytes(String value, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("uncached");
        var tstring = fromJavaString(value, encoding);
        return getBytesOrCopy(tstring, encoding);
    }

    public static String bytesToJavaStringOrThrow(byte[] bytes, int offset, int length, RubyEncoding encoding) {
        var tstring = TStringUtils.fromByteArray(bytes, offset, length, encoding.tencoding);
        return TStringUtils.toJavaStringOrThrow(tstring, encoding);
    }

    public static boolean isSingleByteOptimizable(AbstractTruffleString truffleString, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("Use SingleByteOptimizableNode instead");
        return truffleString.getByteCodeRangeUncached(encoding.tencoding) == ASCII || encoding.isSingleByte;
    }

    public static String toJavaStringOrThrow(byte[] bytes, RubyEncoding encoding) {
        return toJavaStringOrThrow(fromByteArray(bytes, encoding), encoding);
    }

    public static String toJavaStringOrThrow(AbstractTruffleString tstring, RubyEncoding encoding) {
        CompilerAsserts.neverPartOfCompilation("uncached");
        if (encoding == Encodings.BINARY && !StringGuards.is7BitUncached(tstring, encoding)) {
            int length = tstring.byteLength(encoding.tencoding);
            for (int i = 0; i < length; i++) {
                final int b = tstring.readByteUncached(i, encoding.tencoding);
                if (!Encoding.isAscii(b)) {
                    throw new CannotConvertBinaryRubyStringToJavaString(b);
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        } else {
            return tstring.toJavaStringUncached();
        }
    }

    public static boolean hasImmutableInternalByteArray(AbstractTruffleString string) {
        // Immutable strings trivially have immutable byte arrays.
        // Native strings also have immutable byte arrays because we need to copy the data into Java.
        return string.isImmutable() || string.isNative();
    }
}
