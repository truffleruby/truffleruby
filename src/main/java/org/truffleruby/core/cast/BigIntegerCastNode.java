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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import java.math.BigInteger;

/** Casts a value into a BigInteger. */
@GenerateCached(false)
@GenerateInline
public abstract class BigIntegerCastNode extends RubyBaseNode {

    public abstract BigInteger execute(Node node, Object value);

    @Specialization
    BigInteger doInt(int value) {
        return BigIntegerOps.valueOf(value);
    }

    @Specialization
    BigInteger doLong(long value) {
        return BigIntegerOps.valueOf(value);
    }

    @Specialization
    BigInteger doBignum(RubyBignum value) {
        return value.value;
    }

    @Specialization(guards = "!isRubyInteger(value)")
    BigInteger doBasicObject(Object value) {
        throw new RaiseException(getContext(), notAnInteger(value));
    }

    @TruffleBoundary
    private RubyException notAnInteger(Object object) {
        return coreExceptions().typeErrorIsNotA(
                object.toString(),
                "Integer",
                this);
    }

}
