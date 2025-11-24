/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.numeric;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.ImmutableRubyObjectNotCopyable;

import java.math.BigDecimal;
import java.math.BigInteger;

@ExportLibrary(InteropLibrary.class)
public final class RubyBignum extends ImmutableRubyObjectNotCopyable {

    private static final BigInteger LONG_MIN_BIGINT = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);

    public final BigInteger value;

    public RubyBignum(BigInteger value) {
        assert value.compareTo(LONG_MIN_BIGINT) < 0 ||
                value.compareTo(LONG_MAX_BIGINT) > 0 : "Bignum in long range : " + value;
        this.value = value;
    }

    @TruffleBoundary
    private int bitLength() {
        return value.bitLength();
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return value.toString();
    }

    // region InteropLibrary messages
    @Override
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().integerClass;
    }
    // endregion

    // region Number messages
    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInByte() {
        return bitLength() < Byte.SIZE;
    }

    @ExportMessage
    boolean fitsInShort() {
        return bitLength() < Short.SIZE;
    }

    @ExportMessage
    boolean fitsInInt() {
        return bitLength() < Integer.SIZE;
    }

    @ExportMessage
    boolean fitsInLong() {
        return bitLength() < Long.SIZE;
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @TruffleBoundary
    @ExportMessage
    boolean fitsInFloat() {
        if (bitLength() <= 24) { // 24 = size of float mantissa + 1
            return true;
        } else {
            float floatValue = value.floatValue();
            if (!Float.isFinite(floatValue)) {
                return false;
            }
            return new BigDecimal(floatValue).toBigIntegerExact().equals(value);
        }
    }

    @TruffleBoundary
    @ExportMessage
    boolean fitsInDouble() {
        if (bitLength() <= 53) { // 53 = size of double mantissa + 1
            return true;
        } else {
            double doubleValue = value.doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return false;
            }
            return new BigDecimal(doubleValue).toBigIntegerExact().equals(value);
        }
    }

    @TruffleBoundary
    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        try {
            return value.byteValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        try {
            return value.shortValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        if (fitsInFloat()) {
            return value.floatValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (fitsInDouble()) {
            return value.doubleValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value;
    }
    // endregion
}
