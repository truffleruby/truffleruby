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
package org.truffleruby.core.numeric;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.FloatToIntegerNode;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

@GenerateInline
@GenerateCached(false)
public abstract class GeneralDivModNode extends RubyBaseNode {

    public abstract RubyArray execute(Node node, Object a, Object b);

    @Specialization
    static RubyArray doLongs(Node node, long a, long b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Exclusive InlinedBranchProfile bMinusOneProfile,
            @Cached @Exclusive InlinedBranchProfile useFixnumPairProfile,
            @Cached @Exclusive InlinedBranchProfile useObjectPairProfile,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumQuotient) {
        if (b == 0) {
            bZeroProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).zeroDivisionError(node));
        }

        long mod;
        Object integerDiv;

        if (b == -1) {
            bMinusOneProfile.enter(node);

            if (a == Long.MIN_VALUE) {
                integerDiv = BigIntegerOps.negate(a);
            } else {
                integerDiv = -a;
            }
            mod = 0;
        } else {
            long div = a / b;
            mod = a - b * div;
            if (mod < 0 && b > 0 || mod > 0 && b < 0) {
                div -= 1;
                mod += b;
            }
            integerDiv = div;
        }

        if (integerDiv instanceof Long && CoreLibrary.fitsIntoInteger((long) integerDiv) &&
                CoreLibrary.fitsIntoInteger(mod)) {
            useFixnumPairProfile.enter(node);
            return createArray(node, new int[]{ (int) (long) integerDiv, (int) mod });
        } else if (integerDiv instanceof Long) {
            useObjectPairProfile.enter(node);
            return createArray(node, new long[]{ (long) integerDiv, mod });
        } else {
            useObjectPairProfile.enter(node);
            return createArray(node, new Object[]{
                    fixnumOrBignumQuotient.execute(node, (BigInteger) integerDiv),
                    mod
            });
        }
    }

    @Specialization
    static RubyArray doLongAndBigInt(Node node, long a, BigInteger b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile bigIntegerFixnumProfile,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumQuotient,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumRemainder) {
        return divMod(node, BigIntegerOps.valueOf(a), b, bZeroProfile, bigIntegerFixnumProfile, fixnumOrBignumQuotient,
                fixnumOrBignumRemainder);
    }

    @Specialization
    static RubyArray doLongAndDouble(Node node, long a, double b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile nanProfile,
            @Cached @Shared FloatToIntegerNode floatToIntegerNode) {
        return divMod(node, a, b, bZeroProfile, nanProfile, floatToIntegerNode);
    }

    @Specialization
    static RubyArray doBigIntAndLong(Node node, BigInteger a, long b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile bigIntegerFixnumProfile,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumQuotient,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumRemainder) {
        return divMod(node, a, BigIntegerOps.valueOf(b), bZeroProfile, bigIntegerFixnumProfile, fixnumOrBignumQuotient,
                fixnumOrBignumRemainder);
    }

    @Specialization
    static RubyArray doBigInts(Node node, BigInteger a, BigInteger b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile bigIntegerFixnumProfile,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumQuotient,
            @Cached @Shared FixnumOrBignumNode fixnumOrBignumRemainder) {
        return divMod(node, a, b, bZeroProfile, bigIntegerFixnumProfile, fixnumOrBignumQuotient,
                fixnumOrBignumRemainder);
    }

    @Specialization
    static RubyArray doBigIntAndDouble(Node node, BigInteger a, double b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile nanProfile,
            @Cached @Shared FloatToIntegerNode floatToIntegerNode) {
        return divMod(node, BigIntegerOps.doubleValue(a), b, bZeroProfile, nanProfile, floatToIntegerNode);
    }

    @Specialization
    static RubyArray doDoubleAndLong(Node node, double a, long b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile nanProfile,
            @Cached @Shared FloatToIntegerNode floatToIntegerNode) {
        return divMod(node, a, b, bZeroProfile, nanProfile, floatToIntegerNode);
    }

    @Specialization
    static RubyArray doDoubleAndBigInt(Node node, double a, BigInteger b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile nanProfile,
            @Cached @Shared FloatToIntegerNode floatToIntegerNode) {
        return divMod(node, a, BigIntegerOps.doubleValue(b), bZeroProfile, nanProfile, floatToIntegerNode);
    }

    @Specialization
    static RubyArray doDoubles(Node node, double a, double b,
            @Cached @Shared InlinedBranchProfile bZeroProfile,
            @Cached @Shared InlinedBranchProfile nanProfile,
            @Cached @Shared FloatToIntegerNode floatToIntegerNode) {
        return divMod(node, a, b, bZeroProfile, nanProfile, floatToIntegerNode);
    }

    /* div-mod algorithms copied from org.jruby.RubyFixnum, org.jruby.RubyBignum and org.jrubyRubyFloat. See license and
     * contributors there. */

    @TruffleBoundary
    private static RubyArray divMod(Node node, double a, double b, InlinedBranchProfile bZeroProfile,
            InlinedBranchProfile nanProfile,
            FloatToIntegerNode floatToIntegerNode) {
        if (b == 0) {
            bZeroProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).zeroDivisionError(node));
        }

        double mod = Math.IEEEremainder(a, b);

        if (Double.isNaN(mod)) {
            nanProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).floatDomainError("NaN", node));
        }

        final double div = Math.floor(a / b);

        if (b * mod < 0) {
            mod += b;
        }

        return createArray(node, new Object[]{ floatToIntegerNode.execute(node, div), mod });
    }

    @TruffleBoundary
    private static RubyArray divMod(Node node, BigInteger a, BigInteger b, InlinedBranchProfile bZeroProfile,
            InlinedBranchProfile bigIntegerFixnumProfile, FixnumOrBignumNode fixnumOrBignumQuotient,
            FixnumOrBignumNode fixnumOrBignumRemainder) {
        if (b.signum() == 0) {
            bZeroProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).zeroDivisionError(node));
        }

        final BigInteger[] bigIntegerResults = a.divideAndRemainder(b);

        if ((a.signum() * b.signum()) == -1 && bigIntegerResults[1].signum() != 0) {
            bigIntegerFixnumProfile.enter(node);
            bigIntegerResults[0] = bigIntegerResults[0].subtract(BigInteger.ONE);
            bigIntegerResults[1] = b.add(bigIntegerResults[1]);
        }

        return createArray(node, new Object[]{
                fixnumOrBignumQuotient.execute(node, bigIntegerResults[0]),
                fixnumOrBignumRemainder.execute(node, bigIntegerResults[1]) });
    }

}
