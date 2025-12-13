/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.BigIntegerCastNode;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.FloatToIntegerNode;
import org.truffleruby.core.cast.ToRubyIntegerNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.NoImplicitCastsToLong;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.WarnNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.yield.CallBlockNode;

@CoreModule(value = "Integer", isClass = true)
public abstract class IntegerNodes {

    @CoreMethod(names = "-@")
    public abstract static class NegNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeNeg(Object a);

        @Specialization(rewriteOn = ArithmeticException.class)
        int doInt(int value) {
            return Math.negateExact(value);
        }

        @Specialization(replaces = "doInt")
        Object doIntWithOverflow(int value) {
            if (value == Integer.MIN_VALUE) {
                return -((long) value);
            }
            return -value;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLong(long value) {
            return Math.subtractExact(0, value);
        }

        @Specialization(replaces = "doLong")
        Object doLongWithOverflow(long value,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.negate(value));
        }

        @Specialization
        Object doObject(RubyBignum value,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.negate(value.value));
        }

    }

    /** See {@link org.truffleruby.core.inlined.InlinedAddNode} */
    @CoreMethod(names = "+", required = 1)
    public abstract static class AddNode extends CoreMethodArrayArgumentsNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int a, int b) {
            return Math.addExact(a, b);
        }

        @Specialization
        long addWithOverflow(int a, int b) {
            return (long) a + (long) b;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long add(long a, long b) {
            return Math.addExact(a, b);
        }

        @Specialization
        Object addWithOverflow(long a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.add(a, b));
        }

        @Specialization
        double add(long a, double b) {
            return a + b;
        }

        @Specialization
        Object add(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.add(b.value, a));
        }

        @Specialization
        Object add(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.add(a.value, b));
        }

        @Specialization
        double add(RubyBignum a, double b) {
            return BigIntegerOps.doubleValue(a.value) + b;
        }

        @Specialization
        Object add(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.add(a.value, b.value));
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object addCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().PLUS, b);
        }
    }

    /** See {@link org.truffleruby.core.inlined.InlinedSubNode} */
    @CoreMethod(names = "-", required = 1)
    public abstract static class SubNode extends CoreMethodArrayArgumentsNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int sub(int a, int b) {
            return Math.subtractExact(a, b);
        }

        @Specialization
        long subWithOverflow(int a, int b) {
            return (long) a - (long) b;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long sub(long a, long b) {
            return Math.subtractExact(a, b);
        }

        @Specialization
        Object subWithOverflow(long a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.subtract(a, b));
        }

        @Specialization
        double sub(long a, double b) {
            return a - b;
        }

        @Specialization
        Object sub(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.subtract(a, b.value));
        }

        @Specialization
        Object sub(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.subtract(a.value, b));
        }

        @Specialization
        double sub(RubyBignum a, double b) {
            return BigIntegerOps.doubleValue(a.value) - b;
        }

        @Specialization
        Object sub(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.subtract(a.value, b.value));
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object subCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MINUS, b);
        }

    }

    @CoreMethod(names = "*", required = 1)
    public abstract static class MulNode extends CoreMethodArrayArgumentsNode {

        @NeverDefault
        public static MulNode create() {
            return IntegerNodesFactory.MulNodeFactory.create(null);
        }

        public abstract Object executeMul(Object a, Object b);

        @Specialization(rewriteOn = ArithmeticException.class)
        int mul(int a, int b) {
            return Math.multiplyExact(a, b);
        }

        @Specialization
        long mulWithOverflow(int a, int b) {
            return (long) a * (long) b;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long mul(long a, long b) {
            return Math.multiplyExact(a, b);
        }

        @Specialization
        Object mulWithOverflow(long a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.multiply(a, b));
        }

        @Specialization
        double mul(long a, double b) {
            return a * b;
        }

        @Specialization
        Object mul(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.multiply(b.value, a));
        }

        @Specialization
        Object mul(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.multiply(a.value, b));
        }

        @Specialization
        double mul(RubyBignum a, double b) {
            return BigIntegerOps.doubleValue(a.value) * b;
        }

        @Specialization
        Object mul(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.multiply(a.value, b.value));
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object mul(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MULTIPLY, b);
        }

    }

    @Primitive(name = "integer_fdiv")
    public abstract static class FDivNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        double fDivIntInt(int num, int den) {
            return ((double) num) / den;
        }

        @Specialization
        double fDivLongLong(long num, long den) {
            return ((double) num) / den;
        }

        @TruffleBoundary
        @Specialization
        double fDivLongBig(long num, RubyBignum den) {
            return new BigDecimal(num).divide(new BigDecimal(den.value), 323, RoundingMode.HALF_UP).doubleValue();
        }

        @TruffleBoundary
        @Specialization
        double fDivBigLong(RubyBignum num, long den) {
            return new BigDecimal(num.value).divide(new BigDecimal(den), 323, RoundingMode.HALF_UP).doubleValue();
        }

        @TruffleBoundary
        @Specialization
        double fDivBigBig(RubyBignum num, RubyBignum den) {
            return new BigDecimal(num.value).divide(new BigDecimal(den.value), 323, RoundingMode.HALF_UP).doubleValue();
        }

    }

    @CoreMethod(names = "/", required = 1)
    public abstract static class DivNode extends CoreMethodArrayArgumentsNode {

        private final BranchProfile bGreaterZero = BranchProfile.create();
        private final BranchProfile bGreaterZeroAGreaterEqualZero = BranchProfile.create();
        private final BranchProfile bGreaterZeroALessZero = BranchProfile.create();
        private final BranchProfile aGreaterZero = BranchProfile.create();
        private final BranchProfile bMinusOne = BranchProfile.create();
        private final BranchProfile bMinusOneAMinimum = BranchProfile.create();
        private final BranchProfile bMinusOneANotMinimum = BranchProfile.create();
        private final BranchProfile finalCase = BranchProfile.create();

        public abstract Object executeDiv(Object a, Object b);

        // int

        @Specialization(rewriteOn = ArithmeticException.class)
        Object divInt(int a, int b) {
            if (b > 0) {
                bGreaterZero.enter();
                if (a >= 0) {
                    bGreaterZeroAGreaterEqualZero.enter();
                    return a / b;
                } else {
                    bGreaterZeroALessZero.enter();
                    return (a + 1) / b - 1;
                }
            } else if (a > 0) {
                aGreaterZero.enter();
                return (a - 1) / b - 1;
            } else if (b == -1) {
                bMinusOne.enter();
                if (a == Integer.MIN_VALUE) {
                    bMinusOneAMinimum.enter();
                    return -((long) Integer.MIN_VALUE);
                } else {
                    bMinusOneANotMinimum.enter();
                    return -a;
                }
            } else {
                finalCase.enter();
                return a / b;
            }
        }

        @Specialization
        Object divIntFallback(int a, int b,
                @Cached @Shared InlinedConditionProfile zeroProfile) {
            if (zeroProfile.profile(this, b == 0)) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            } else {
                return divInt(a, b);
            }
        }

        // long

        @Specialization(rewriteOn = ArithmeticException.class)
        Object divLong(long a, long b) {
            if (b > 0) {
                bGreaterZero.enter();
                if (a >= 0) {
                    bGreaterZeroAGreaterEqualZero.enter();
                    return a / b;
                } else {
                    bGreaterZeroALessZero.enter();
                    return (a + 1) / b - 1;
                }
            } else if (a > 0) {
                aGreaterZero.enter();
                return (a - 1) / b - 1;
            } else if (b == -1) {
                bMinusOne.enter();
                if (a == Long.MIN_VALUE) {
                    bMinusOneAMinimum.enter();
                    return createBignum(BigIntegerOps.negate(a));
                } else {
                    bMinusOneANotMinimum.enter();
                    return -a;
                }
            } else {
                finalCase.enter();
                return a / b;
            }
        }

        @Specialization
        Object divLongFallback(long a, long b,
                @Cached @Shared InlinedConditionProfile zeroProfile) {
            if (zeroProfile.profile(this, b == 0)) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            } else {
                return divLong(a, b);
            }
        }

        @Specialization
        double div(long a, double b) {
            return a / b;
        }

        @Specialization
        Object divBignum(long a, RubyBignum b) {
            return (a == 0 || Long.signum(a) == b.value.signum()) ? 0 : -1;
        }

        // Bignum

        @TruffleBoundary
        @Specialization
        Object div(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            if (b == 0) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }
            final BigInteger bBigInt = BigInteger.valueOf(b);
            final BigInteger aBigInt = a.value;
            final BigInteger result = aBigInt.divide(bBigInt);
            if (result.signum() == -1 && !aBigInt.mod(bBigInt.abs()).equals(BigInteger.ZERO)) {
                return fixnumOrBignumNode.execute(this, result.subtract(BigInteger.ONE));
            } else {
                return fixnumOrBignumNode.execute(this, result);
            }
        }

        @Specialization
        double div(RubyBignum a, double b) {
            return BigIntegerOps.doubleValue(a) / b;
        }

        @TruffleBoundary
        @Specialization
        Object div(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            final BigInteger aBigInt = a.value;
            final BigInteger bBigInt = b.value;
            final BigInteger result = aBigInt.divide(bBigInt);
            if (result.signum() == -1 && !aBigInt.mod(bBigInt.abs()).equals(BigInteger.ZERO)) {
                return fixnumOrBignumNode.execute(this, result.subtract(BigInteger.ONE));
            } else {
                return fixnumOrBignumNode.execute(this, result);
            }
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object divCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().DIVIDE, b);
        }

    }

    // Defined in Java as we need to statically call #/
    @CoreMethod(names = "div", required = 1)
    public abstract static class IDivNode extends CoreMethodArrayArgumentsNode {

        @Child private DivNode divNode = IntegerNodesFactory.DivNodeFactory.create(null);

        @Specialization
        Object idiv(Object a, Object b,
                @Cached InlinedConditionProfile zeroProfile,
                @Cached FloatToIntegerNode floatToIntegerNode,
                @Cached DispatchNode floorNode) {
            Object quotient = divNode.executeDiv(a, b);
            if (quotient instanceof Double) {
                if (zeroProfile.profile(this, (double) b == 0.0)) {
                    throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
                }
                return floatToIntegerNode.execute(this, Math.floor((double) quotient));
            } else if (RubyGuards.isRubyInteger(quotient)) {
                return quotient;
            } else {
                return floorNode.call(quotient, "floor");
            }
        }

    }

    // Splitting: inline cache
    @CoreMethod(names = { "%", "modulo" }, required = 1, split = Split.ALWAYS)
    public abstract static class ModNode extends CoreMethodArrayArgumentsNode {

        private final BranchProfile adjustProfile = BranchProfile.create();

        public abstract Object executeMod(Object a, Object b);

        @Specialization(guards = { "a >= 0", "b == cachedB", "isPowerOfTwo(cachedB)" },
                limit = "getDefaultCacheLimit()")
        int modPowerOfTwo(int a, int b,
                @Cached("b") int cachedB) {
            return a & (cachedB - 1);
        }

        @Idempotent
        protected static boolean isPowerOfTwo(int n) {
            return n > 0 && (n & (n - 1)) == 0;
        }

        @Specialization(replaces = "modPowerOfTwo")
        int mod(int a, int b,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            if (b == 0) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }

            int mod = a % b;

            if (mod < 0 && b > 0 || mod > 0 && b < 0) {
                adjustProfile.enter();
                mod += b;
            }

            return mod;
        }

        @Specialization
        double mod(long a, double b,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            if (b == 0) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }

            double mod = a % b;

            if (mod < 0 && b > 0 || mod > 0 && b < 0) {
                adjustProfile.enter();
                mod += b;
            }

            return mod;
        }

        @Specialization
        long mod(long a, long b,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            if (b == 0) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }

            long mod = a % b;

            if (mod < 0 && b > 0 || mod > 0 && b < 0) {
                adjustProfile.enter();
                mod += b;
            }

            return mod;
        }

        @Specialization
        Object mod(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            // TODO(CS): why are we getting this case?

            if (a < 0 != b.value.signum() < 0) {
                adjustProfile.enter();
                return fixnumOrBignumNode.execute(this, BigIntegerOps.add(b.value, a));
            }

            return a;
        }

        @TruffleBoundary
        @Specialization
        Object mod(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            if (b == 0) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            } else if (b < 0) {
                final BigInteger bigint = BigInteger.valueOf(b);
                final BigInteger mod = a.value.mod(bigint.negate());
                return fixnumOrBignumNode.execute(this, mod.add(bigint));
            }
            return fixnumOrBignumNode.execute(this, a.value.mod(BigInteger.valueOf(b)));
        }

        @TruffleBoundary // exception throw + BigInteger
        @Specialization
        double mod(RubyBignum a, double b) {
            if (b == 0) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }

            double mod = a.value.doubleValue() % b;

            if (mod < 0 && b > 0 || mod > 0 && b < 0) {
                mod += b;
            }

            return mod;
        }

        @TruffleBoundary
        @Specialization
        Object mod(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            final BigInteger bigint = b.value;
            final int compare = bigint.compareTo(BigInteger.ZERO);
            if (compare == 0) {
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            } else if (compare < 0) {
                final BigInteger mod = a.value.mod(bigint.negate());
                return fixnumOrBignumNode.execute(this, mod.add(bigint));
            }
            return fixnumOrBignumNode.execute(this, a.value.mod(b.value));
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object modCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MODULO, b);
        }

    }

    @Primitive(name = "integer_divmod")
    public abstract static class DivModNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyArray divMod(long a, long b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b);
        }

        @Specialization
        RubyArray divMod(long a, RubyBignum b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b.value);
        }

        @Specialization
        RubyArray divMod(long a, double b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b);
        }

        @Specialization
        RubyArray divMod(RubyBignum a, long b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a.value, b);
        }

        @Specialization
        RubyArray divMod(RubyBignum a, double b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a.value, b);
        }

        @Specialization
        RubyArray divMod(RubyBignum a, RubyBignum b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a.value, b.value);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object divModOther(Object a, Object b) {
            return FAILURE;
        }

    }

    @CoreMethod(names = "<", required = 1)
    public abstract static class LessNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean less(int a, int b) {
            return a < b;
        }

        @Specialization
        boolean less(long a, long b) {
            return a < b;
        }

        @Specialization
        boolean less(long a, double b) {
            return a < b;
        }

        @Specialization
        boolean less(long a, RubyBignum b) {
            return BigIntegerOps.isPositive(b); // Bignums are never long-valued.
        }

        @Specialization
        boolean less(RubyBignum a, long b) {
            return BigIntegerOps.isNegative(a); // Bignums are never long-valued.
        }

        @Specialization
        boolean less(RubyBignum a, double b) {
            return BigIntegerOps.less(a, b);
        }

        @Specialization
        boolean less(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.compare(a, b) < 0;
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object lessCoerced(Object a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().LESS_THAN, b);
        }
    }

    @CoreMethod(names = "<=", required = 1)
    public abstract static class LessEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean lessEqual(int a, int b) {
            return a <= b;
        }

        @Specialization
        boolean lessEqual(long a, long b) {
            return a <= b;
        }

        @Specialization
        boolean lessEqual(long a, double b) {
            return a <= b;
        }

        @Specialization
        boolean lessEqual(long a, RubyBignum b) {
            return BigIntegerOps.isPositive(b); // Bignums are never long-valued.
        }

        @Specialization
        boolean lessEqual(RubyBignum a, long b) {
            return BigIntegerOps.isNegative(a); // Bignums are never long-valued.
        }

        @Specialization
        boolean lessEqual(RubyBignum a, double b) {
            return BigIntegerOps.lessEqual(a, b);
        }

        @Specialization
        boolean lessEqual(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.compare(a, b) <= 0;
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object lessEqualCoerced(Object a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().LEQ, b);
        }

    }

    @GenerateUncached
    @CoreMethod(names = { "==", "===" }, required = 1, alwaysInlined = true)
    public abstract static class EqualNode extends AlwaysInlinedMethodNode {

        @Specialization
        static Object equal(Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached EqualInternalNode equalInternalNode,
                @Bind Node node) {
            Object other = RubyArguments.getArgument(rubyArgs, 0);
            return equalInternalNode.execute(node, self, other);
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class EqualInternalNode extends RubyBaseNode {

        public abstract Object execute(Node node, Object a, Object b);

        @Specialization
        boolean equal(int a, int b) {
            return a == b;
        }

        @Specialization
        boolean equal(int a, RubyBignum b) {
            return false;
        }

        @Specialization
        boolean equal(long a, long b) {
            return a == b;
        }

        @Specialization
        boolean equal(long a, double b) {
            return a == b;
        }

        @Specialization
        boolean equal(long a, RubyBignum b) {
            return false;
        }

        @Specialization
        boolean equal(RubyBignum a, long b) {
            return false;
        }

        @Specialization
        boolean equal(RubyBignum a, double b) {
            return BigIntegerOps.equal(a, b);
        }

        @Specialization
        boolean equal(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.equals(a.value, b.value);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        static Object equal(Node node, Object a, Object b,
                @Cached(inline = false) DispatchNode reverseCallNode,
                @Cached BooleanCastNode booleanCastNode) {
            final Object reversedResult = reverseCallNode.call(b, "==", a);
            return booleanCastNode.execute(node, reversedResult);
        }

    }

    @CoreMethod(names = "<=>", required = 1)
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int compare(int a, int b,
                @Cached @Shared InlinedConditionProfile smallerProfile,
                @Cached @Shared InlinedConditionProfile equalProfile) {
            if (smallerProfile.profile(this, a < b)) {
                return -1;
            } else if (equalProfile.profile(this, a == b)) {
                return 0;
            } else {
                return +1;
            }
        }

        @Specialization
        int compare(long a, long b,
                @Cached @Shared InlinedConditionProfile smallerProfile,
                @Cached @Shared InlinedConditionProfile equalProfile) {
            if (smallerProfile.profile(this, a < b)) {
                return -1;
            } else if (equalProfile.profile(this, a == b)) {
                return 0;
            } else {
                return +1;
            }
        }

        @Specialization(guards = "isNaN(b)")
        Object compareNaN(long a, double b) {
            return nil;
        }

        @Specialization(guards = "!isNaN(b)")
        int compare(long a, double b,
                @Cached @Exclusive InlinedConditionProfile equalProfile) {
            return FloatNodes.CompareNode.compareDoubles(a, b, equalProfile, this);
        }

        @Specialization
        int compare(long a, RubyBignum b) {
            return BigIntegerOps.compare(a, b);
        }

        @Specialization
        int compare(RubyBignum a, long b) {
            return BigIntegerOps.compare(a, b);
        }

        @Specialization(guards = "isNaN(b)")
        Object compareNaN(RubyBignum a, double b) {
            return nil;
        }

        @Specialization(guards = "!isNaN(b)")
        int compare(RubyBignum a, double b) {
            return BigIntegerOps.compare(a.value, b);
        }

        @Specialization
        int compare(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.compare(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object compare(Object a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare_no_error", b);
        }

    }

    @CoreMethod(names = ">=", required = 1)
    public abstract static class GreaterEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean greaterEqual(int a, int b) {
            return a >= b;
        }

        @Specialization
        boolean greaterEqual(long a, long b) {
            return a >= b;
        }

        @Specialization
        boolean greaterEqual(long a, double b) {
            return a >= b;
        }

        @Specialization
        boolean greaterEqual(long a, RubyBignum b) {
            return BigIntegerOps.isNegative(b); // Bignums are never long-valued.
        }

        @Specialization
        boolean greaterEqual(RubyBignum a, long b) {
            return BigIntegerOps.isPositive(a); // Bignums are never long-valued.
        }

        @Specialization
        boolean greaterEqual(RubyBignum a, double b) {
            return BigIntegerOps.greaterEqual(a, b);
        }

        @Specialization
        boolean greaterEqual(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.compare(a, b) >= 0;
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object greaterEqualCoerced(Object a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().GEQ, b);
        }

    }

    @CoreMethod(names = ">", required = 1)
    public abstract static class GreaterNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean greater(int a, int b) {
            return a > b;
        }

        @Specialization
        boolean greater(long a, long b) {
            return a > b;
        }

        @Specialization
        boolean greater(long a, double b) {
            return a > b;
        }

        @Specialization
        boolean greater(long a, RubyBignum b) {
            return BigIntegerOps.isNegative(b); // Bignums are never long-valued.
        }

        @Specialization
        boolean greater(RubyBignum a, long b) {
            return BigIntegerOps.isPositive(a); // Bignums are never long-valued.
        }

        @Specialization
        boolean greater(RubyBignum a, double b) {
            return BigIntegerOps.greater(a, b);
        }

        @Specialization
        boolean greater(RubyBignum a, RubyBignum b) {
            return BigIntegerOps.compare(a, b) > 0;
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object greaterCoerced(Object a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().GREATER_THAN, b);
        }

    }

    @CoreMethod(names = "~")
    public abstract static class ComplementNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int complement(int n) {
            return ~n;
        }

        @Specialization
        long complement(long n) {
            return ~n;
        }

        @Specialization
        Object complement(RubyBignum value,
                @Cached FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.not(value.value));
        }

    }

    @TypeSystemReference(NoImplicitCastsToLong.class)
    @CoreMethod(names = "&", required = 1)
    public abstract static class BitAndNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeBitAnd(Object a, Object b);

        @Specialization
        int bitAndIntInt(int a, int b) {
            return a & b;
        }

        @Specialization(guards = "a >= 0")
        int bitAndIntLong(int a, long b) {
            return a & ((int) b);
        }

        @Specialization(guards = "a < 0")
        long bitAndIntLongNegative(int a, long b) {
            return a & b;
        }

        @Specialization(guards = "b >= 0")
        int bitAndLongInt(long a, int b) {
            return ((int) a) & b;
        }

        @Specialization(guards = "b < 0")
        long bitAndLongIntNegative(long a, int b) {
            return a & b;
        }

        @Specialization
        long bitAndLongLong(long a, long b) {
            return a & b;
        }

        @Specialization
        Object bitAndBignum(int a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.and(b.value, a));
        }

        @Specialization
        Object bitAndBignum(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.and(b.value, a));
        }

        @Specialization
        Object bitAnd(RubyBignum a, int b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.and(a.value, b));
        }

        @Specialization
        Object bitAnd(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.and(a.value, b));
        }

        @Specialization
        Object bitAnd(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.and(a.value, b.value));
        }

        @Specialization(guards = "!isRubyInteger(b)")
        Object bitAndCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_bit_coerced", coreSymbols().AMPERSAND, b);
        }

    }

    @CoreMethod(names = "|", required = 1)
    public abstract static class BitOrNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeBitOr(Object a, Object b);

        @Specialization
        int bitOr(int a, int b) {
            return a | b;
        }

        @Specialization
        long bitOr(long a, long b) {
            return a | b;
        }

        @Specialization
        Object bitOr(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.or(b.value, a));
        }

        @Specialization
        Object bitOr(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.or(a.value, b));
        }

        @Specialization
        Object bitOr(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.or(a.value, b.value));
        }

        @Specialization(guards = "!isRubyInteger(b)")
        Object bitOrCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_bit_coerced", coreSymbols().PIPE, b);
        }

    }

    @CoreMethod(names = "^", required = 1)
    public abstract static class BitXOrNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int bitXOr(int a, int b) {
            return a ^ b;
        }

        @Specialization
        long bitXOr(long a, long b) {
            return a ^ b;
        }

        @Specialization
        Object bitXOr(long a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.xor(b.value, a));
        }

        @Specialization
        Object bitXOr(RubyBignum a, long b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.xor(a.value, b));
        }

        @Specialization
        Object bitXOr(RubyBignum a, RubyBignum b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.xor(a.value, b.value));
        }

        @Specialization(guards = "!isRubyInteger(b)")
        Object bitXOrCoerced(Object a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_bit_coerced", coreSymbols().CIRCUMFLEX, b);
        }

    }

    @CoreMethod(names = "<<", required = 1, lowerFixnum = 1)
    public abstract static class LeftShiftNode extends CoreMethodArrayArgumentsNode {

        @Child private NegNode negNode;
        @Child private RightShiftNode rightShiftNode;

        static final long MAX_INT = Integer.MAX_VALUE;

        public static LeftShiftNode create() {
            return IntegerNodesFactory.LeftShiftNodeFactory.create(null);
        }

        public abstract Object executeLeftShift(Object a, Object b);

        // b >= 0

        @Specialization(guards = { "b >= 0", "canShiftIntoInt(a, b)" })
        int leftShift(int a, int b) {
            return a << b;
        }

        @Specialization(guards = { "b >= 0", "canShiftLongIntoInt(a, b)" })
        int leftShift(long a, int b) {
            return (int) (a << b);
        }

        @Specialization(guards = { "b >= 0", "canShiftIntoLong(a, b)" })
        long leftShiftToLong(long a, int b) {
            return a << b;
        }

        @Specialization(guards = "b >= 0")
        Object leftShiftWithOverflow(long a, int b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            if (canShiftIntoLong(a, b)) {
                return leftShiftToLong(a, b);
            } else {
                return fixnumOrBignumNode.execute(this, BigIntegerOps.shiftLeft(a, b));
            }
        }

        @Specialization(guards = "b >= 0")
        Object leftShift(RubyBignum a, int b,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.shiftLeft(a.value, b));
        }

        @Specialization(guards = "b > MAX_INT")
        int leftShift(long a, long b,
                @Cached @Shared InlinedConditionProfile zeroProfile) {
            if (zeroProfile.profile(this, a == 0L)) {
                return 0;
            } else {
                throw shiftWidthTooBig();
            }
        }

        @Specialization(guards = "b > MAX_INT")
        int leftShift(RubyBignum a, long b) {
            // We raise a NoMemoryError like MRI; JRuby would raise a coercion error.
            throw shiftWidthTooBig();
        }

        @Specialization(guards = "isPositive(b)")
        int leftShift(long a, RubyBignum b,
                @Cached @Shared InlinedConditionProfile zeroProfile) {
            if (zeroProfile.profile(this, a == 0L)) {
                return 0;
            } else {
                throw shiftWidthTooBig();
            }
        }

        @Specialization(guards = "isPositive(b)")
        int leftShift(RubyBignum a, RubyBignum b) {
            // We raise a NoMemoryError like MRI; JRuby would raise a coercion error.
            throw shiftWidthTooBig();
        }

        private RaiseException shiftWidthTooBig() {
            return new RaiseException(getContext(), coreExceptions().rangeError("shift width too big", this));
        }

        // b < 0, delegate to a >> -b

        @Specialization(guards = "b < 0")
        Object leftShiftNeg(Object a, int b) {
            return negateAndRightShift(a, b);
        }

        @Specialization(guards = "b < 0")
        Object leftShiftNeg(Object a, long b) {
            return negateAndRightShift(a, b);
        }

        @Specialization(guards = "!isPositive(b)")
        Object leftShiftNeg(Object a, RubyBignum b) {
            return negateAndRightShift(a, b);
        }

        // Coercion

        @Specialization(guards = "!isRubyInteger(b)")
        static Object leftShiftCoerced(Object a, Object b,
                @Cached ToRubyIntegerNode toRubyIntNode,
                @Cached LeftShiftNode leftShiftNode,
                @Bind Node node) {
            return leftShiftNode.executeLeftShift(a, toRubyIntNode.execute(node, b));
        }

        private Object negateAndRightShift(Object a, Object b) {
            if (rightShiftNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightShiftNode = insert(IntegerNodesFactory.RightShiftNodeFactory.create(null));
            }

            if (negNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                negNode = insert(IntegerNodesFactory.NegNodeFactory.create(null));
            }

            final Object negated = negNode.executeNeg(b);
            return rightShiftNode.executeRightShift(a, negated);
        }

        static boolean canShiftIntoInt(int a, int b) {
            return Integer.numberOfLeadingZeros(a) - b > 0;
        }

        static boolean canShiftLongIntoInt(long a, int b) {
            return Long.numberOfLeadingZeros(a) - 32 - b > 0;
        }

        static boolean canShiftIntoLong(long a, int b) {
            return Long.numberOfLeadingZeros(a) - b > 0;
        }

        static boolean isPositive(RubyBignum b) {
            return b.value.signum() >= 0;
        }
    }

    @CoreMethod(names = ">>", required = 1, lowerFixnum = 1)
    public abstract static class RightShiftNode extends CoreMethodArrayArgumentsNode {

        @Child private NegNode negNode;
        @Child private LeftShiftNode leftShiftNode;

        static final long MAX_INT = Integer.MAX_VALUE;

        public static RightShiftNode create() {
            return IntegerNodesFactory.RightShiftNodeFactory.create(null);
        }

        public abstract Object executeRightShift(Object a, Object b);

        // b >= 0

        @Specialization(guards = "b >= 0")
        int rightShift(int a, int b,
                @Cached @Exclusive InlinedConditionProfile profile) {
            if (profile.profile(this, b >= Integer.SIZE - 1)) {
                return a < 0 ? -1 : 0;
            } else {
                return a >> b;
            }
        }

        @Specialization(guards = "b >= 0")
        Object rightShift(long a, int b,
                @Cached @Exclusive InlinedConditionProfile profile) {
            if (profile.profile(this, b >= Long.SIZE - 1)) {
                return a < 0 ? -1 : 0; // int
            } else {
                return a >> b; // long
            }
        }

        @Specialization(guards = "b >= 0")
        Object rightShift(RubyBignum a, int b,
                @Cached FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.shiftRight(a.value, b));
        }

        @Specialization(guards = "b > MAX_INT")
        int rightShift(long a, long b) {
            return a < 0 ? -1 : 0;
        }

        @Specialization(guards = "b > MAX_INT")
        int rightShift(RubyBignum a, long b) {
            return a.value.signum() < 0 ? -1 : 0;
        }

        @Specialization(guards = "isPositive(b)")
        int rightShift(long a, RubyBignum b) {
            return a < 0 ? -1 : 0;
        }

        @Specialization(guards = "isPositive(b)")
        int rightShift(RubyBignum a, RubyBignum b) {
            return a.value.signum() < 0 ? -1 : 0;
        }

        // b < 0, delegate to a << -b

        @Specialization(guards = "b < 0")
        Object rightShiftNeg(Object a, int b) {
            return negateAndLeftShift(a, b);
        }

        @Specialization(guards = "b < 0")
        Object rightShiftNeg(Object a, long b) {
            return negateAndLeftShift(a, b);
        }

        @Specialization(guards = "!isPositive(b)")
        Object rightShiftNeg(Object a, RubyBignum b) {
            return negateAndLeftShift(a, b);
        }

        // Coercion

        @Specialization(guards = "!isRubyInteger(b)")
        static Object rightShiftCoerced(Object a, Object b,
                @Cached ToRubyIntegerNode toRubyIntNode,
                @Cached RightShiftNode rightShiftNode,
                @Bind Node node) {
            return rightShiftNode.executeRightShift(a, toRubyIntNode.execute(node, b));
        }

        private Object negateAndLeftShift(Object a, Object b) {
            if (leftShiftNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                leftShiftNode = insert(IntegerNodesFactory.LeftShiftNodeFactory.create(null));
            }

            if (negNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                negNode = insert(IntegerNodesFactory.NegNodeFactory.create(null));
            }

            final Object negated = negNode.executeNeg(b);
            return leftShiftNode.executeLeftShift(a, negated);
        }

        static boolean isPositive(RubyBignum b) {
            return b.value.signum() >= 0;
        }
    }

    @CoreMethod(names = { "abs", "magnitude" })
    public abstract static class AbsNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeAbs(Object a);

        @Specialization(rewriteOn = ArithmeticException.class)
        int absIntInBounds(int n) {
            return (n < 0) ? Math.negateExact(n) : n;
        }

        @Specialization(replaces = "absIntInBounds")
        Object abs(int n) {
            if (n == Integer.MIN_VALUE) {
                return -((long) n);
            }
            return (n < 0) ? -n : n;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long absInBounds(long n) {
            return (n < 0) ? Math.subtractExact(0, n) : n;
        }

        @Specialization(replaces = "absInBounds")
        Object abs(long n) {
            if (n == Long.MIN_VALUE) {
                return createBignum(BigIntegerOps.abs(n));
            }
            return (n < 0) ? -n : n;
        }

        @Specialization
        Object abs(RubyBignum value,
                @Cached FixnumOrBignumNode fixnumOrBignumNode) {
            return fixnumOrBignumNode.execute(this, BigIntegerOps.abs(value.value));
        }

    }

    @CoreMethod(names = "bit_length")
    public abstract static class BitLengthNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int bitLength(int n) {
            if (n < 0) {
                n = ~n;
            }

            return Integer.SIZE - Integer.numberOfLeadingZeros(n);
        }

        @Specialization
        int bitLength(long n) {
            if (n < 0) {
                n = ~n;
            }

            return Long.SIZE - Long.numberOfLeadingZeros(n);
        }

        @Specialization
        int bitLength(RubyBignum value) {
            return BigIntegerOps.bitLength(value.value);
        }

    }

    @CoreMethod(names = "size")
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int size(long value) {
            return Long.BYTES;
        }

        @Specialization
        int size(RubyBignum value) {
            return (BigIntegerOps.bitLength(value.value) + 7) / 8;
        }

    }

    @CoreMethod(names = "to_f")
    public abstract static class ToFNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double toF(int n) {
            return n;
        }

        @Specialization
        double toF(long n) {
            return n;
        }

        @Specialization
        double toF(RubyBignum value) {
            return BigIntegerOps.doubleValue(value.value);
        }

    }

    @CoreMethod(names = { "to_s", "inspect" }, optional = 1, lowerFixnum = 1)
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString defaultBase10(long n, NotProvided base,
                @Cached @Shared TruffleString.FromLongNode fromLongNode) {
            var tstring = fromLongNode.execute(n, Encodings.US_ASCII.tencoding, true);
            return createString(tstring, Encodings.US_ASCII);
        }

        @TruffleBoundary
        @Specialization
        RubyString toS(RubyBignum value, NotProvided base,
                @Cached @Shared TruffleString.FromJavaStringNode fromJavaStringNode) {
            return createString(
                    fromJavaStringNode,
                    BigIntegerOps.toString(value.value),
                    Encodings.US_ASCII); // CR_7BIT
        }

        @Specialization(guards = "base == 10")
        RubyString base10(long n, int base,
                @Cached @Shared TruffleString.FromLongNode fromLongNode) {
            return defaultBase10(n, NotProvided.INSTANCE, fromLongNode);
        }

        @TruffleBoundary
        @Specialization(guards = "base != 10")
        RubyString toS(long n, int base,
                @Cached @Shared TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (base < 2 || base > 36) {
                throw new RaiseException(getContext(), coreExceptions().argumentErrorInvalidRadix(base, this));
            }

            return createString(fromJavaStringNode, Long.toString(n, base), Encodings.US_ASCII); // CR_7BIT
        }

        @TruffleBoundary
        @Specialization
        RubyString toS(RubyBignum value, int base,
                @Cached @Shared TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (base < 2 || base > 36) {
                throw new RaiseException(getContext(), coreExceptions().argumentErrorInvalidRadix(base, this));
            }

            return createString(
                    fromJavaStringNode,
                    BigIntegerOps.toString(value.value, base),
                    Encodings.US_ASCII); // CR_7BIT
        }
    }

    @Primitive(name = "ruby_integer?")
    public abstract static class IsRubyIntegerNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean doInt(int a) {
            return true;
        }

        @Specialization
        boolean doLong(long a) {
            return true;
        }

        @Specialization
        boolean doBignum(RubyBignum a) {
            return true;
        }

        @Fallback
        boolean other(Object a) {
            return false;
        }
    }

    @Primitive(name = "integer_fits_into_int?")
    public abstract static class FixnumFitsIntoIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean fitsIntoIntInt(int a) {
            return true;
        }

        @Specialization
        boolean fitsIntoIntLong(long a) {
            return CoreLibrary.fitsIntoInteger(a);
        }

        @Specialization
        boolean fitsIntoIntBignum(RubyBignum a) {
            return false;
        }

    }

    @Primitive(name = "integer_fits_into_uint?")
    public abstract static class IntegerFitsIntoUIntNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean fitsIntoUIntInt(int a) {
            return true;
        }

        @Specialization
        boolean fitsIntoUIntLong(long a) {
            return CoreLibrary.fitsIntoUnsignedInteger(a);
        }

        @Specialization
        boolean fitsIntoUIntBignum(RubyBignum a) {
            return false;
        }

    }

    @Primitive(name = "integer_fits_into_long?")
    public abstract static class IntegerFitsIntoLongNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean fitsIntoLongInt(int a) {
            return true;
        }

        @Specialization
        boolean fitsIntoLongLong(long a) {
            return true;
        }

        @Specialization
        boolean fitsIntoLongBignum(RubyBignum a) {
            return false;
        }

    }

    @Primitive(name = "integer_fits_into_ulong?")
    public abstract static class IntegerFitsIntoULongNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean fitsIntoULongInt(int a) {
            return true;
        }

        @Specialization
        boolean fitsIntoULongLong(long a) {
            return true;
        }

        @TruffleBoundary
        @Specialization
        boolean fitsIntoULongBignum(RubyBignum a) {
            BigInteger bigInt = a.value;
            if (bigInt.signum() >= 0) {
                return bigInt.bitLength() <= 64;
            } else {
                return false;
            }
        }

    }

    @Primitive(name = "integer_lower")
    public abstract static class IntegerLowerNode extends PrimitiveArrayArgumentsNode {

        public static IntegerLowerNode create() {
            return IntegerNodesFactory.IntegerLowerNodeFactory.create(null);
        }

        public abstract Object executeLower(Object value);

        @Specialization
        int lower(int value) {
            return value;
        }

        @Specialization(guards = "fitsInInteger(value)")
        int lower(long value) {
            return (int) value;
        }

        @Specialization(guards = "!fitsInInteger(value)")
        long lowerFails(long value) {
            return value;
        }
    }

    @Primitive(name = "integer_ulong_from_bignum")
    public abstract static class IntegerULongFromBigNumNode extends PrimitiveArrayArgumentsNode {

        private static final BigInteger TWO_POW_64 = BigInteger.valueOf(1).shiftLeft(64);
        private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

        @TruffleBoundary
        @Specialization
        long uLongFromBignum(RubyBignum b,
                @Cached InlinedConditionProfile doesNotNeedsConversion) {
            final BigInteger value = b.value;
            assert value.signum() >= 0;
            if (doesNotNeedsConversion.profile(this, value.compareTo(LONG_MAX) < 1)) {
                return value.longValue();
            } else {
                return value.subtract(TWO_POW_64).longValue();
            }
        }
    }

    @Primitive(name = "integer_pow", lowerFixnum = { 0, 1 })
    @ReportPolymorphism // inline cache
    public abstract static class PowNode extends PrimitiveArrayArgumentsNode {

        @Child private PowNode recursivePowNode;

        // Value taken from MRI for determining when to promote integer exponentiation into doubles.
        private static final int BIGLEN_LIMIT = 32 * 1024 * 1024;

        public abstract Object executePow(Object a, Object b);

        protected Object recursivePow(Object a, Object b) {
            if (recursivePowNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursivePowNode = insert(IntegerNodesFactory.PowNodeFactory.create(null));
            }

            return recursivePowNode.executePow(a, b);
        }

        // Highest bit we can set is the 30th due to sign
        @Specialization(guards = { "base == 2", "exponent >= 0", "exponent <= 30" })
        int powTwoInt(int base, int exponent) {
            return 1 << exponent;
        }

        // Highest bit we can set is the 62nd due to sign
        @Specialization(guards = { "base == 2", "exponent >= 0", "exponent <= 62" }, replaces = "powTwoInt")
        long powTwoLong(int base, int exponent) {
            return 1L << exponent;
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        @Specialization(
                guards = {
                        "isImplicitLong(base)",
                        "exponent == cachedExponent",
                        "cachedExponent >= 0",
                        "cachedExponent <= 10" },
                limit = "getLimit()")
        Object powConstantExponent(Object base, int exponent,
                @Cached("exponent") int cachedExponent,
                @Cached @Shared InlinedBranchProfile overflowProfile,
                @Cached @Exclusive MulNode mulNode) {
            Object result = 1;
            int exp = cachedExponent;
            while (exp > 0) {
                if ((exp & 1) == 0) {
                    base = mulNode.executeMul(base, base);
                    exp >>= 1;

                    if (base instanceof RubyBignum) { // Bignum
                        overflowProfile.enter(this);
                        final Object bignumResult = recursivePow(base, exp);
                        return mulNode.executeMul(result, bignumResult);
                    }
                } else {
                    result = mulNode.executeMul(base, result);
                    exp--;
                }
            }
            return result;
        }

        @Specialization(guards = { "isImplicitLong(base)", "exponent >= 0" })
        Object powLoop(Object base, long exponent,
                @Cached @Shared InlinedBranchProfile overflowProfile,
                @Cached @Exclusive MulNode mulNode) {
            Object result = 1;
            long exp = exponent;
            while (exp > 0) {
                if ((exp & 1) == 0) {
                    base = mulNode.executeMul(base, base);
                    exp >>= 1;

                    if (base instanceof RubyBignum) { // Bignum
                        overflowProfile.enter(this);
                        final Object bignumResult = recursivePow(base, exp);
                        return mulNode.executeMul(result, bignumResult);
                    }
                } else {
                    result = mulNode.executeMul(base, result);
                    exp--;
                }
            }
            return result;
        }

        @Specialization(guards = "exponent < 0")
        Object pow(long base, long exponent) {
            return FAILURE;
        }

        @Specialization
        Object powDouble(long base, double exponent,
                @Cached @Exclusive InlinedConditionProfile complexProfile) {
            if (complexProfile.profile(this, base < 0)) {
                return FAILURE;
            } else {
                return Math.pow(base, exponent);
            }
        }

        @Specialization
        Object powBignum(long base, RubyBignum exponent,
                @Cached @Shared WarnNode warnNode) {
            if (base == 0) {
                return 0;
            }

            if (base == 1) {
                return 1;
            }

            if (base == -1) {
                if (BigIntegerOps.testBit(exponent.value, 0)) {
                    return -1;
                } else {
                    return 1;
                }
            }

            if (BigIntegerOps.compare(exponent.value, BigInteger.ZERO) < 0) {
                return FAILURE;
            }

            if (warnNode.shouldWarn()) {
                warnNode.warningMessage(
                        getContext().getCallStack().getTopMostUserSourceSection(),
                        "in a**b, b may be too big");
            }
            // b >= 2**63 && (a > 1 || a < -1) => larger than largest double
            // MRI behavior/bug: always positive Infinity even if a negative and b odd (likely due
            // to libc pow(a, +inf)).
            return Double.POSITIVE_INFINITY;
        }

        @Specialization
        Object pow(RubyBignum base, long exponent,
                @Cached @Exclusive InlinedConditionProfile negativeProfile,
                @Cached @Exclusive InlinedConditionProfile maybeTooBigProfile,
                @Cached @Shared WarnNode warnNode) {
            if (negativeProfile.profile(this, exponent < 0)) {
                return FAILURE;
            } else {
                final BigInteger bigIntegerBase = base.value;
                final int baseBitLength = BigIntegerOps.bitLength(bigIntegerBase);

                // Logic for promoting integer exponentiation into doubles taken from MRI.
                // We replicate the logic exactly so we match MRI's ranges.
                if (maybeTooBigProfile
                        .profile(this, baseBitLength > BIGLEN_LIMIT || (baseBitLength * exponent > BIGLEN_LIMIT))) {
                    if (warnNode.shouldWarn()) {
                        warnNode.warningMessage(
                                getContext().getCallStack().getTopMostUserSourceSection(),
                                "in a**b, b may be too big");
                    }
                    return BigIntegerOps.pow(bigIntegerBase, /* as double */ exponent);
                }

                // The cast is safe because of the check above.
                return createBignum(BigIntegerOps.pow(bigIntegerBase, (int) exponent));
            }
        }

        @Specialization
        Object pow(RubyBignum base, double exponent) {
            double doublePow = BigIntegerOps.pow(base.value, exponent);
            if (Double.isNaN(doublePow)) {
                // Instead of returning NaN, run the fallback code which can create a complex result
                return FAILURE;
            } else {
                return doublePow;
            }
        }

        @Specialization
        Object pow(RubyBignum base, RubyBignum exponent) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyNumber(exponent)")
        Object pow(Object base, Object exponent) {
            return FAILURE;
        }

        protected int getLimit() {
            return getLanguage().options.POW_CACHE;
        }

    }

    @Primitive(name = "mod_pow")
    public abstract static class ModPowNodePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object doModPow(Object baseObject, Object exponentObject, Object moduloObject,
                @Cached BigIntegerCastNode baseCastNode,
                @Cached BigIntegerCastNode exponentCastNode,
                @Cached BigIntegerCastNode moduloCastNode,
                @Cached ModPowNode modPowNode) {
            final var base = baseCastNode.execute(this, baseObject);
            final var exponent = exponentCastNode.execute(this, exponentObject);
            final var modulo = moduloCastNode.execute(this, moduloObject);
            return modPowNode.execute(this, base, exponent, modulo);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ModPowNode extends RubyBaseNode {

        public abstract Object execute(Node node, BigInteger base, BigInteger exponent, BigInteger modulo);

        @Specialization(guards = "modulo.signum() < 0")
        static Object mod_pow_neg(Node node, BigInteger base, BigInteger exponent, BigInteger modulo,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignum) {
            BigInteger result = BigIntegerOps.modPow(base, exponent, BigIntegerOps.negate(modulo));
            return fixnumOrBignum.execute(node, result.signum() == 1 ? BigIntegerOps.add(result, modulo) : result);
        }

        @Specialization(guards = "modulo.signum() > 0")
        static Object mod_pow_pos(Node node, BigInteger base, BigInteger exponent, BigInteger modulo,
                @Cached @Shared FixnumOrBignumNode fixnumOrBignum) {
            BigInteger result = BigIntegerOps.modPow(base, exponent, modulo);
            return fixnumOrBignum.execute(node, result);
        }

        @Specialization(guards = "modulo.signum() == 0")
        static Object mod_pow_zero(Node node, BigInteger base, BigInteger exponent, BigInteger modulo) {
            throw new RaiseException(getContext(node), coreExceptions(node).zeroDivisionError(node));
        }

    }

    @CoreMethod(names = "downto", needsBlock = true, required = 1, returnsEnumeratorIfNoBlock = true)
    public abstract static class DownToNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode downtoInternalCall;

        @Specialization
        Object downto(int from, int to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared InlinedLoopConditionProfile loopProfile) {
            int i = from;
            try {
                for (; loopProfile.inject(this, i >= to); i--) {
                    yieldNode.yield(this, block, i);
                }
            } finally {
                profileAndReportLoopCount(this, loopProfile, from - i + 1);
            }

            return nil;
        }

        @Specialization
        Object downto(int from, double to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared InlinedLoopConditionProfile loopProfile) {
            return downto(from, (int) Math.ceil(to), block, yieldNode, loopProfile);
        }

        @Specialization
        Object downto(long from, long to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared InlinedLoopConditionProfile loopProfile) {
            long i = from;
            try {
                for (; i >= to; i--) {
                    yieldNode.yield(this, block, i);
                }
            } finally {
                profileAndReportLoopCount(this, loopProfile, from - i + 1);
            }

            return nil;
        }

        @Specialization
        Object downto(long from, double to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode,
                @Cached @Shared InlinedLoopConditionProfile loopProfile) {
            return downto(from, (long) Math.ceil(to), block, yieldNode, loopProfile);
        }

        @Specialization(guards = "isRubyBignum(from) || !isImplicitLongOrDouble(to)")
        Object downto(Object from, Object to, RubyProc block) {
            if (downtoInternalCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                downtoInternalCall = insert(DispatchNode.create());
            }

            return downtoInternalCall.callWithBlock(from, "downto_internal", block, to);
        }

    }

    @CoreMethod(names = { "to_i", "to_int" })
    public abstract static class ToINode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int toI(int n) {
            return n;
        }

        @Specialization
        long toI(long n) {
            return n;
        }

        @Specialization
        RubyBignum toI(RubyBignum n) {
            return n;
        }

    }

    @CoreMethod(names = "upto", needsBlock = true, required = 1, returnsEnumeratorIfNoBlock = true)
    public abstract static class UpToNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode uptoInternalCall;
        private final LoopConditionProfile loopProfile = LoopConditionProfile.create();

        @Specialization
        Object upto(int from, int to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode) {
            int i = from;
            try {
                for (; loopProfile.inject(i <= to); i++) {
                    yieldNode.yield(this, block, i);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i - from + 1);
            }

            return nil;
        }

        @Specialization
        Object upto(int from, double to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode) {
            return upto(from, (int) Math.floor(to), block, yieldNode);
        }

        @Specialization
        Object upto(long from, long to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode) {
            long i = from;
            try {
                for (; i <= to; i++) {
                    yieldNode.yield(this, block, i);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i - from + 1);
            }

            return nil;
        }

        @Specialization
        Object upto(long from, double to, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode) {
            return upto(from, (long) Math.floor(to), block, yieldNode);
        }

        @Specialization(guards = "isRubyBignum(from) || !isImplicitLongOrDouble(to)")
        Object upto(Object from, Object to, RubyProc block) {
            if (uptoInternalCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                uptoInternalCall = insert(DispatchNode.create());
            }

            return uptoInternalCall.callWithBlock(from, "upto_internal", block, to);
        }

    }

}
