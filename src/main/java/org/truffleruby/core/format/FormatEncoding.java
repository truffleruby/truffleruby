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
package org.truffleruby.core.format;

import com.oracle.truffle.api.nodes.Node;

import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.RubyContext;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.language.control.RaiseException;

public final class FormatEncoding {

    public static final FormatEncoding DEFAULT = new FormatEncoding(Encodings.BINARY);
    public static final FormatEncoding ASCII_8BIT = new FormatEncoding(Encodings.BINARY);
    public static final FormatEncoding US_ASCII = new FormatEncoding(Encodings.US_ASCII);
    public static final FormatEncoding UTF_8 = new FormatEncoding(Encodings.UTF_8);

    private final RubyEncoding encoding;

    public FormatEncoding(RubyEncoding encoding) {
        this.encoding = encoding;
    }

    public RubyEncoding getEncoding() {
        return encoding;
    }

    public RubyEncoding getEncodingForLength(int length) {
        if (length == 0) {
            return Encodings.US_ASCII;
        } else {
            return encoding;
        }
    }

    public static FormatEncoding find(RubyEncoding encoding, Node currentNode) {
        if (encoding == Encodings.BINARY) {
            return ASCII_8BIT;
        }

        if (encoding == Encodings.US_ASCII) {
            return US_ASCII;
        }

        if (encoding == Encodings.UTF_8) {
            return UTF_8;
        }

        // TODO (kjmenard 17-Oct-18): This entire enum needs to be rethought since a format string can take on any encoding, not just the 3 codified here.
        RubyContext context = RubyContext.get(currentNode);
        throw new RaiseException(
                context,
                context.getCoreExceptions().runtimeError("Can't find format encoding for " + encoding, currentNode));
    }

    /** Given the current encoding for a pack string, and something that requires another encoding, give us the encoding
     * that we should use for the result of pack. */
    public FormatEncoding unifyWith(FormatEncoding other) {
        if (this == DEFAULT) {
            return other;
        }

        if (other == DEFAULT) {
            return this;
        }

        if (other == ASCII_8BIT || other == US_ASCII) {
            return ASCII_8BIT;
        } else if (other == UTF_8) {
            if (this == ASCII_8BIT || this == US_ASCII) {
                return ASCII_8BIT;
            } else if (this == UTF_8) {
                return UTF_8;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

}
