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

    @TruffleBoundary
    @Override
    protected byte[] doFormat(int precision, Object value) {
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
