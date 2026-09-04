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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;
import org.truffleruby.core.string.StringOperations;

@ImportStatic(Double.class)
public abstract class FormatFFloatNode extends FormatFloatGenericNode {

    public FormatFFloatNode(
            boolean hasSpaceFlag,
            boolean hasZeroFlag,
            boolean hasPlusFlag,
            boolean hasMinusFlag,
            boolean hasFSharpFlag) {
        super(hasSpaceFlag, hasZeroFlag, hasPlusFlag, hasMinusFlag, hasFSharpFlag);
    }

    @Specialization(guards = { "nonSpecialValue(dval)" })
    byte[] formatFGeneric(int width, int precision, Object dval) {
        if (precision == PrintfSimpleTreeBuilder.DEFAULT) {
            precision = 6;
        }

        return formatNumber(width, precision, dval);
    }

    /** 10^0 .. 10^18 */
    private static final long[] POW10 = new long[19];
    /** 5^0 .. 5^18 */
    private static final long[] POW5 = new long[19];
    static {
        POW10[0] = 1;
        POW5[0] = 1;
        for (int i = 1; i < POW10.length; i++) {
            POW10[i] = POW10[i - 1] * 10;
            POW5[i] = POW5[i - 1] * 5;
        }
    }

    @TruffleBoundary
    @Override
    protected byte[] doFormat(int precision, Object value) {
        if (value instanceof Double) {
            final byte[] fast = fastFixedFormat(precision, (double) value);
            if (fast != null) {
                return fast;
            }
        }
        return slowFormat(precision, value);
    }

    /** Formats {@code %.<precision>f} exactly like C printf (round half to even on the exact decimal value of the
     * double), using only long arithmetic: value * 10^precision = mantissa * 5^precision * 2^(exponent + precision),
     * computed exactly in 128 bits and rounded once. Returns null when out of range for this fast path (then the
     * DecimalFormat path is used). This avoids DecimalFormat and its FloatingDecimal digit generation, which dominated
     * profiles of ERB rendering with float formatting. */
    private byte[] fastFixedFormat(int precision, double dval) {
        if (precision < 0 || precision > 17) {
            return null;
        }

        final long bits = Double.doubleToRawLongBits(dval);
        final boolean negative = bits < 0;
        final int biasedExponent = (int) ((bits >>> 52) & 0x7ff);

        final long scaled; // round_half_even(|dval| * 10^precision)
        if (biasedExponent == 0) {
            if ((bits << 1) != 0) {
                return null; // subnormal: rare, use the slow path
            }
            scaled = 0; // +0.0 or -0.0
        } else {
            assert biasedExponent != 0x7ff : "special values are handled by other specializations";
            final long mantissa = (bits & 0x000f_ffff_ffff_ffffL) | (1L << 52);
            final int exponent = biasedExponent - 1075; // |dval| = mantissa * 2^exponent
            // |dval| * 10^precision = product * 2^shift, exactly (product = mantissa * 5^precision < 2^96)
            final long productLow = mantissa * POW5[precision];
            final long productHigh = Math.multiplyHigh(mantissa, POW5[precision]);
            final int shift = exponent + precision;

            if (shift >= 0) {
                // An exact integer: give up if it does not fit in a positive long
                if (productHigh != 0 || shift >= 63 || (productLow >>> (63 - shift)) != 0) {
                    return null;
                }
                scaled = productLow << shift;
            } else {
                scaled = shiftRightHalfEven(productHigh, productLow, -shift);
                if (scaled < 0) {
                    return null; // did not fit in a positive long
                }
            }
        }

        return fixedDigits(precision, negative, scaled);
    }

