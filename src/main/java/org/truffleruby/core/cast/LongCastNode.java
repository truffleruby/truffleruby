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

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/** See {@link ToIntNode} for a comparison of different integer conversion nodes. */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class LongCastNode extends RubyBaseNode {

    public abstract long executeCastLong(Node node, Object value);

    @Specialization
    static long doInt(int value) {
        return value;
    }

    @Specialization
    static long doLong(long value) {
        return value;
    }

    @TruffleBoundary
    @Specialization(guards = "!isImplicitLong(value)")
    static long doBasicObject(Node node, Object value) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).typeErrorIsNotA(value.toString(), "Integer (fitting in long)", node));
    }
}
