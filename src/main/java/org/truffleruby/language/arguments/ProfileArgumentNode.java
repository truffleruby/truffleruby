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
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.language.NoImplicitCastsToLong;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

import java.util.Objects;

@TypeSystemReference(NoImplicitCastsToLong.class)
@ImportStatic(CompilerDirectives.class)
@NodeChild(value = "childNode", type = RubyNode.class)
public abstract class ProfileArgumentNode extends RubyContextSourceNode {

    protected abstract RubyNode getChildNode();

    @Specialization(guards = "guardBoolean(value, cachedValue)")
    boolean doBoolean(boolean value,
            @Cached("createCachedValue(value)") @Shared Object cachedValue) {
        return (boolean) cachedValue;
    }

    @Specialization(guards = "guardInt(value, cachedValue)")
    int doInt(int value,
            @Cached("createCachedValue(value)") @Shared Object cachedValue) {
        return (int) cachedValue;
    }

    @Specialization(guards = "guardLong(value, cachedValue)")
    long doLong(long value,
            @Cached("createCachedValue(value)") @Shared Object cachedValue) {
        return (long) cachedValue;
    }

    @Specialization(guards = "guardDouble(value, cachedValue)")
    double doDouble(double value,
            @Cached("createCachedValue(value)") @Shared Object cachedValue) {
        return (double) cachedValue;
    }

    @Specialization(guards = { "guardClass(value, cachedValue)", "!isPrimitiveClass(cachedValue)" })
    Object doClass(Object value,
            @Cached("createCachedValue(value)") @Shared Object cachedValue) {
        assert RubyGuards.assertIsValidRubyValue(value);
        // The cast is only useful for the compiler.
        if (CompilerDirectives.inInterpreter()) {
            return value;
        } else {
            return CompilerDirectives.castExact(value, (Class<?>) cachedValue);
        }
    }

    @Specialization(replaces = { "doBoolean", "doInt", "doLong", "doDouble", "doClass" })
    Object doGeneric(Object value) {
        assert RubyGuards.assertIsValidRubyValue(value);
        return value;
    }

    /** The reason this method is used for all cached arguments is that Truffle DSL forces us to use same cache
     * initializer for all @Shared arguments. Otherwise, it throws an error. */
    @NeverDefault
    static Object createCachedValue(Object value) {
        if (RubyGuards.isPrimitive(value)) {
            return value;
        }

        return value == null ? Objects.class : value.getClass();
    }

    static boolean guardBoolean(boolean value, Object cachedValue) {
        return cachedValue instanceof Boolean cachedBoolean && value == cachedBoolean;
    }

    static boolean guardInt(int value, Object cachedValue) {
        return cachedValue instanceof Integer cachedInt && value == cachedInt;
    }

    static boolean guardLong(long value, Object cachedValue) {
        return cachedValue instanceof Long cachedLong && value == cachedLong;
    }

    static boolean guardDouble(double value, Object cachedValue) {
        return cachedValue instanceof Double cachedDouble &&
                // -0.0 == 0.0, but you can tell the difference through other means, so we need to differentiate.
                Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(cachedDouble);
    }

    static boolean guardClass(Object value, Object cachedValue) {
        return cachedValue instanceof Class<?> cachedClass && CompilerDirectives.isExact(value, cachedClass);
    }

    @Idempotent
    static boolean isPrimitiveClass(Object cachedValue) {
        return RubyGuards.isPrimitiveClass((Class<?>) cachedValue);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ProfileArgumentNodeGen.create(getChildNode().cloneUninitialized());
        return copy.copyFlags(this);
    }
}
