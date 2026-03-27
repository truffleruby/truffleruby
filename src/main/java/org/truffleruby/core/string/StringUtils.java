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

import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/** {@link TruffleBoundary} methods for {@link java.lang.String} */
public abstract class StringUtils {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    @TruffleBoundary
    public static String toString(Object value) {
        return String.valueOf(value);
    }

    @TruffleBoundary
    public static String format(Locale locale, String format, Object... args) {
        return String.format(locale, format, args);
    }

    @TruffleBoundary
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

    public static byte[] formatASCIIBytes(String format, Object... args) {
        return StringOperations.encodeAsciiBytes(format(format, args));
    }

    @TruffleBoundary
    public static String replace(String string, char oldChar, char newChar) {
        return string.replace(oldChar, newChar);
    }

    @TruffleBoundary
    public static String replace(String string, CharSequence target, CharSequence replacement) {
        return string.replace(target, replacement);
    }

    @TruffleBoundary
    public static String toLowerCase(String string) {
        return string.toLowerCase(Locale.ENGLISH);
    }

    @TruffleBoundary
    public static String toUpperCase(String string) {
        return string.toUpperCase(Locale.ENGLISH);
    }

    @TruffleBoundary
    public static String join(Object[] elements, String separator) {
        return join(elements, separator, "", "");
    }

    @TruffleBoundary
    public static String join(Object[] elements, String separator, String prefix, String suffix) {
        var builder = new StringBuilder(prefix);
        for (int i = 0; i < elements.length; i++) {
            builder.append(elements[i]);
            if (i != elements.length - 1) {
                builder.append(separator);
            }
        }
        builder.append(suffix);
        return builder.toString();
    }

}
