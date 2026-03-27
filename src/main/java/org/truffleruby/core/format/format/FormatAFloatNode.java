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

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;
import org.truffleruby.core.string.StringOperations;

@ImportStatic(Double.class)
public abstract class FormatAFloatNode extends FormatFloatGenericNode {

    private static final long SIGN_MASK = 1L << 63;
    private static final long BIASED_EXP_MASK = 0x7ffL << 52;
    private static final long MANTISSA_MASK = ~(SIGN_MASK | BIASED_EXP_MASK);
    private static final byte[] HEX_DIGITS = new byte[]{
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'a',
            'b',
            'c',
            'd',
            'e',
            'f' };
    private static final byte[] HEX_DIGITS_UPPER_CASE = new byte[]{
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'A',
            'B',
            'C',
            'D',
            'E',
            'F' };

    private final char expSeparator;

    public FormatAFloatNode(
            char expSeparator,
            boolean hasSpaceFlag,
            boolean hasZeroFlag,
            boolean hasPlusFlag,
            boolean hasMinusFlag,
            boolean hasFSharpFlag) {
        super(hasSpaceFlag, hasZeroFlag, hasPlusFlag, hasMinusFlag, hasFSharpFlag);
        this.expSeparator = expSeparator;
    }

    @Specialization(guards = { "nonSpecialValue(dval)" })
    byte[] formatFGeneric(int width, int precision, Object dval) {
        return formatNumber(width, precision, dval);
    }

    @Override
    protected int prefixBytes() {
        return 2;
    }

    @TruffleBoundary
    @Override
    protected byte[] doFormat(int precision, Object value) {
        final ByteArrayBuilder buf = new ByteArrayBuilder();
        final boolean positive = isPositive(value);
        long exponent = getExponent(value);
        final byte[] mantissaBytes = getMantissaBytes(value);

        if (!positive) {
            buf.append('-');
        } else if (hasPlusFlag) {
            buf.append('+');
        } else if (hasSpaceFlag) {
            buf.append(' ');
        }
        buf.append('0');
        buf.append(expSeparator == 'a' ? 'x' : 'X');
        if (mantissaBytes[0] == 0) {
            exponent = 0;
            buf.append('0');
            if (precision > 0 || hasFSharpFlag) {
                buf.append('.');
                while (precision > 0) {
                    buf.append('0');
                    precision--;
                }
            }
        } else {
            int i = 0;
            int digit = getDigit(i++, mantissaBytes);
            if (digit == 0) {
                digit = getDigit(i++, mantissaBytes);
            }
            assert digit == 1;
            buf.append('1');
            int digits = getNumberOfDigits(mantissaBytes);
            if (i < digits || hasFSharpFlag || precision > 0) {
                buf.append('.');
            }

            if (precision == PrintfSimpleTreeBuilder.DEFAULT) {
                precision = -1;
            }

            while ((precision < 0 && i < digits) || precision > 0) {
                digit = getDigit(i++, mantissaBytes);
                buf.append((expSeparator == 'a' ? HEX_DIGITS : HEX_DIGITS_UPPER_CASE)[digit]);
                precision--;
            }
        }

        buf.append(expSeparator == 'a' ? 'p' : 'P');
        if (exponent >= 0) {
            buf.append('+');
        }
        buf.append(StringOperations.encodeAsciiBytes(Long.toString(exponent)));

        return buf.getBytes();
    }


    private int getNumberOfDigits(byte[] bytes) {
        int digits = bytes.length * 2;
        if (getDigit(digits - 1, bytes) == 0) {
            digits--;
        }
        return digits;
    }

    private byte getDigit(int position, byte[] bytes) {
        int index = position / 2;
        if (index < bytes.length) {
            byte twoDigits = bytes[index];
            if (position % 2 == 0) {
                return (byte) ((twoDigits >> 4) & 0xf);
            } else {
                return (byte) (twoDigits & 0xf);
            }
        } else {
            return 0;
        }
    }

    private boolean isPositive(Object value) {
        if (value instanceof Double) {
            final long bits = Double.doubleToRawLongBits((double) value);
            return (bits & SIGN_MASK) == 0;
        } else if (value instanceof Long) {
            return 0 <= (long) value;
        } else if (value instanceof Integer) {
            return 0 <= (int) value;
        } else if (value instanceof BigInteger) {
            return ((BigInteger) value).signum() >= 0;
        }
        return true;
    }

    private byte[] getMantissaBytes(Object value) {
        BigInteger bi;
        if (value instanceof Double) {
            final long bits = Double.doubleToRawLongBits((double) value);
            long biasedExp = ((bits & BIASED_EXP_MASK) >> 52);
            long mantissaBits = bits & MANTISSA_MASK;
            if (biasedExp > 0) {
                mantissaBits = mantissaBits | 0x10000000000000L;
            }
            bi = BigInteger.valueOf(mantissaBits);
        } else if (value instanceof BigInteger) {
            bi = ((BigInteger) value);
        } else if (value instanceof Long) {
            bi = BigInteger.valueOf((long) value);
        } else if (value instanceof Integer) {
            bi = BigInteger.valueOf((int) value);
        } else {
            bi = BigInteger.ZERO;
        }
        bi = bi.abs();
        if (BigInteger.ZERO.equals(bi)) {
            return new byte[1];
        }

        // Shift things to get rid of all the trailing zeros.
        bi = bi.shiftRight(bi.getLowestSetBit() - 1);

        // We want the bit length to be 4n + 1 so that things line up nicely for the printing routine.
        int bitLength = bi.bitLength() % 4;
        if (bitLength != 1) {
            bi = bi.shiftLeft(5 - bitLength);
        }
        return bi.toByteArray();
    }

    private long getExponent(Object value) {
        if (value instanceof BigInteger) {
            return ((BigInteger) value).abs().bitLength() - 1;
        } else if (value instanceof Long) {
            long lval = (long) value;
            return lval == Long.MIN_VALUE ? 63 : 63 - Long.numberOfLeadingZeros(Math.abs(lval));
        } else if (value instanceof Integer) {
            long lval = (int) value;
            return 63 - Long.numberOfLeadingZeros(Math.abs(lval));
        } else if (value instanceof Double) {
            final long bits = Double.doubleToRawLongBits((double) value);
            long biasedExp = ((bits & BIASED_EXP_MASK) >> 52);
            long mantissaBits = bits & MANTISSA_MASK;
            if (biasedExp == 0) {
                // Sub normal cases are a little special.
                // Find the most significant bit in the mantissa
                final int lz = Long.numberOfLeadingZeros(mantissaBits);
                // Adjust the exponent to reflect this.
                biasedExp = biasedExp - (lz - 12);
            }
            return biasedExp - 1023;
        }
        return 0;
    }
}
