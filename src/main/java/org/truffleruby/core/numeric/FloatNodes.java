/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.numeric;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.Split;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.FloatToIntegerNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@CoreModule(value = "Float", isClass = true)
public abstract class FloatNodes {

    @CoreMethod(names = "-@")
    public abstract static class NegNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double neg(double value) {
            return -value;
        }

    }

    /** See {@link org.truffleruby.core.inlined.InlinedAddNode} */
    @CoreMethod(names = "+", required = 1)
    public abstract static class AddNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double add(double a, long b) {
            return a + b;
        }

        @Specialization
        double add(double a, double b) {
            return a + b;
        }

        @Specialization
        double add(double a, RubyBignum b) {
            return a + BigIntegerOps.doubleValue(b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object addCoerced(double a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().PLUS, b);
        }
    }

    /** See {@link org.truffleruby.core.inlined.InlinedSubNode} */
    @CoreMethod(names = "-", required = 1)
    public abstract static class SubNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double sub(double a, long b) {
            return a - b;
        }

        @Specialization
        double sub(double a, double b) {
            return a - b;
        }

        @Specialization
        double sub(double a, RubyBignum b) {
            return a - BigIntegerOps.doubleValue(b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object subCoerced(double a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MINUS, b);
        }

    }

    @CoreMethod(names = "*", required = 1)
    public abstract static class MulNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double mul(double a, long b) {
            return a * b;
        }

        @Specialization
        double mul(double a, double b) {
            return a * b;
        }

        @Specialization
        double mul(double a, RubyBignum b) {
            return a * BigIntegerOps.doubleValue(b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object mulCoerced(double a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MULTIPLY, b);
        }

    }

    // Splitting: inline cache
    @CoreMethod(names = "**", required = 1, split = Split.ALWAYS)
    public abstract static class PowNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode complexConvertNode;
        @Child private DispatchNode complexPowNode;

        private final ConditionProfile complexProfile = ConditionProfile.create();

        @Specialization(
                guards = { "exponent == cachedExponent", "cachedExponent >= 0", "cachedExponent < 10" },
                limit = "10")
        @ExplodeLoop
        double powCached(double base, long exponent,
                @Cached("exponent") long cachedExponent) {
            double result = 1.0;
            for (int i = 0; i < cachedExponent; i++) {
                result *= base;
            }
            return result;
        }

        @Specialization(replaces = "powCached")
        double pow(double base, long exponent) {
            return Math.pow(base, exponent);
        }

        @Specialization
        Object pow(VirtualFrame frame, double base, double exponent) {
            if (complexProfile.profile(base < 0 && exponent != Math.round(exponent))) {
                if (complexConvertNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    complexConvertNode = insert(DispatchNode.create());
                    complexPowNode = insert(DispatchNode.create());
                }

                final Object aComplex = complexConvertNode.call(coreLibrary().complexClass, "convert", base, 0);

                return complexPowNode.call(aComplex, "**", exponent);
            } else {
                return Math.pow(base, exponent);
            }
        }

        @Specialization
        double pow(double base, RubyBignum exponent) {
            return Math.pow(base, BigIntegerOps.doubleValue(exponent));
        }

        @Specialization(guards = "!isRubyNumber(exponent)")
        Object powCoerced(double base, Object exponent,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(base, "redo_coerced", coreSymbols().POW, exponent);
        }

    }

    @CoreMethod(names = "/", required = 1)
    public abstract static class DivNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double div(double a, long b) {
            return a / b;
        }

        @Specialization
        double div(double a, double b) {
            return a / b;
        }

        @Specialization
        double div(double a, RubyBignum b) {
            return a / BigIntegerOps.doubleValue(b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object divCoerced(double a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().DIVIDE, b);
        }

    }

    @CoreMethod(names = "%", required = 1)
    public abstract static class ModNode extends CoreMethodArrayArgumentsNode {

        private final ConditionProfile lessThanZeroProfile = ConditionProfile.create();
        private final BranchProfile zeroProfile = BranchProfile.create();

        @NeverDefault
        public static ModNode create() {
            return FloatNodesFactory.ModNodeFactory.create(null);
        }

        public abstract Object executeMod(double a, double b);

        @Specialization
        double mod(double a, long b) {
            return mod(a, (double) b);
        }

        @Specialization
        double mod(double a, double b) {
            if (b == 0) {
                zeroProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().zeroDivisionError(this));
            }

            double result = Math.IEEEremainder(a, b);

            if (lessThanZeroProfile.profile(b * result < 0)) {
                result += b;
            }

            return result;
        }

        @Specialization
        double mod(double a, RubyBignum b) {
            return mod(a, BigIntegerOps.doubleValue(b));
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object modCoerced(double a, Object b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().MODULO, b);
        }

    }

    @CoreMethod(names = "divmod", required = 1)
    public abstract static class DivModNode extends CoreMethodArrayArgumentsNode {


        @Specialization
        RubyArray divMod(double a, long b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b);
        }

        @Specialization
        RubyArray divMod(double a, double b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b);
        }

        @Specialization
        RubyArray divMod(double a, RubyBignum b,
                @Cached @Shared GeneralDivModNode divModNode) {
            return divModNode.execute(this, a, b.value);
        }

        @Specialization
        Object divModCoerced(double a, RubyDynamicObject b,
                @Cached DispatchNode redoCoerced) {
            return redoCoerced.call(a, "redo_coerced", coreSymbols().DIVMOD, b);
        }

    }

    @CoreMethod(names = "<", required = 1)
    public abstract static class LessNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean less(double a, long b) {
            return a < b;
        }

        @Specialization
        boolean less(double a, double b) {
            return a < b;
        }

        @Specialization
        boolean lessBignum(double a, RubyBignum b) {
            return BigIntegerOps.less(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object lessCoerced(double a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().LESS_THAN, b);
        }
    }

    @CoreMethod(names = "<=", required = 1)
    public abstract static class LessEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean lessEqual(double a, long b) {
            return a <= b;
        }

        @Specialization
        boolean lessEqual(double a, double b) {
            return a <= b;
        }

        @Specialization
        boolean lessEqual(double a, RubyBignum b) {
            return BigIntegerOps.lessEqual(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object lessEqualCoerced(double a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().LEQ, b);
        }
    }

    @CoreMethod(names = "eql?", required = 1)
    public abstract static class EqlNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean eql(double a, double b) {
            return a == b;
        }

        @Specialization(guards = { "!isDouble(b)" })
        boolean eqlGeneral(double a, Object b) {
            return false;
        }
    }

    @ImportStatic(RubyArguments.class)
    @GenerateUncached
    @CoreMethod(names = { "==", "===" }, required = 1, alwaysInlined = true)
    public abstract static class EqualNode extends AlwaysInlinedMethodNode {

        @Specialization
        Object equal(Object self, Object[] rubyArgs, RootCallTarget target,
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
        boolean equal(double a, double b) {
            return a == b;
        }

        @Specialization
        boolean equal(double a, long b) {
            return a == b;
        }

        @Specialization
        boolean equal(double a, RubyBignum b) {
            return BigIntegerOps.equal(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object equal(double a, Object b,
                @Cached(inline = false) DispatchNode fallbackCallNode) {
            return fallbackCallNode.call(a, "equal_fallback", b);
        }
    }

    @CoreMethod(names = "<=>", required = 1)
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "isNaN(a)")
        Object compareFirstNaN(double a, Object b) {
            return nil;
        }

        @Specialization(guards = "isNaN(b)")
        Object compareSecondNaN(Object a, double b) {
            return nil;
        }

        @Specialization(guards = { "!isNaN(a)", "!isNaN(b)" })
        int compareDoubleDouble(double a, double b,
                @Cached @Shared InlinedConditionProfile equalProfile) {
            return compareDoubles(a, b, equalProfile, this);
        }

        @Specialization(guards = "!isNaN(a)")
        int compareDoubleLong(double a, long b,
                @Cached @Shared InlinedConditionProfile equalProfile) {
            return compareDoubles(a, b, equalProfile, this);
        }

        @Specialization(guards = "!isNaN(a)")
        int compareBignum(double a, RubyBignum b) {
            return BigIntegerOps.compare(a, b.value);
        }

        @Specialization(guards = { "!isNaN(a)", "!isRubyNumber(b)" })
        Object compare(double a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare_bad_coerce_return_error", b);
        }

        public static int compareDoubles(double a, double b, InlinedConditionProfile equalProfile, Node node) {
            if (equalProfile.profile(node, a == b)) {
                return 0;
            } else {
                return a > b ? 1 : -1;
            }
        }
    }

    @CoreMethod(names = ">=", required = 1)
    public abstract static class GreaterEqualNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean greaterEqual(double a, long b) {
            return a >= b;
        }

        @Specialization
        boolean greaterEqual(double a, double b) {
            return a >= b;
        }

        @Specialization
        boolean greaterEqual(double a, RubyBignum b) {
            return BigIntegerOps.greaterEqual(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object greaterEqualCoerced(double a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().GEQ, b);
        }

    }

    @CoreMethod(names = ">", required = 1)
    public abstract static class GreaterNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean greater(double a, long b) {
            return a > b;
        }

        @Specialization
        boolean greater(double a, double b) {
            return a > b;
        }

        @Specialization
        boolean greater(double a, RubyBignum b) {
            return BigIntegerOps.greater(a, b);
        }

        @Specialization(guards = "!isRubyNumber(b)")
        Object greaterCoerced(double a, Object b,
                @Cached DispatchNode redoCompare) {
            return redoCompare.call(a, "redo_compare", coreSymbols().GREATER_THAN, b);
        }
    }

    @CoreMethod(names = { "abs", "magnitude" })
    public abstract static class AbsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double abs(double n) {
            return Math.abs(n);
        }

    }

    @CoreMethod(names = "infinite?")
    public abstract static class InfiniteNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object infinite(double value) {
            if (Double.isInfinite(value)) {
                if (value < 0) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                return nil;
            }
        }

    }

    @CoreMethod(names = "nan?")
    public abstract static class NaNNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean nan(double value) {
            return Double.isNaN(value);
        }

    }

    @CoreMethod(names = "next_float")
    public abstract static class NextFloatNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double nextFloat(double value) {
            return Math.nextUp(value);
        }

    }

    @CoreMethod(names = "prev_float")
    public abstract static class PrevFloatNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double prevFloat(double value) {
            return Math.nextDown(value);
        }

    }

    protected static final class FloatRoundGuards {

        // These are < rather than <=, because we may offset by -1 or +1 to round in the direction that we want

        public static boolean fitsInInteger(double n) {
            return Integer.MIN_VALUE < n && n < Integer.MAX_VALUE;
        }

        public static boolean fitsInLong(double n) {
            return Long.MIN_VALUE < n && n < Long.MAX_VALUE;
        }

    }

    @Primitive(name = "float_ceil")
    public abstract static class FloatCeilPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object ceil(double n,
                @Cached FloatToIntegerNode floatToIntegerNode) {
            return floatToIntegerNode.execute(this, Math.ceil(n));
        }
    }

    @Primitive(name = "float_ceil_ndigits", lowerFixnum = 1)
    public abstract static class FloatCeilNDigitsPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        double ceilNDigits(double n, int ndigits) {
            final double scale = Math.pow(10.0, ndigits);
            return Math.ceil(n * scale) / scale;
        }

    }

    @Primitive(name = "float_floor")
    public abstract static class FloatFloorPrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object floor(double n,
                @Cached FloatToIntegerNode floatToIntegerNode) {
            return floatToIntegerNode.execute(this, Math.floor(n));
        }
    }

    @Primitive(name = "float_floor_ndigits", lowerFixnum = 1)
    public abstract static class FloatFloorNDigitsPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        double floorNDigits(double n, int ndigits) {
            final double scale = Math.pow(10.0, ndigits);
            return Math.floor(n * scale) / scale;
        }

    }

    @Primitive(name = "float_exp")
    public abstract static class FloatExpNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int exp(double value) {
            return Math.getExponent(value);
        }
    }

    @CoreMethod(names = { "to_i", "to_int" })
    public abstract static class ToINode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object toI(double value,
                @Cached FloatToIntegerNode floatToIntegerNode) {
            return floatToIntegerNode.execute(this, value);
        }
    }

    @CoreMethod(names = "to_f")
    public abstract static class ToFNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        double toF(double value) {
            return value;
        }

    }

    @CoreMethod(names = { "to_s", "inspect" })
    @ImportStatic(Double.class)
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        /* Ruby has complex custom formatting logic for floats. Our logic meets the specs but we suspect it's possibly
         * still not entirely correct. JRuby seems to be correct, but their logic is tied up in their printf
         * implementation. Also see our FormatFloatNode, which I suspect is also deficient or under-tested. */

        @Specialization(guards = "value == POSITIVE_INFINITY")
        RubyString toSPositiveInfinity(double value,
                @Cached("specialValueString(POSITIVE_INFINITY)") TruffleString cachedString) {
            return createString(cachedString, Encodings.US_ASCII);
        }

        @Specialization(guards = "value == NEGATIVE_INFINITY")
        RubyString toSNegativeInfinity(double value,
                @Cached("specialValueString(NEGATIVE_INFINITY)") TruffleString cachedString) {
            return createString(cachedString, Encodings.US_ASCII);
        }

        @Specialization(guards = "isNaN(value)")
        RubyString toSNaN(double value,
                @Cached("specialValueString(value)") TruffleString cachedString) {
            return createString(cachedString, Encodings.US_ASCII);
        }

        @Specialization(guards = "hasNoExp(value)")
        RubyString toSNoExp(double value) {
            return createString(fromJavaStringNode, makeStringNoExp(value, getLanguage().getCurrentThread()),
                    Encodings.US_ASCII); // CR_7BIT
        }

        @Specialization(guards = "hasLargeExp(value)")
        RubyString toSLargeExp(double value) {
            return createString(fromJavaStringNode, makeStringLargeExp(value, getLanguage().getCurrentThread()),
                    Encodings.US_ASCII); // CR_7BIT
        }

        @Specialization(guards = "hasSmallExp(value)")
        RubyString toSSmallExp(double value) {
            return createString(fromJavaStringNode, makeStringSmallExp(value, getLanguage().getCurrentThread()),
                    Encodings.US_ASCII); // CR_7BIT
        }

        @TruffleBoundary
        private String makeStringNoExp(double value, RubyThread thread) {
            return getNoExpFormat(thread).format(value);
        }

        @TruffleBoundary
        private String makeStringSmallExp(double value, RubyThread thread) {
            return getSmallExpFormat(thread).format(value);
        }

        @TruffleBoundary
        private String makeStringLargeExp(double value, RubyThread thread) {
            return getLargeExpFormat(thread).format(value);
        }

        protected static boolean hasNoExp(double value) {
            double abs = Math.abs(value);
            return abs == 0.0 || ((abs >= 0.0001) && (abs < 1_000_000_000_000_000.0));
        }

        protected static boolean hasLargeExp(double value) {
            double abs = Math.abs(value);
            return Double.isFinite(abs) && (abs >= 1_000_000_000_000_000.0);
        }

        protected static boolean hasSmallExp(double value) {
            double abs = Math.abs(value);
            return (abs < 0.0001) && (abs != 0.0);
        }

        @NeverDefault
        protected static TruffleString specialValueString(double value) {
            return TStringUtils.fromJavaString(Double.toString(value), Encodings.US_ASCII);
        }

        private DecimalFormat getNoExpFormat(RubyThread thread) {
            if (thread.noExpFormat == null) {
                final DecimalFormatSymbols noExpSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
                thread.noExpFormat = new DecimalFormat("0.0################", noExpSymbols);
            }
            return thread.noExpFormat;
        }

        private DecimalFormat getSmallExpFormat(RubyThread thread) {
            if (thread.smallExpFormat == null) {
                final DecimalFormatSymbols smallExpSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
                smallExpSymbols.setExponentSeparator("e");
                thread.smallExpFormat = new DecimalFormat("0.0################E00", smallExpSymbols);
            }
            return thread.smallExpFormat;
        }

        private DecimalFormat getLargeExpFormat(RubyThread thread) {
            if (thread.largeExpFormat == null) {
                final DecimalFormatSymbols largeExpSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
                largeExpSymbols.setExponentSeparator("e+");
                thread.largeExpFormat = new DecimalFormat("0.0################E00", largeExpSymbols);
            }
            return thread.largeExpFormat;
        }

    }

    @NonStandard
    @CoreMethod(names = "dtoa", visibility = Visibility.PRIVATE)
    public abstract static class DToANode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyArray dToA(double value) {
            // Large enough to print all digits of Float::MIN.
            String string = StringUtils.format(Locale.ENGLISH, "%.1022f", value);

            if (string.toLowerCase(Locale.ENGLISH).contains("e")) {
                throw new UnsupportedOperationException();
            }

            string = StringUtils.replace(string, "-", "");
            while (string.charAt(string.length() - 1) == '0') {
                string = string.substring(0, string.length() - 1);
            }

            int decimal;

            if (string.startsWith("0.")) {
                string = StringUtils.replace(string, "0.", "");
                decimal = 0;

                while (string.charAt(0) == '0') {
                    string = string.substring(1, string.length());
                    --decimal;
                }
            } else {
                decimal = string.indexOf('.');

                if (decimal == -1) {
                    throw new UnsupportedOperationException();
                }

                string = StringUtils.replace(string, ".", "");
            }

            final int sign = value < 0 ? 1 : 0;

            return createArray(new Object[]{
                    createString(fromJavaStringNode, string, Encodings.UTF_8), // CR_7BIT
                    decimal,
                    sign,
                    string.length()
            });
        }

    }

}
