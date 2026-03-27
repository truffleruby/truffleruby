/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import static org.truffleruby.cext.ValueWrapperManager.FALSE_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.MAX_FIXNUM_VALUE;
import static org.truffleruby.cext.ValueWrapperManager.MIN_FIXNUM_VALUE;
import static org.truffleruby.cext.ValueWrapperManager.NIL_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.TRUE_HANDLE;

import java.math.BigInteger;

import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.core.numeric.BignumOperations;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.core.numeric.RubyBignum;

/**
 * <pre>
 * Object IDs distribution
 *
 * We try to respect MRI scheme when it makes sense (Fixnum for the moment).
 * Have a look at include/ruby/ruby.h below ruby_special_consts.
 *
 * Encoding for Fixnum (long):
 * ... 0000 = false
 * ... 0010 = true
 * ... 0100 = nil
 *
 * ... xxx1 = Fixnum of value (id-1)/2 if -2^62 <= value < 2^62
 * ... xxx0 = BasicObject generated id (for id > 4)
 *
 * Encoding for Bignum:
 * ... 0001 | 64-bit long = Fixnum if value < -2^62 or value >= 2^62
 * ... 0010 | 64-bit raw double bits = Float
 * </pre>
 */
public abstract class ObjectIDOperations {

    public static final long FALSE = FALSE_HANDLE;
    public static final long TRUE = TRUE_HANDLE;
    public static final long NIL = NIL_HANDLE;

    private static final BigInteger LARGE_FIXNUM_FLAG = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger FLOAT_FLAG = BigInteger.ONE.shiftLeft(65);

    // primitive => ID

    /** Whether the long fits as a tagged pointer (fixnum) in C */
    public static boolean isSmallFixnum(long fixnum) {
        // TODO: optimize
        return MIN_FIXNUM_VALUE <= fixnum && fixnum <= MAX_FIXNUM_VALUE;
    }

    /** This constant (typed as long) is needed in order to have compatible bytecode between JDK8 and JDK11. In JDK9+,
     * there is a new Math.multiplyExact(long, int) method, which does not exist on JDK8. We avoid using it purposefully
     * to avoid this incompatibility. */
    private static final long TWO_AS_LONG = 2;

    public static long smallFixnumToIDOverflow(long fixnum) throws ArithmeticException {
        return Math.addExact(Math.multiplyExact(fixnum, TWO_AS_LONG), 1);
    }

    public static long smallFixnumToID(long fixnum) {
        assert isSmallFixnum(fixnum);
        return fixnum * 2 + 1;
    }

    @TruffleBoundary // BigInteger
    public static RubyBignum largeFixnumToID(long fixnum) {
        assert !isSmallFixnum(fixnum);
        BigInteger big = unsignedBigInteger(fixnum);
        return BignumOperations.createBignum(big.or(LARGE_FIXNUM_FLAG));
    }

    @TruffleBoundary // BigInteger
    public static RubyBignum floatToID(double value) {
        long bits = Double.doubleToRawLongBits(value);
        BigInteger big = unsignedBigInteger(bits);
        return BignumOperations.createBignum(big.or(FLOAT_FLAG));
    }

    // ID => primitive

    public static boolean isSmallFixnumID(long id) {
        return id % 2 != 0;
    }

    public static long toFixnum(long id) {
        return (id - 1) / 2;
    }

    @TruffleBoundary // BigInteger
    public static boolean isLargeFixnumID(BigInteger id) {
        return !id.and(LARGE_FIXNUM_FLAG).equals(BigInteger.ZERO);
    }

    @TruffleBoundary // BigInteger
    public static boolean isFloatID(BigInteger id) {
        return !id.and(FLOAT_FLAG).equals(BigInteger.ZERO);
    }

    public static boolean isBasicObjectID(long id) {
        return id != FALSE && (id & ValueWrapperManager.IMMEDIATE_MASK) == 0;
    }

    @TruffleBoundary // BigInteger
    private static BigInteger unsignedBigInteger(long value) {
        BigInteger big = BigInteger.valueOf(value);
        if (value < 0) {
            big = new BigInteger(1, big.toByteArray()); // consider as unsigned
        }
        return big;
    }

}
