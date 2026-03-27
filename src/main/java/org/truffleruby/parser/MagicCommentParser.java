/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is modified from org.jruby.runtime.encoding.EncodingService,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.collections.Memo;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.TStringWithEncoding;

import java.util.Objects;

import static org.truffleruby.core.string.StringSupport.isAsciiSpace;

public abstract class MagicCommentParser {

    public interface MagicCommentHandler {
        boolean onMagicComment(String name, TruffleString value);
    }

    public static boolean isMagicEncodingComment(String name) {
        return "coding".equalsIgnoreCase(name) || "encoding".equalsIgnoreCase(name);
    }

    public static boolean isMagicTruffleRubyPrimitivesComment(String name) {
        return "truffleruby_primitives".equalsIgnoreCase(name);
    }

    public static TStringWithEncoding createSourceTStringBasedOnMagicEncodingComment(byte[] bytes,
            RubyEncoding defaultEncoding) {
        // We need a TStringWithEncoding, it could be in any encoding, it's just bytes at this stage.
        // We use the defaultEncoding so then we do not need to scan bytes for CodeRange a second time
        // if there is no magic encoding or if it's the same as defaultEncoding.
        var tstring = new TStringWithEncoding(TStringUtils.fromByteArray(bytes, defaultEncoding), defaultEncoding);
        return createSourceTStringBasedOnMagicEncodingComment(tstring, defaultEncoding);
    }

    public static TStringWithEncoding createSourceTStringBasedOnMagicEncodingComment(TStringWithEncoding source,
            RubyEncoding defaultEncoding) {
        Objects.requireNonNull(defaultEncoding);
        var encoding = parseMagicEncodingComment(source);
        if (encoding == null) {
            encoding = defaultEncoding;
        }
        if (source.getEncoding() != encoding) {
            source = source.forceEncoding(encoding);
        }
        return source;
    }

    /** Peak in source to see if there is a magic encoding comment. This is used by eval() & friends to know the actual
     * encoding of the source code, and be able to convert to a Java String faithfully. */
    public static RubyEncoding parseMagicEncodingComment(TStringWithEncoding source) {
        var encoding = new Memo<RubyEncoding>(null);

        var bytes = source.getInternalByteArray();
        final int length = bytes.getLength();
        int start = 0;

        if (hasShebangLine(bytes)) {
            start = newLineIndex(bytes, 2) + 1;
        }

        // Skip leading spaces but don't jump to another line
        while (start < length && isAsciiSpace(bytes.get(start)) && bytes.get(start) != '\n') {
            start++;
        }

        if (start < length && bytes.get(start) == '#') {
            start++;

            final int magicLineStart = start;
            int endOfMagicLine = newLineIndex(bytes, magicLineStart);
            if (endOfMagicLine < length) {
                endOfMagicLine++;
            }
            int magicLineLength = endOfMagicLine - magicLineStart;

            TStringWithEncoding magicLine = source.substring(magicLineStart, magicLineLength);

            parser_magic_comment(magicLine, 0, magicLineLength,
                    (name, value) -> {
                        if (isMagicEncodingComment(name)) {
                            RubyEncoding rubyEncoding = Encodings.getBuiltInEncoding(value.toJavaStringUncached());
                            if (rubyEncoding != null) {
                                encoding.set(rubyEncoding);
                                return true;
                            }
                        }
                        return false;
                    });

            if (encoding.get() == null) {
                TruffleString encodingName = get_file_encoding(magicLine);
                if (encodingName != null) {
                    RubyEncoding rubyEncoding = Encodings.getBuiltInEncoding(encodingName.toJavaStringUncached());
                    if (rubyEncoding != null) {
                        encoding.set(rubyEncoding);
                    }
                }
            }
        }

        return encoding.get();
    }

