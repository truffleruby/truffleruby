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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.EncodingDB;
import org.graalvm.shadowed.org.jcodings.specific.ASCIIEncoding;
import org.graalvm.shadowed.org.jcodings.specific.ISO8859_1Encoding;
import org.graalvm.shadowed.org.jcodings.specific.USASCIIEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF16BEEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF16LEEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF32BEEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF32LEEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF8Encoding;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.string.ImmutableStrings;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.TStringConstants;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class Encodings {

    /** We must load all initial encodings in EncodingList order before accessing any specific one like
     * {@link #DUMMY_JCODING} to get the expected {@link Encoding#getIndex() index} for the initial encodings. */
    public static final int INITIAL_NUMBER_OF_ENCODINGS = loadAllInitialEncodingsAndReturnSize();
    /** We choose UTF-7 as the dummy jcoding for rb_define_dummy_encoding() encodings and dummy UTF-16 and UTF-32. */
    private static final Encoding DUMMY_JCODING = EncodingDB.getEncodings()
            .get(StringOperations.encodeAsciiBytes("UTF-7")).getEncoding();
    /** This is NOT used for the UTF-16 RubyEncoding, we only reference it to get the proper jcoding index. */
    static final Encoding DUMMY_UTF16_JCODING = EncodingDB.getEncodings()
            .get(StringOperations.encodeAsciiBytes("UTF-16")).getEncoding();
    /** This is NOT used for the UTF-32 RubyEncoding, we only reference it to get the proper jcoding index. */
    static final Encoding DUMMY_UTF32_JCODING = EncodingDB.getEncodings()
            .get(StringOperations.encodeAsciiBytes("UTF-32")).getEncoding();

    static final int MAX_NUMBER_OF_ENCODINGS = 256;
    private static final int US_ASCII_INDEX = getUsAsciiIndex();
    public static final RubyEncoding US_ASCII = new RubyEncoding(US_ASCII_INDEX);
    static final RubyEncoding[] BUILT_IN_ENCODINGS = initializeRubyEncodings();
    private static final RubyEncoding[] BUILT_IN_ENCODINGS_BY_JCODING_INDEX = initializeBuiltinEncodingsByJCodingIndex();

    public static final RubyEncoding BINARY = getBuiltInEncoding(ASCIIEncoding.INSTANCE);
    public static final RubyEncoding UTF_8 = getBuiltInEncoding(UTF8Encoding.INSTANCE);
    public static final RubyEncoding UTF16LE = getBuiltInEncoding(UTF16LEEncoding.INSTANCE);
    public static final RubyEncoding UTF16BE = getBuiltInEncoding(UTF16BEEncoding.INSTANCE);
    public static final RubyEncoding UTF32LE = getBuiltInEncoding(UTF32LEEncoding.INSTANCE);
    public static final RubyEncoding UTF32BE = getBuiltInEncoding(UTF32BEEncoding.INSTANCE);
    public static final RubyEncoding ISO_8859_1 = getBuiltInEncoding(ISO8859_1Encoding.INSTANCE);
    public static final RubyEncoding UTF16_DUMMY = getBuiltInEncoding(DUMMY_UTF16_JCODING);
    public static final RubyEncoding UTF32_DUMMY = getBuiltInEncoding(DUMMY_UTF32_JCODING);

    @CompilationFinal(dimensions = 1) public static final RubyEncoding[] STANDARD_ENCODINGS = new RubyEncoding[3];
    static {
        if (Encodings.BINARY.index >= 3 || Encodings.UTF_8.index >= 3 || Encodings.US_ASCII.index >= 3) {
            throw new Error("Expected standard encoding indices to be 0, 1 or 2");
        }

        STANDARD_ENCODINGS[BINARY.index] = BINARY;
        STANDARD_ENCODINGS[UTF_8.index] = UTF_8;
        STANDARD_ENCODINGS[US_ASCII.index] = US_ASCII;
    }
    public static final int NUMBER_OF_STANDARD_ENCODINGS = STANDARD_ENCODINGS.length;

    /** On Linux and macOS the filesystem encoding is always UTF-8 */
    public static final RubyEncoding FILESYSTEM = UTF_8;
    public static final Charset FILESYSTEM_CHARSET = StandardCharsets.UTF_8;

    public Encodings() {
    }

    private static int loadAllInitialEncodingsAndReturnSize() {
        for (var entry : EncodingDB.getEncodings()) {
            entry.getEncoding(); // causes classloading and initializing the index of the Encoding
        }
        return EncodingDB.getEncodings().size();
    }

    /** Indicates whether the encoding is one of the runtime-default encodings. Many (most?) applications do not
     * override the default encodings and as such, this set of encodings is used very frequently in real-world Ruby
     * applications. */
    @Idempotent
    public static boolean isStandardEncoding(RubyEncoding encoding) {
        return encoding.index < NUMBER_OF_STANDARD_ENCODINGS;
    }

    @TruffleBoundary
    public static RubyEncoding newDummyEncoding(RubyLanguage language, int index, String name) {
        if (!StringOperations.isAsciiOnly(name)) {
            throw CompilerDirectives
                    .shouldNotReachHere("Encoding name contained non ascii characters \"" + name + "\"");
        }

        var tstring = TStringUtils.fromJavaString(name, Encodings.US_ASCII);
        final ImmutableRubyString string = language.getImmutableString(tstring, Encodings.US_ASCII);

        return new RubyEncoding(Encodings.DUMMY_JCODING, string, index);
    }

    private static int getUsAsciiIndex() {
        int index = 0;
        for (var entry : EncodingDB.getEncodings()) {
            if (entry.getEncoding() == USASCIIEncoding.INSTANCE) {
                return index;
            }
            index++;
        }
        throw CompilerDirectives.shouldNotReachHere("No US-ASCII");
    }

    private static RubyEncoding[] initializeRubyEncodings() {
        final RubyEncoding[] encodings = new RubyEncoding[INITIAL_NUMBER_OF_ENCODINGS];

        int index = 0;
        for (var entry : EncodingDB.getEncodings()) {
            final Encoding encoding = entry.getEncoding();

            final RubyEncoding rubyEncoding;
            if (encoding == USASCIIEncoding.INSTANCE) {
                assert index == US_ASCII_INDEX;
                rubyEncoding = US_ASCII;
            } else {
                TruffleString tstring = TStringConstants.TSTRING_CONSTANTS.get(encoding.toString());
                if (tstring == null) {
                    throw CompilerDirectives.shouldNotReachHere("no TStringConstants for " + encoding);
                }
                final ImmutableRubyString name = ImmutableStrings.createAndCacheLater(tstring, US_ASCII);

                Encoding jcodingToUse = encoding;
                if (encoding == DUMMY_UTF16_JCODING || encoding == DUMMY_UTF32_JCODING) { // Those are regular dummy encodings in Ruby 3.2+
                    jcodingToUse = DUMMY_JCODING;
                }
                rubyEncoding = new RubyEncoding(jcodingToUse, name, index);
            }
            encodings[index] = rubyEncoding;

            index++;
        }

        assert index == EncodingDB.getEncodings().size();
        return encodings;
    }

    public static RubyEncoding[] initializeBuiltinEncodingsByJCodingIndex() {
        final RubyEncoding[] encodings = new RubyEncoding[INITIAL_NUMBER_OF_ENCODINGS];
        for (RubyEncoding encoding : BUILT_IN_ENCODINGS) {
            // This and the usage in getBuiltInEncoding() below should be the only usages of org.jcodings.Encoding#getIndex().
            // That index is not deterministic and depends on classloading, so use it as little as possible.
            int jcodingIndex = encoding.jcoding.getIndex();
            if (encoding.toString().equals("UTF-16")) {
                jcodingIndex = DUMMY_UTF16_JCODING.getIndex();
            } else if (encoding.toString().equals("UTF-32")) {
                jcodingIndex = DUMMY_UTF32_JCODING.getIndex();
            }
            encodings[jcodingIndex] = encoding;
        }
        assert ArrayUtils.assertValidElements(encodings, 0, encodings.length);
        return encodings;
    }

    public static Encoding getTranscodingJCoding(RubyEncoding encoding) {
        if (encoding == UTF16_DUMMY) {
            return DUMMY_UTF16_JCODING;
        } else if (encoding == UTF32_DUMMY) {
            return DUMMY_UTF32_JCODING;
        } else {
            return encoding.jcoding;
        }
    }

    /** Should only be used when there is no other way, because this will resolve most dummy encodings to UTF-7 (see
     * {@link #DUMMY_JCODING}) and so get the wrong name. */
    public static RubyEncoding getBuiltInEncoding(Encoding jcoding) {
        var rubyEncoding = BUILT_IN_ENCODINGS_BY_JCODING_INDEX[jcoding.getIndex()];
        assert rubyEncoding.jcoding == jcoding || rubyEncoding.toString().equals("UTF-16") ||
                rubyEncoding.toString().equals("UTF-32");
        return rubyEncoding;
    }

    @TruffleBoundary
    public static RubyEncoding getBuiltInEncoding(String name) {
        byte[] nameBytes = StringOperations.encodeAsciiBytes(name);
        EncodingDB.Entry entry = EncodingDB.getEncodings().get(nameBytes);

        if (entry == null) {
            entry = EncodingDB.getAliases().get(nameBytes);
        }

        if (entry != null) {
            return getBuiltInEncoding(entry.getEncoding());
        }

        return null;
    }

}
