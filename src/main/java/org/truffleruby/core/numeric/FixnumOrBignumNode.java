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
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

@GenerateCached(false)
@GenerateInline
@GenerateUncached
public abstract class FixnumOrBignumNode extends RubyBaseNode {

    private static final BigInteger LONG_MIN_BIGINT = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);

    public static Object executeUncached(BigInteger value) {
        return FixnumOrBignumNodeGen.getUncached().execute(null, value);
    }

    public abstract Object execute(Node node, BigInteger value);

    @Specialization
    static Object fixnumOrBignum(Node node, BigInteger value,
            @Cached InlinedConditionProfile lowerProfile,
            @Cached InlinedConditionProfile intProfile) {
        if (lowerProfile.profile(node, fitsIntoLong(value))) {
            final long longValue = BigIntegerOps.longValue(value);

            if (intProfile.profile(node, CoreLibrary.fitsIntoInteger(longValue))) {
                return (int) longValue;
            } else {
                return longValue;
            }
        } else {
            return createBignum(value);
        }
    }

    @TruffleBoundary
    private static boolean fitsIntoLong(BigInteger value) {
        return value.compareTo(LONG_MIN_BIGINT) >= 0 && value.compareTo(LONG_MAX_BIGINT) <= 0;
    }

}