    /** round_half_even((high:low) >> shift), or -1 if the result does not fit in a positive long */
    private static long shiftRightHalfEven(long high, long low, int shift) {
        final long result;
        final boolean roundUp;
        if (shift < 64) {
            if ((shift == 0 ? high : high >>> shift) != 0) {
                return -1;
            }
            result = shift == 0 ? low : ((high << (64 - shift)) | (low >>> shift));
            if (result < 0) {
                return -1;
            }
            if (shift == 0) {
                return result;
            }
            final long remainder = low & ((1L << shift) - 1);
            final long half = 1L << (shift - 1);
            roundUp = remainder > half || (remainder == half && (result & 1) == 1);
        } else if (shift < 128) {
            final int highShift = shift - 64;
            result = high >>> highShift;
            if (result < 0) {
                return -1;
            }
            // remainder = (high & mask(highShift)) : low, compared against half = 2^(shift-1)
            final long remainderHigh = highShift == 0 ? 0 : high & ((1L << highShift) - 1);
            final int compare;
            if (highShift == 0) {
                // half = 2^63: compare low (unsigned) against 2^63
                compare = Long.compareUnsigned(low, 1L << 63);
            } else {
                final long halfHigh = 1L << (highShift - 1);
                compare = remainderHigh != halfHigh
                        ? Long.compareUnsigned(remainderHigh, halfHigh)
                        : (low != 0 ? 1 : 0);
            }
            roundUp = compare > 0 || (compare == 0 && (result & 1) == 1);
        } else {
            return 0; // the value is far smaller than half of 1, it rounds to 0
        }
        return roundUp ? result + 1 : result;
    }

    /** Builds the "[sign]integer[.fraction]" bytes for scaled = round(|value| * 10^precision) */
    private byte[] fixedDigits(int precision, boolean negative, long scaled) {
        final long integerPart = scaled / POW10[precision];
        final long fractionPart = scaled - integerPart * POW10[precision];

        int integerLength = 1;
        for (long remaining = integerPart; remaining >= 10; remaining /= 10) {
            integerLength++;
        }

        final byte signChar;
        if (negative) {
            signChar = '-';
        } else if (hasPlusFlag) {
            signChar = '+';
        } else if (hasSpaceFlag) {
            signChar = ' ';
        } else {
            signChar = 0;
        }

        final boolean dot = precision > 0 || hasFSharpFlag;
        final int length = (signChar != 0 ? 1 : 0) + integerLength + (dot ? 1 : 0) + precision;
        final byte[] bytes = new byte[length];
        int index = 0;
        if (signChar != 0) {
            bytes[index++] = signChar;
        }
        writeDigits(bytes, index, integerLength, integerPart);
        index += integerLength;
        if (dot) {
            bytes[index++] = '.';
            writeDigits(bytes, index, precision, fractionPart);
        }
        return bytes;
    }

    private static void writeDigits(byte[] bytes, int start, int length, long value) {
        long remaining = value;
        for (int i = start + length - 1; i >= start; i--) {
            bytes[i] = (byte) ('0' + (remaining % 10));
            remaining /= 10;
        }
        assert remaining == 0;
    }

    @TruffleBoundary
    private byte[] slowFormat(int precision, Object value) {
        final byte[] digits;
        DecimalFormat format = getLanguage().getCurrentThread().formatFFloat;
        if (format == null) {
            final DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
            format = new DecimalFormat("", formatSymbols);
            getLanguage().getCurrentThread().formatFFloat = format;
        }

        format.setGroupingSize(0);
        if (hasPlusFlag) {
            format.setPositivePrefix("+");
        } else if (hasSpaceFlag) {
            format.setPositivePrefix(" ");
        } else {
            format.setPositivePrefix("");
        }

        if (precision == 0 && hasFSharpFlag) {
            format.setPositiveSuffix(".");
            format.setNegativeSuffix(".");
        } else {
            format.setPositiveSuffix("");
            format.setNegativeSuffix("");
        }

        format.setMinimumIntegerDigits(1);
        format.setMinimumFractionDigits(precision);
        format.setMaximumFractionDigits(precision);
        if (value instanceof Double && Math.getExponent((double) value) > 53) {
            double dval = (double) value;
            long mantissa = Double.doubleToLongBits(dval) & 0xfffffffffffffL | 0x10000000000000L;
            BigInteger bi = BigInteger.valueOf(mantissa).shiftLeft(Math.getExponent(dval) - 52);
            if (dval < 0.0) {
                bi = bi.negate();
            }
            value = bi;
        }
        digits = StringOperations.encodeAsciiBytes(format.format(value));

        if (precision <= 340) {
            return digits;
        } else {
            // Decimal format has a limit of 340 decimal places, and apparently people require more.

            final ByteArrayBuilder buf = new ByteArrayBuilder();
            buf.append(digits);
            buf.append('0', precision - 340);
            return buf.getBytes();
        }
    }

}
