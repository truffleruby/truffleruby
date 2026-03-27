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
package org.truffleruby.core.format.format;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.StringOperations;

import java.math.BigInteger;

@NodeChild("width")
@NodeChild("precision")
@NodeChild("value")
public abstract class FormatIntegerBinaryNode extends FormatNode {

    private final char format;
    private final boolean hasPlusFlag;
    private final boolean useAlternativeFormat;
    private final boolean hasMinusFlag;
    private final boolean hasSpaceFlag;
    private final boolean hasZeroFlag;

    public FormatIntegerBinaryNode(
            char format,
            boolean hasPlusFlag,
            boolean useAlternativeFormat,
            boolean hasMinusFlag,
            boolean hasSpaceFlag,
            boolean hasZeroFlag) {
        this.format = format;
        this.hasPlusFlag = hasPlusFlag;
        this.useAlternativeFormat = useAlternativeFormat;
        this.hasMinusFlag = hasMinusFlag;
        this.hasSpaceFlag = hasSpaceFlag;
        this.hasZeroFlag = hasZeroFlag;
    }

    @Specialization
    byte[] format(int width, int precision, int value) {
        final boolean isNegative = value < 0;
        final boolean negativeAndPadded = isNegative && (this.hasSpaceFlag || this.hasPlusFlag);
        final String formatted = negativeAndPadded ? Integer.toBinaryString(-value) : Integer.toBinaryString(value);
        return getFormattedString(
                formatted,
                width,
                precision,
                isNegative,
                this.hasSpaceFlag,
                this.hasPlusFlag,
                this.hasZeroFlag,
                this.useAlternativeFormat,
                this.hasMinusFlag,
                this.format);
    }

    @Specialization
    byte[] format(int width, int precision, long value) {
        final boolean isNegative = value < 0;
        final boolean negativeAndPadded = isNegative && (this.hasSpaceFlag || this.hasPlusFlag);
        final String formatted = negativeAndPadded ? Long.toBinaryString(-value) : Long.toBinaryString(value);
        return getFormattedString(
                formatted,
                width,
                precision,
                isNegative,
                this.hasSpaceFlag,
                this.hasPlusFlag,
                this.hasZeroFlag,
                this.useAlternativeFormat,
                this.hasMinusFlag,
                this.format);
    }

    @TruffleBoundary
    @Specialization
    byte[] format(int width, int precision, RubyBignum value) {
        final BigInteger bigInteger = value.value;
        final boolean isNegative = bigInteger.signum() == -1;
        final boolean negativeAndPadded = isNegative && (this.hasSpaceFlag || this.hasPlusFlag);

        final String formatted;
        if (negativeAndPadded) {
            formatted = bigInteger.abs().toString(2);
        } else if (!isNegative) {
            formatted = bigInteger.toString(2);
        } else {
            StringBuilder builder = new StringBuilder();
            final byte[] bytes = bigInteger.toByteArray();
            for (byte b : bytes) {
                builder.append(Integer.toBinaryString(b & 0xFF));
            }
            formatted = builder.toString();
        }
        return getFormattedString(
                formatted,
                width,
                precision,
                isNegative,
                this.hasSpaceFlag,
                this.hasPlusFlag,
                this.hasZeroFlag,
                this.useAlternativeFormat,
                this.hasMinusFlag,
                this.format);
    }

    @TruffleBoundary
    private static byte[] getFormattedString(String formatted, int width, int precision, boolean isNegative,
            boolean isSpacePadded, boolean hasPlusFlag, boolean hasZeroFlag,
            boolean useAlternativeFormat, boolean hasMinusFlag,
            char format) {
        if (width < 0 && width != PrintfSimpleTreeBuilder.DEFAULT) {
            width = -width;
            hasMinusFlag = true;
        }

        if (isNegative && !(isSpacePadded || hasPlusFlag)) {
            if (formatted.contains("0")) {
                formatted = formatted.substring(formatted.indexOf('0'), formatted.length());
                int desiredLength = Math.max(precision, width);
                if (formatted.length() + 3 < desiredLength) {
                    final int addOnes = desiredLength - (formatted.length() + 3);
                    for (int i = addOnes; i > 0; i--) {
                        formatted = "1" + formatted;
                    }
                }
                formatted = "..1" + formatted;
            } else {
                formatted = "..1";
            }
        } else {
            if (hasZeroFlag || precision != PrintfSimpleTreeBuilder.DEFAULT) {
                if (!hasMinusFlag) {
                    final int padZeros = precision != PrintfSimpleTreeBuilder.DEFAULT ? precision : width;
                    while (formatted.length() < padZeros) {
                        formatted = "0" + formatted;
                    }
                }
            }
        }

        while (formatted.length() < width) {
            if (!hasMinusFlag) {
                formatted = " " + formatted;
            } else {
                formatted = formatted + " ";
            }

        }


        if (useAlternativeFormat) {
            if (format == 'B') {
                formatted = "0B" + formatted;
            } else {
                formatted = "0b" + formatted;
            }
        }

        if (isSpacePadded || hasPlusFlag) {
            if (isNegative) {
                formatted = "-" + formatted;
            } else {
                if (hasPlusFlag) {
                    formatted = "+" + formatted;
                } else {
                    formatted = " " + formatted;
                }

            }
        }

        return StringOperations.encodeAsciiBytes(formatted);
    }

}
