/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.TStringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// Must use TruffleString.Encoding and not RubyEncoding to avoid initialization cycle
public final class TStringConstants {
    public static final Map<String, TruffleString> TSTRING_CONSTANTS = new HashMap<>();

    public static final TruffleString EMPTY_BINARY = TruffleString.Encoding.BYTES.getEmpty();
    public static final TruffleString EMPTY_US_ASCII = TruffleString.Encoding.US_ASCII.getEmpty();
    public static final TruffleString EMPTY_UTF8 = TruffleString.Encoding.UTF_8.getEmpty();

    @CompilationFinal(dimensions = 1) public static final byte[] EMPTY_BYTES = new byte[0];
    @CompilationFinal(dimensions = 1) public static final byte[] NEWLINE_BYTE_ARRAY = new byte[]{ '\n' };

    @CompilationFinal(dimensions = 1) public static final TruffleString[] UTF8_SINGLE_BYTE = new TruffleString[256];
    @CompilationFinal(dimensions = 1) public static final TruffleString[] US_ASCII_SINGLE_BYTE = new TruffleString[256];
    @CompilationFinal(dimensions = 1) public static final TruffleString[] BINARY_SINGLE_BYTE = new TruffleString[256];
    @CompilationFinal(dimensions = 1) private static final TruffleString[] PADDED_NUMBERS = createPaddedNumbersTable();
    @CompilationFinal(dimensions = 1) private static final TruffleString[] PADDING_ZEROS = createPaddingZeroTable();

    static {
        for (int i = 0; i < 256; i++) {
            final byte[] bytes = new byte[]{ (byte) i };
            UTF8_SINGLE_BYTE[i] = withHashCode(TStringUtils.fromByteArray(bytes, TruffleString.Encoding.UTF_8));
            US_ASCII_SINGLE_BYTE[i] = withHashCode(TStringUtils.fromByteArray(bytes, TruffleString.Encoding.US_ASCII));
            BINARY_SINGLE_BYTE[i] = withHashCode(TStringUtils.fromByteArray(bytes, TruffleString.Encoding.BYTES));
        }
    }


