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
package org.truffleruby.language;

import com.oracle.truffle.api.dsl.Idempotent;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.method.RubyUnboundMethod;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.range.RubyIntRange;
import org.truffleruby.core.range.RubyLongRange;
import org.truffleruby.core.range.RubyObjectRange;
import org.truffleruby.core.regexp.RubyMatchData;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.support.RubyIO;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;

public abstract class RubyGuards {

    private static final long NEGATIVE_ZERO_DOUBLE_BITS = Double.doubleToRawLongBits(-0.0);

    // Basic Java types

    public static boolean fitsInInteger(long value) {
        return CoreLibrary.fitsIntoInteger(value);
    }

    public static boolean isString(Object value) {
        return value instanceof String;
    }

    // no isLong, use isImplicitLong instead in guards to account for the int->long implicit cast of RubyTypes

    public static boolean isInteger(Object object) {
        return object instanceof Integer;
    }

    public static boolean isDouble(Object object) {
        return object instanceof Double;
    }

    public static boolean isImplicitLong(Object object) {
        return object instanceof Integer || object instanceof Long;
    }

    public static boolean isImplicitLongOrDouble(Object object) {
        return object instanceof Integer || object instanceof Long || object instanceof Double;
    }

    public static boolean isRubyInteger(Object object) {
        return isImplicitLong(object) || object instanceof RubyBignum;
    }

    public static boolean isRubyNumber(Object object) {
        // Doesn't include classes like BigDecimal
        return isImplicitLongOrDouble(object) || object instanceof RubyBignum;
    }

    public static boolean assertIsValidRubyValue(Object value) {
        assert value != null : "null flowing in Ruby nodes";
        if (value instanceof Byte || value instanceof Short || value instanceof Float) {
            assert false : "Invalid primitive flowing in Ruby nodes: " + value + " (" +
                    value.getClass().getSimpleName() + ")";
        }
        assert !(value instanceof ArgumentsDescriptor) : value;
        return true;
    }

    /** Does not include {@link Byte}, {@link Short}, {@link Float} as those are converted at the interop boundary in
     * {@link org.truffleruby.interop.ForeignToRubyNode} and are not implicit casts in {@link RubyTypes}. */
    public static boolean isPrimitive(Object value) {
        assert assertIsValidRubyValue(value);
        return value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double;
    }

    public static boolean isPrimitiveOrImmutable(Object value) {
        assert assertIsValidRubyValue(value);
        return isPrimitive(value) || value instanceof ImmutableRubyObject;
    }

    @Idempotent
    public static boolean isPrimitiveClass(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Integer.class || clazz == Long.class || clazz == Double.class;
    }

    // Ruby types

    public static boolean isRubyBignum(Object value) {
        return value instanceof RubyBignum;
    }

    public static boolean isRubyRange(Object value) {
        return value instanceof RubyObjectRange || value instanceof RubyIntRange ||
                value instanceof RubyLongRange;
    }

    public static boolean isRubyObjectRange(Object value) {
        return value instanceof RubyObjectRange;
    }

    public static boolean isRubyArray(Object value) {
        return value instanceof RubyArray;
    }

    @Idempotent
    public static boolean isRubyClass(Object value) {
        return value instanceof RubyClass;
    }

    public static boolean isRubyHash(Object value) {
        return value instanceof RubyHash;
    }

    public static boolean isRubyModule(Object value) {
        return value instanceof RubyModule;
    }

    @Idempotent
    public static boolean isRubyIO(Object value) {
        return value instanceof RubyIO;
    }

    public static boolean isRubyRegexp(Object value) {
        return value instanceof RubyRegexp;
    }

    /** Use RubyStringLibrary to check if it's a String */
    public static boolean isNotRubyString(Object value) {
        return !(value instanceof ImmutableRubyString) && !(value instanceof RubyString);
    }

    public static boolean isImmutableRubyString(Object value) {
        return value instanceof ImmutableRubyString;
    }

    public static boolean isRubySymbol(Object value) {
        return value instanceof RubySymbol;
    }

    /** Should be used only for interop together with {@link ToJavaStringNode} */
    public static boolean isRubySymbolOrString(Object value) {
        return isRubySymbol(value) || value instanceof RubyString || value instanceof ImmutableRubyString;
    }

    public static boolean isRubyEncoding(Object object) {
        return object instanceof RubyEncoding;
    }

    public static boolean isRubyException(Object object) {
        return object instanceof RubyException;
    }

    public static boolean isRubyMethod(Object value) {
        return value instanceof RubyMethod;
    }

    public static boolean isRubyUnboundMethod(Object value) {
        return value instanceof RubyUnboundMethod;
    }

    public static boolean isRubyProc(Object object) {
        return object instanceof RubyProc;
    }

    public static boolean isRubyMatchData(Object object) {
        return object instanceof RubyMatchData;
    }

    public static boolean isNil(Object object) {
        return object == Nil.INSTANCE;
    }

    // Internal types

    public static boolean isRubyDynamicObject(Object object) {
        return object instanceof RubyDynamicObject;
    }

    public static boolean isRubyValue(Object object) {
        return object instanceof RubyDynamicObject || object instanceof ImmutableRubyObject || isPrimitive(object);
    }

    public static boolean isForeignObject(Object object) {
        return !isRubyValue(object);
    }

    // Sentinels

    public static boolean wasProvided(Object value) {
        return !wasNotProvided(value);
    }

    public static boolean wasNotProvided(Object value) {
        return value == NotProvided.INSTANCE;
    }

    // Values

    public static boolean isNaN(double value) {
        return Double.isNaN(value);
    }

    public static boolean isInfinity(double value) {
        return Double.isInfinite(value);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    public static boolean isPositive(double value) {
        return value >= 0;
    }

    public static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == NEGATIVE_ZERO_DOUBLE_BITS;
    }

    // Composite

    public static boolean isSingletonClass(Object value) {
        return value instanceof RubyClass && ((RubyClass) value).isSingleton;
    }

    public static boolean isMetaClass(RubyModule value) {
        return isSingletonClass(value) && ((RubyClass) value).attached instanceof RubyModule;
    }

    // Arguments

    public static boolean noArguments(Object[] args) {
        return args.length == 0;
    }

    public static boolean singleArgument(Object[] args) {
        return args.length == 1;
    }

}
