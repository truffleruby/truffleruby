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
package org.truffleruby.core.format.read.bytes;

import java.util.Arrays;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.core.string.RubyString;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadBinaryStringNode extends FormatNode {

    final boolean readToEnd;
    final boolean readToNull;
    final int count;
    final boolean trimTrailingSpaces;
    final boolean trimTrailingNulls;
    final boolean trimToFirstNull;

    public ReadBinaryStringNode(
            boolean readToEnd,
            boolean readToNull,
            int count,
            boolean trimTrailingSpaces,
            boolean trimTrailingNulls,
            boolean trimToFirstNull) {

        this.readToEnd = readToEnd;
        this.readToNull = readToNull;
        this.count = count;
        this.trimTrailingSpaces = trimTrailingSpaces;
        this.trimTrailingNulls = trimTrailingNulls;
        this.trimToFirstNull = trimToFirstNull;
    }

    @Specialization(guards = "isNull(source)")
    void read(VirtualFrame frame, Object source) {
        // Advance will handle the error
        advanceSourcePosition(frame, count);

        throw new IllegalStateException();
    }

    @Specialization
    RubyString read(VirtualFrame frame, byte[] source,
            @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        final int start = getSourcePosition(frame);
        final int end = getSourceEnd(frame);

        int length;

        if (readToEnd) {
            length = 0;

            while (start + length < end &&
                    (!readToNull || (start + length < end && source[start + length] != 0))) {
                length++;
            }

            if (start + length < end && source[start + length] == 0) {
                length++;
            }
        } else if (readToNull) {
            length = 0;

            while (start + length < end && length < count && source[start + length] != 0) {
                length++;
            }
        } else {
            length = count;

            if (start + length >= end) {
                length = end - start;
            }
        }

        int usedLength = length;

        while (usedLength > 0 && ((trimTrailingSpaces && source[start + usedLength - 1] == ' ') ||
                (trimTrailingNulls && source[start + usedLength - 1] == 0))) {
            usedLength--;
        }

        if (trimToFirstNull) {
            final int firstNull = indexOfFirstNull(source, start, usedLength);

            if (firstNull != -1 && trimTrailingNulls) {
                usedLength = firstNull;
            }
        }

        setSourcePosition(frame, start + length);

        return createString(
                fromByteArrayNode,
                Arrays.copyOfRange(source, start, start + usedLength),
                Encodings.BINARY);
    }

    private int indexOfFirstNull(byte[] bytes, int start, int length) {
        for (int n = 0; n < length; n++) {
            if (bytes[start + n] == 0) {
                return n;
            }
        }

        return -1;
    }

}