    public static final TruffleString __FILE__ = ascii("__FILE__");
    public static final TruffleString __LINE__ = ascii("__LINE__");
    public static final TruffleString __ENCODING__ = ascii("__ENCODING__");
    public static final TruffleString AMPERSAND = ascii("&");
    public static final TruffleString AMPERSAND_AMPERSAND = ascii("&&");
    public static final TruffleString AMPERSAND_DOT = ascii("&.");
    public static final TruffleString BACKTICK = ascii("`");
    public static final TruffleString BACKSLASH = ascii("\\");
    public static final TruffleString BANG = ascii("!");
    public static final TruffleString BANG_EQ = ascii("!=");
    public static final TruffleString BANG_TILDE = ascii("!~");
    public static final TruffleString CALL = ascii("call");
    public static final TruffleString CARET = ascii("^");
    public static final TruffleString COLON = ascii(":");
    public static final TruffleString COLON_COLON = ascii("::");
    public static final TruffleString COMMA = ascii(",");
    public static final TruffleString DOT = ascii(".");
    public static final TruffleString DOT_DOT = ascii("..");
    public static final TruffleString DOT_DOT_DOT = ascii("...");
    public static final TruffleString DOLLAR_BANG = ascii("$!");
    public static final TruffleString DOLLAR_ZERO = ascii("$0");
    public static final TruffleString EQ = ascii("=");
    public static final TruffleString EQ_EQ = ascii("==");
    public static final TruffleString EQ_EQ_EQ = ascii("===");
    public static final TruffleString EQ_GT = ascii("=>");
    public static final TruffleString EQ_TILDE = ascii("=~");
    public static final TruffleString FALSE = ascii("false");
    public static final TruffleString GT = ascii(">");
    public static final TruffleString GT_EQ = ascii(">=");
    public static final TruffleString GT_GT = ascii(">>");
    public static final TruffleString LBRACKET = ascii("[");
    public static final TruffleString LBRACKET_RBRACKET = ascii("[]");
    public static final TruffleString LBRACKET_RBRACKET_EQ = ascii("[]=");
    public static final TruffleString LCURLY = ascii("{");
    public static final TruffleString LT = ascii("<");
    public static final TruffleString LT_EQ = ascii("<=");
    public static final TruffleString LT_EQ_GT = ascii("<=>");
    public static final TruffleString LT_LT = ascii("<<");
    public static final TruffleString MINUS = ascii("-");
    public static final TruffleString MINUS_AT = ascii("-@");
    public static final TruffleString MINUS_GT = ascii("->");
    public static final TruffleString NIL = ascii("nil");
    public static final TruffleString OR = ascii("|");
    public static final TruffleString OR_OR = ascii("||");
    public static final TruffleString PERCENT = ascii("%");
    public static final TruffleString PLUS = ascii("+");
    public static final TruffleString PLUS_AT = ascii("+@");
    public static final TruffleString Q = ascii("'");
    public static final TruffleString QQ = ascii("\"");
    public static final TruffleString QUESTION = ascii("?");
    public static final TruffleString RBRACKET = ascii("]");
    public static final TruffleString RCURLY = ascii("}");
    public static final TruffleString RPAREN = ascii(")");
    public static final TruffleString SELF = ascii("self");
    public static final TruffleString SEMICOLON = ascii(";");
    public static final TruffleString SLASH = ascii("/");
    public static final TruffleString STAR = ascii("*");
    public static final TruffleString STAR_STAR = ascii("**");
    public static final TruffleString TILDE = ascii("~");
    public static final TruffleString TRUE = ascii("true");
    // Encoding names, generated by:
    // names = Encoding.list.map { |e| e.name }
    // names.each { |n| puts "public static final TruffleString #{n.upcase.gsub('-','_')} = ascii(\"#{n}\");" }
    public static final TruffleString ASCII_8BIT = ascii("ASCII-8BIT");
    public static final TruffleString US_ASCII = ascii("US-ASCII");
    public static final TruffleString UTF_8 = ascii("UTF-8");
    public static final TruffleString BIG5 = ascii("Big5");
    public static final TruffleString BIG5_HKSCS = ascii("Big5-HKSCS");
    public static final TruffleString BIG5_UAO = ascii("Big5-UAO");
    public static final TruffleString CP949 = ascii("CP949");
    public static final TruffleString EMACS_MULE = ascii("Emacs-Mule");
    public static final TruffleString EUC_JP = ascii("EUC-JP");
    public static final TruffleString EUC_KR = ascii("EUC-KR");
    public static final TruffleString EUC_TW = ascii("EUC-TW");
    public static final TruffleString GB18030 = ascii("GB18030");
    public static final TruffleString GBK = ascii("GBK");
    public static final TruffleString ISO_8859_1 = ascii("ISO-8859-1");
    public static final TruffleString ISO_8859_2 = ascii("ISO-8859-2");
    public static final TruffleString ISO_8859_3 = ascii("ISO-8859-3");
    public static final TruffleString ISO_8859_4 = ascii("ISO-8859-4");
    public static final TruffleString ISO_8859_5 = ascii("ISO-8859-5");
    public static final TruffleString ISO_8859_6 = ascii("ISO-8859-6");
    public static final TruffleString ISO_8859_7 = ascii("ISO-8859-7");
    public static final TruffleString ISO_8859_8 = ascii("ISO-8859-8");
    public static final TruffleString ISO_8859_9 = ascii("ISO-8859-9");
    public static final TruffleString ISO_8859_10 = ascii("ISO-8859-10");
    public static final TruffleString ISO_8859_11 = ascii("ISO-8859-11");
    public static final TruffleString ISO_8859_13 = ascii("ISO-8859-13");
    public static final TruffleString ISO_8859_14 = ascii("ISO-8859-14");
    public static final TruffleString ISO_8859_15 = ascii("ISO-8859-15");
    public static final TruffleString ISO_8859_16 = ascii("ISO-8859-16");
    public static final TruffleString KOI8_R = ascii("KOI8-R");
    public static final TruffleString KOI8_U = ascii("KOI8-U");
    public static final TruffleString SHIFT_JIS = ascii("Shift_JIS");
    public static final TruffleString UTF_16BE = ascii("UTF-16BE");
    public static final TruffleString UTF_16LE = ascii("UTF-16LE");
    public static final TruffleString UTF_32BE = ascii("UTF-32BE");
    public static final TruffleString UTF_32LE = ascii("UTF-32LE");
    public static final TruffleString WINDOWS_31J = ascii("Windows-31J");
    public static final TruffleString WINDOWS_1250 = ascii("Windows-1250");
    public static final TruffleString WINDOWS_1251 = ascii("Windows-1251");
    public static final TruffleString WINDOWS_1252 = ascii("Windows-1252");
    public static final TruffleString WINDOWS_1253 = ascii("Windows-1253");
    public static final TruffleString WINDOWS_1254 = ascii("Windows-1254");
    public static final TruffleString WINDOWS_1257 = ascii("Windows-1257");
    public static final TruffleString IBM437 = ascii("IBM437");
    public static final TruffleString IBM737 = ascii("IBM737");
    public static final TruffleString IBM775 = ascii("IBM775");
    public static final TruffleString CP850 = ascii("CP850");
    public static final TruffleString IBM852 = ascii("IBM852");
    public static final TruffleString CP852 = ascii("CP852");
    public static final TruffleString IBM855 = ascii("IBM855");
    public static final TruffleString CP855 = ascii("CP855");
    public static final TruffleString IBM857 = ascii("IBM857");
    public static final TruffleString IBM860 = ascii("IBM860");
    public static final TruffleString IBM861 = ascii("IBM861");
    public static final TruffleString IBM862 = ascii("IBM862");
    public static final TruffleString IBM863 = ascii("IBM863");
    public static final TruffleString IBM864 = ascii("IBM864");
    public static final TruffleString IBM865 = ascii("IBM865");
    public static final TruffleString IBM866 = ascii("IBM866");
    public static final TruffleString IBM869 = ascii("IBM869");
    public static final TruffleString WINDOWS_1258 = ascii("Windows-1258");
    public static final TruffleString GB1988 = ascii("GB1988");
    public static final TruffleString MACCENTEURO = ascii("macCentEuro");
    public static final TruffleString MACCROATIAN = ascii("macCroatian");
    public static final TruffleString MACCYRILLIC = ascii("macCyrillic");
    public static final TruffleString MACGREEK = ascii("macGreek");
    public static final TruffleString MACICELAND = ascii("macIceland");
    public static final TruffleString MACROMAN = ascii("macRoman");
    public static final TruffleString MACROMANIA = ascii("macRomania");
    public static final TruffleString MACTHAI = ascii("macThai");
    public static final TruffleString MACTURKISH = ascii("macTurkish");
    public static final TruffleString MACUKRAINE = ascii("macUkraine");
    public static final TruffleString CP950 = ascii("CP950");
    public static final TruffleString CP951 = ascii("CP951");
    public static final TruffleString IBM037 = ascii("IBM037");
    public static final TruffleString STATELESS_ISO_2022_JP = ascii("stateless-ISO-2022-JP");
    public static final TruffleString EUCJP_MS = ascii("eucJP-ms");
    public static final TruffleString CP51932 = ascii("CP51932");
    public static final TruffleString EUC_JIS_2004 = ascii("EUC-JIS-2004");
    public static final TruffleString GB2312 = ascii("GB2312");
    public static final TruffleString GB12345 = ascii("GB12345");
    public static final TruffleString ISO_2022_JP = ascii("ISO-2022-JP");
    public static final TruffleString ISO_2022_JP_2 = ascii("ISO-2022-JP-2");
    public static final TruffleString CP50220 = ascii("CP50220");
    public static final TruffleString CP50221 = ascii("CP50221");
    public static final TruffleString WINDOWS_1256 = ascii("Windows-1256");
    public static final TruffleString WINDOWS_1255 = ascii("Windows-1255");
    public static final TruffleString TIS_620 = ascii("TIS-620");
    public static final TruffleString WINDOWS_874 = ascii("Windows-874");
    public static final TruffleString MACJAPANESE = ascii("MacJapanese");
    public static final TruffleString UTF_7 = ascii("UTF-7");
    public static final TruffleString UTF8_MAC = ascii("UTF8-MAC");
    public static final TruffleString UTF_16 = ascii("UTF-16");
    public static final TruffleString UTF_32 = ascii("UTF-32");
    public static final TruffleString UTF8_DOCOMO = ascii("UTF8-DoCoMo");
    public static final TruffleString SJIS_DOCOMO = ascii("SJIS-DoCoMo");
    public static final TruffleString UTF8_KDDI = ascii("UTF8-KDDI");
    public static final TruffleString SJIS_KDDI = ascii("SJIS-KDDI");
    public static final TruffleString ISO_2022_JP_KDDI = ascii("ISO-2022-JP-KDDI");
    public static final TruffleString STATELESS_ISO_2022_JP_KDDI = ascii("stateless-ISO-2022-JP-KDDI");
    public static final TruffleString UTF8_SOFTBANK = ascii("UTF8-SoftBank");
    public static final TruffleString SJIS_SOFTBANK = ascii("SJIS-SoftBank");
    public static final TruffleString IBM720 = ascii("IBM720");
    public static final TruffleString CESU_8 = ascii("CESU-8");