    // MRI: parser_magic_comment
    public static boolean parser_magic_comment(TStringWithEncoding magicLine, int magicLineOffset, int magicLineLength,
            MagicCommentHandler magicCommentHandler) {
        boolean emacsStyle = false;
        int i = magicLineOffset;
        int end = magicLineOffset + magicLineLength;

        if (magicLineLength <= 7) {
            return false;
        }

        final int emacsBegin = findEmacsStyleMarker(magicLine, 0, end);
        if (emacsBegin >= 0) {
            final int emacsEnd = findEmacsStyleMarker(magicLine, emacsBegin, end);
            if (emacsEnd < 0) {
                return false;
            }
            emacsStyle = true;
            i = emacsBegin;
            end = emacsEnd - 3; // -3 is to backup over the final -*- we just found
        }

        while (i < end) { // in Emacs mode, there can be multiple name/value pairs on the same line

            // Manual parsing corresponding to this Regexp.
            // Done manually as we want to parse bytes and don't know the encoding yet, and to optimize speed.

            // / [\s'":;]* (?<name> [^\s'":;]+ ) \s* : \s* (?<value> "(?:\\.|[^"])*" | [^";\s]+ ) [\s;]* /x

            // Ignore leading whitespace or '":;
            while (i < end) {
                int c = magicLine.get(i);

                if (isIgnoredMagicLineCharacter(c) || isAsciiSpace(c)) {
                    i++;
                } else {
                    break;
                }
            }

            final int nameBegin = i;

            // Consume anything except [\s'":;]
            while (i < end) {
                int c = magicLine.get(i);

                if (isIgnoredMagicLineCharacter(c) || isAsciiSpace(c)) {
                    break;
                } else {
                    i++;
                }
            }

            final int nameEnd = i;

            // Ignore whitespace
            while (i < end && isAsciiSpace(magicLine.get(i))) {
                i++;
            }

            if (i == end) {
                break;
            }

            // Expect ':' between name and value
            final int sep = magicLine.get(i);
            if (sep == ':') {
                i++;
            } else {
                if (!emacsStyle) {
                    return false;
                }
                continue;
            }

            // Ignore whitespace
            while (i < end && isAsciiSpace(magicLine.get(i))) {
                i++;
            }

            if (i == end) {
                break;
            }

            final int valueBegin, valueEnd;

            if (magicLine.get(i) == '"') { // quoted value
                valueBegin = ++i;
                while (i < end && magicLine.get(i) != '"') {
                    if (magicLine.get(i) == '\\') {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                valueEnd = i;

                // Skip the final "
                if (i < end) {
                    i++;
                }
            } else {
                valueBegin = i;
                while (i < end) {
                    int c = magicLine.get(i);
                    if (c != '"' && c != ';' && !isAsciiSpace(c)) {
                        i++;
                    } else {
                        break;
                    }
                }
                valueEnd = i;
            }

            if (emacsStyle) {
                // Ignore trailing whitespace or ;
                while (i < end && (magicLine.get(i) == ';' ||
                        isAsciiSpace(magicLine.get(i)))) {
                    i++;
                }
            } else {
                // Ignore trailing whitespace
                while (i < end && isAsciiSpace(magicLine.get(i))) {
                    i++;
                }

                if (i < end) {
                    return false;
                }
            }

            final String name = magicLine.substring(nameBegin, nameEnd - nameBegin).toJavaString().replace('-', '_');
            final TruffleString value = magicLine.substringAsTString(valueBegin, valueEnd - valueBegin);

            if (!magicCommentHandler.onMagicComment(name, value)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasShebangLine(InternalByteArray bytes) {
        return bytes.getLength() > 2 && bytes.get(0) == '#' && bytes.get(1) == '!';
    }

    private static int newLineIndex(InternalByteArray bytes, int start) {
        int index = com.oracle.truffle.api.ArrayUtils.indexOf(
                bytes.getArray(),
                bytes.getOffset() + start,
                bytes.getEnd(),
                (byte) '\n');
        if (index < 0) {
            return bytes.getLength();
        } else {
            return index - bytes.getOffset();
        }
    }

    private static boolean isIgnoredMagicLineCharacter(int c) {
        switch (c) {
            case '\'':
            case '"':
            case ':':
            case ';':
                return true;
            default:
                return false;
        }
    }

    /* MRI: magic_comment_marker Find -*-, as in emacs "file local variable" (special comment at the top of the file) */
    private static int findEmacsStyleMarker(TStringWithEncoding str, int begin, int end) {
        var bytes = str.getInternalByteArray();
        int i = begin;

        while (i < end) {
            switch (bytes.get(i)) {
                case '-':
                    if (i >= 2 && bytes.get(i - 1) == '*' && bytes.get(i - 2) == '-') {
                        return i + 1;
                    }
                    i += 2;
                    break;
                case '*':
                    if (i + 1 >= end) {
                        return -1;
                    }

                    if (bytes.get(i + 1) != '-') {
                        i += 4;
                    } else if (bytes.get(i - 1) != '-') {
                        i += 2;
                    } else {
                        return i + 2;
                    }
                    break;
                default:
                    i += 3;
                    break;
            }
        }
        return -1;
    }

    public static TruffleString get_file_encoding(TStringWithEncoding magicLine) {
        int str = 0;
        int send = magicLine.byteLength();
        boolean sep = false;
        for (;;) {
            if (send - str <= 6) {
                return null;
            }

            switch (magicLine.get(str + 6)) {
                case 'C':
                case 'c':
                    str += 6;
                    continue;
                case 'O':
                case 'o':
                    str += 5;
                    continue;
                case 'D':
                case 'd':
                    str += 4;
                    continue;
                case 'I':
                case 'i':
                    str += 3;
                    continue;
                case 'N':
                case 'n':
                    str += 2;
                    continue;
                case 'G':
                case 'g':
                    str += 1;
                    continue;
                case '=':
                case ':':
                    sep = true;
                    str += 6;
                    break;
                default:
                    str += 6;
                    if (Character.isSpaceChar(magicLine.get(str))) {
                        break;
                    }
                    continue;
            }
            if (magicLine.substring(str - 6, 6).toJavaString().equalsIgnoreCase("coding")) {
                break;
            }
        }

        for (;;) {
            do {
                str++;
                if (str >= send) {
                    return null;
                }
            } while (Character.isSpaceChar(magicLine.get(str)));
            if (sep) {
                break;
            }

            if (magicLine.get(str) != '=' && magicLine.get(str) != ':') {
                return null;
            }
            sep = true;
            str++;
        }

        int beg = str;
        while ((magicLine.get(str) == '-' || magicLine.get(str) == '_' ||
                Character.isLetterOrDigit(magicLine.get(str))) && ++str < send) {
        }
        return magicLine.substring(beg, str - beg).tstring;
    }

}
