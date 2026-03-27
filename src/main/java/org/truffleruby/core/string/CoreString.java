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

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class CoreString {

    private final RubyLanguage language;
    private final String literal;

    @CompilationFinal private volatile TruffleString tstring;

    public CoreString(RubyLanguage language, String literal) {
        assert language != null;
        assert is7Bit(literal);
        this.language = language;
        this.literal = literal;
    }

    public TruffleString getTruffleString() {
        if (tstring == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            // Binary because error message Strings have a ASCII-8BIT encoding on MRI.
            // When used for creating a Symbol, the encoding is adapted as needed.
            tstring = language.tstringCache.getTString(StringOperations.encodeAsciiBytes(literal), Encodings.BINARY);
        }

        return tstring;
    }

    public RubyString createInstance(RubyContext context) {
        return new RubyString(
                context.getCoreLibrary().stringClass,
                language.stringShape,
                false,
                getTruffleString(),
                Encodings.BINARY);
    }

    private static boolean is7Bit(String literal) {
        for (int n = 0; n < literal.length(); n++) {
            if (literal.charAt(n) > 0b1111111) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return literal;
    }

}