    private static TruffleString ascii(String string) {
        if (string.length() == 1) {
            return US_ASCII_SINGLE_BYTE[string.charAt(0)];
        } else {
            final TruffleString tstring = TStringUtils.fromJavaString(string, TruffleString.Encoding.US_ASCII);
            var before = TSTRING_CONSTANTS.putIfAbsent(string, tstring);

            if (before != null) {
                throw new AssertionError("Duplicate TruffleString in TStringConstants: " + before);
            }

            return tstring;
        }
    }

    public static TruffleString lookupUSASCIITString(String string) {
        if (string.isEmpty()) {
            return EMPTY_US_ASCII;
        } else if (string.length() == 1) {
            return US_ASCII_SINGLE_BYTE[string.charAt(0)];
        } else {
            return TSTRING_CONSTANTS.get(string);
        }
    }

    private static TruffleString[] createPaddedNumbersTable() {
        final TruffleString[] table = new TruffleString[100];

        for (int n = 0; n < table.length; n++) {
            table[n] = TruffleString.fromByteArrayUncached(
                    new byte[]{ (byte) ('0' + n / 10), (byte) ('0' + n % 10) },
                    TruffleString.Encoding.UTF_8,
                    false);
        }

        return table;
    }

    /*** Zero-padded numbers in the format %02d, between 00 and 99. */
    public static TruffleString paddedNumber(int n) {
        return PADDED_NUMBERS[n];
    }

    private static TruffleString[] createPaddingZeroTable() {
        final TruffleString[] table = new TruffleString[6];

        for (int n = 0; n < table.length; n++) {
            final byte[] bytes = new byte[n];

            Arrays.fill(bytes, (byte) '0');

            table[n] = TruffleString.fromByteArrayUncached(bytes, TruffleString.Encoding.UTF_8, false);
        }

        return table;
    }

    public static TruffleString paddingZeros(int n) {
        return PADDING_ZEROS[n];
    }

    private static <T> T withHashCode(T object) {
        object.hashCode();
        return object;
    }
}
