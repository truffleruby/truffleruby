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

import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.thread.RubyThread;

@ImportStatic(Double.class)
public abstract class FormatGFloatNode extends FormatFloatGenericNode {

    private final char expSeparator;

    public FormatGFloatNode(
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
    byte[] formatGExponential(int width, int precision, Object dval) {
        if (precision == PrintfSimpleTreeBuilder.DEFAULT) {
            precision = 6;
        }
        if (precision == 0) {
            precision = 1;
        }
        return formatNumber(width, precision, dval);
    }

    protected static boolean inSimpleRange(int precision, Object value) {
        if (value instanceof Double) {
            double dval = (double) value;
            return dval == 0.0 || (Math.abs(dval) >= 0.0001 && Math.pow(10, precision) - Math.abs(dval) >= 0.5);
        } else if (value instanceof Long) {
            long lval = (long) value;
            return lval == 0 || (Math.pow(10, precision) - Math.abs(lval) > 0);
        } else if (value instanceof Integer) {
            int ival = (int) value;
            return ival == 0 || (Math.pow(10, precision) - Math.abs(ival) > 0);
        } else if (value instanceof BigInteger) {
            BigInteger bval = (BigInteger) value;
            return bval.equals(BigInteger.ZERO) ||
                    BigInteger.TEN.pow(precision).subtract(bval.abs()).compareTo(BigInteger.ZERO) == 1;
        } else {
            return true;
        }
    }

    @TruffleBoundary
    @Override
    protected byte[] doFormat(int precision, Object value) {
        final boolean simple = inSimpleRange(precision, value);
        final byte[] digits;
        RubyThread currentThread = getLanguage().getCurrentThread();
        DecimalFormat format = simple ? currentThread.formatGFloatSimple : currentThread.formatGFloatExponential;

        if (format == null) {
            if (simple) {
                final DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
                format = new DecimalFormat("0.0", formatSymbols);
                currentThread.formatGFloatSimple = format;
            } else {
                final DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
                format = new DecimalFormat("0.0E00", formatSymbols);
                currentThread.formatGFloatExponential = format;
            }

        }

        if (hasPlusFlag) {
            format.setPositivePrefix("+");
        } else if (hasSpaceFlag) {
            format.setPositivePrefix(" ");
        } else {
            format.setPositivePrefix("");
        }

        format.setDecimalSeparatorAlwaysShown(hasFSharpFlag);

        if (!simple) {
            DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
            boolean positiveExp = !(value instanceof Double && Math.abs((double) value) < 1.0);
            if (expSeparator == 'g') {
                symbols.setExponentSeparator(positiveExp ? "e+" : "e");
            } else {
                symbols.setExponentSeparator(positiveExp ? "E+" : "E");
            }
            format.setDecimalFormatSymbols(symbols);
        }

        format.setMinimumIntegerDigits(1);
        if (!simple) {
            format.setMaximumFractionDigits(Math.max(0, precision - 1));
            format.setMinimumFractionDigits(0);
        } else {
            int intDigits = getIntDigits(value);
            if (hasFSharpFlag) {
                format.setMinimumFractionDigits(precision - intDigits);
            } else {
                format.setMinimumFractionDigits(0);
            }
            format.setMaximumFractionDigits(precision - intDigits);
        }
        digits = StringOperations.encodeAsciiBytes(format.format(value));

        return digits;
    }

    private int getIntDigits(Object value) {
        int intDigits = 0;
        if (value instanceof Double) {
            double absval = Math.abs((double) value);
            double pow = 0.1;
            if (absval != 0.0) {
                while (absval >= pow * 10) {
                    pow = 10 * pow;
                    intDigits++;
                }
                while (absval < pow) {
                    pow = pow / 10;
                    intDigits--;
                }
            }
        } else if (value instanceof Long || value instanceof Integer) {
            long absval;
            if (value instanceof Long) {
                absval = Math.abs((long) value);
            } else {
                absval = Math.abs((int) value);
            }
            long pow = 1;
            intDigits = 1;
            if (absval != 0) {
                while (absval >= pow * 10) {
                    pow = 10 * pow;
                    intDigits++;
                }
            }
        } else if (value instanceof BigInteger) {
            BigInteger absval = ((BigInteger) value).abs();
            BigInteger pow = BigInteger.ONE;
            intDigits = 1;
            if (!absval.equals(BigInteger.ZERO)) {
                while (absval.compareTo(pow = pow.multiply(BigInteger.TEN)) == 1) {
                    intDigits++;
                }
            }
        }
        return intDigits;
    }
}
