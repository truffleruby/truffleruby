/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is modified from org.jruby.util.Sprintf,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 *
 * Contains code modified from Sprintf.java
 *
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
 */
package org.truffleruby.core.format.format;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;

@NodeChild("width")
@NodeChild("precision")
@NodeChild("value")
@ImportStatic(Double.class)
public abstract class FormatFloatGenericNode extends FormatNode {

    private static final byte[] NAN_VALUE = { 'N', 'a', 'N' };
    private static final byte[] INFINITY_VALUE = { 'I', 'n', 'f' };

    protected final boolean hasSpaceFlag;
    protected final boolean hasZeroFlag;
    protected final boolean hasPlusFlag;
    protected final boolean hasMinusFlag;
    protected final boolean hasFSharpFlag;

    protected FormatFloatGenericNode(
            boolean hasSpaceFlag,
            boolean hasZeroFlag,
            boolean hasPlusFlag,
            boolean hasMinusFlag,
            boolean hasFSharpFlag) {
        this.hasSpaceFlag = hasSpaceFlag;
        this.hasZeroFlag = hasZeroFlag;
        this.hasPlusFlag = hasPlusFlag;
        this.hasMinusFlag = hasMinusFlag;
        this.hasFSharpFlag = hasFSharpFlag;
    }

    @Specialization(guards = "value == POSITIVE_INFINITY")
    byte[] formatPositiveInfinity(int width, int precision, double value) {
        final byte[] digits;
        final int len;
        final byte signChar;
        final ByteArrayBuilder buf = new ByteArrayBuilder();

        if (width == PrintfSimpleTreeBuilder.DEFAULT) {
            width = 0;
        }

        digits = INFINITY_VALUE;
        len = INFINITY_VALUE.length;
        if (hasPlusFlag) {
            signChar = '+';
            width--;
        } else if (hasSpaceFlag) {
            signChar = ' ';
            width--;
        } else {
            signChar = 0;
        }
        width -= len;

        if (width > 0 && !hasMinusFlag) {
            buf.append(' ', width);
            width = 0;
        }
        if (signChar != 0) {
            buf.append(signChar);
        }

        if (width > 0 && !hasMinusFlag) {
            buf.append('0', width);
            width = 0;
        }
        buf.append(digits);
        if (width > 0) {
            buf.append(' ', width);
        }

        return buf.getBytes();
    }

    @Specialization(guards = "value == NEGATIVE_INFINITY")
    byte[] formatNegativeInfinity(int width, int precision, double value) {
        final byte[] digits;
        final int len;
        final byte signChar;
        final ByteArrayBuilder buf = new ByteArrayBuilder();

        if (width == PrintfSimpleTreeBuilder.DEFAULT) {
            width = 0;
        }

        digits = INFINITY_VALUE;
        len = INFINITY_VALUE.length;
        signChar = '-';
        width--;

        width -= len;

        if (width > 0 && !hasMinusFlag) {
            buf.append(' ', width);
            width = 0;
        }
        buf.append(signChar);

        if (width > 0 && !hasMinusFlag) {
            buf.append('0', width);
            width = 0;
        }
        buf.append(digits);
        if (width > 0) {
            buf.append(' ', width);
        }

        return buf.getBytes();
    }

    @Specialization(guards = "isNaN(value)")
    byte[] formatNaN(int width, int precision, double value) {
        final byte[] digits;
        final int len;
        final byte signChar;
        final ByteArrayBuilder buf = new ByteArrayBuilder();

        if (width == PrintfSimpleTreeBuilder.DEFAULT) {
            width = 0;
        }

        digits = NAN_VALUE;
        len = NAN_VALUE.length;
        if (hasPlusFlag) {
            signChar = '+';
            width--;
        } else if (hasSpaceFlag) {
            signChar = ' ';
            width--;
        } else {
            signChar = 0;
        }
        width -= len;

        if (width > 0 && !hasMinusFlag) {
            buf.append(' ', width);
            width = 0;
        }
        if (signChar != 0) {
            buf.append(signChar);
        }

        if (width > 0 && !hasMinusFlag) {
            buf.append('0', width);
            width = 0;
        }
        buf.append(digits);
        if (width > 0) {
            buf.append(' ', width);
        }

        return buf.getBytes();
    }

    protected final byte[] formatNumber(int origWidth, int precision, Object value) {
        final byte[] digits = doFormat(precision, value);
        final ByteArrayBuilder buf = new ByteArrayBuilder();

        int width = (origWidth == PrintfSimpleTreeBuilder.DEFAULT) ? -1 : Math.abs(origWidth);
        width -= digits.length;

        if (origWidth > 0 && width > 0 && !hasMinusFlag) {
            if (hasZeroFlag) {
                boolean firstDigit = digits[0] >= '0' && digits[0] <= '9';
                buf.append(digits, 0, prefixBytes() + (firstDigit ? 0 : 1));
                buf.append('0', width);
                appendNumber(digits, buf, prefixBytes() + (firstDigit ? 0 : 1));
            } else {
                buf.append(' ', width);
                appendNumber(digits, buf, 0);
            }
        } else {
            appendNumber(digits, buf, 0);
            if (width > 0) {
                buf.append(' ', width);
            }
        }
        return buf.getBytes();
    }

    protected int prefixBytes() {
        return 0;
    }

    protected byte[] doFormat(int precision, Object value) {
        return null;
    }

    private static void appendNumber(byte[] digits, ByteArrayBuilder buf, int start) {
        buf.append(digits, start, digits.length - start);
    }

    protected boolean nonSpecialValue(Object value) {
        if (value instanceof Double) {
            return Double.isFinite((double) value);
        } else {
            return true;
        }
    }
}
