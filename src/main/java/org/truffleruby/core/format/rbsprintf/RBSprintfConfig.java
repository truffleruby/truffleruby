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
package org.truffleruby.core.format.rbsprintf;

import org.truffleruby.core.format.exceptions.InvalidFormatException;

public final class RBSprintfConfig {

    public enum FormatType {
        INTEGER,
        FLOAT,
        POINTER,
        RUBY_VALUE,
        OTHER
    }

    /* This enum type must be kept in sync with the one in printf.c as itas they are used to communicate the types of
     * arguments to be fetched from the va_list. */
    public enum FormatArgumentType {
        UNKNOWN,
        CHAR,
        SHORT,
        INT,
        LONG,
        LONGLONG,
        DOUBLE,
        LONGDOUBLE,
        SIZE_T,
        INTMAX_T,
        PTRDIFF_T,
        STRING,
        POINTER,
        VALUE,
    }

    private boolean literal = false;
    private byte[] literalBytes;

    private boolean argWidth = false;

    private Integer absoluteArgumentIndex;
    private Integer precision;
    private boolean precisionArg = false;
    private boolean precisionVisited = false;
    private Integer width;
    private boolean hasSpace = false;
    private boolean fsharp = false; // #
    private boolean plus = false;
    private boolean minus = false;
    private boolean zero = false;
    private boolean separator = false;
    private boolean widthStar = false;
    private boolean precisionStar = false;
    private char format;
    private FormatArgumentType formatLength;
    private FormatType formatType;


    public void checkForFlags() {
        if (hasWidth()) {
            throw new InvalidFormatException("flag after width");
        }
        if (hasPrecision()) {
            throw new InvalidFormatException("flag after precision");
        }
    }

    public void checkForWidth() {
        if (hasWidth()) {
            throw new InvalidFormatException("width given twice");
        }
        if (hasPrecision()) {
            throw new InvalidFormatException("width after precision");
        }
    }

    public void checkForFormatArgumentType() {
        if (hasFormatArgumentType()) {
            throw new InvalidFormatException("length given twice");
        }
    }

    public boolean isHasSpace() {
        return hasSpace;
    }

    public void setHasSpace(boolean hasSpace) {
        this.hasSpace = hasSpace;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public boolean isFsharp() {
        return fsharp;
    }

    public void setFsharp(boolean fsharp) {
        this.fsharp = fsharp;
    }

    public boolean isPlus() {
        return plus;
    }

    public void setPlus(boolean plus) {
        this.plus = plus;
    }

    public boolean isMinus() {
        return minus;
    }

    public void setMinus(boolean minus) {
        this.minus = minus;
    }

    public boolean isZero() {
        return zero;
    }

    public void setZero(boolean zero) {
        this.zero = zero;
    }

    public boolean isSeparator() {
        return separator;
    }

    public void setSeparator(boolean separator) {
        this.separator = separator;
    }

    public FormatArgumentType getFormatArgumentType() {
        return formatLength;
    }

    public void setFormatArgumentType(FormatArgumentType formatLength) {
        this.formatLength = formatLength;
    }

    public FormatType getFormatType() {
        return formatType;
    }

    public void setFormatType(FormatType formatType) {
        this.formatType = formatType;
    }

    public char getFormat() {
        return format;
    }

    public void setFormat(char format) {
        this.format = format;
    }

    public boolean isWidthStar() {
        return widthStar;
    }

    public void setWidthStar(boolean widthStar) {
        this.widthStar = widthStar;
    }

    public boolean isPrecisionStar() {
        return precisionStar;
    }

    public void setPrecisionStar(boolean precisionStar) {
        this.precisionStar = precisionStar;
    }

    public boolean hasPrecision() {
        return precision != null || precisionStar || precisionVisited;
    }

    public boolean hasWidth() {
        return width != null || widthStar;
    }

    public boolean hasFormatArgumentType() {
        return formatLength != null;
    }

    public boolean isLiteral() {
        return literal;
    }

    public void setLiteral(boolean literal) {
        this.literal = literal;
    }

    public byte[] getLiteralBytes() {
        return literalBytes;
    }

    public void setLiteralBytes(byte[] literalBytes) {
        this.literalBytes = literalBytes;
    }

    public Integer getAbsoluteArgumentIndex() {
        return absoluteArgumentIndex;
    }

    public void setAbsoluteArgumentIndex(Integer absoluteArgumentIndex) {
        this.absoluteArgumentIndex = absoluteArgumentIndex;
    }

    public boolean isArgWidth() {
        return argWidth;
    }

    public void setArgWidth(boolean argWidth) {
        this.argWidth = argWidth;
    }


    public boolean isPrecisionVisited() {
        return precisionVisited;
    }

    public void setPrecisionVisited(boolean precisionVisited) {
        this.precisionVisited = precisionVisited;
    }

    public boolean isPrecisionArg() {
        return precisionArg;
    }

    public void setPrecisionArg(boolean precisionArg) {
        this.precisionArg = precisionArg;
    }

    public boolean hasFlags() {
        return literal || precision != null || precisionVisited || width != null || hasSpace || fsharp || plus ||
                minus || zero || precisionStar || widthStar || formatType != null;
    }
}
