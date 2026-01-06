/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
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

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.utils.Utils;

/** See {@link ToIntNode} for a comparison of different integer conversion nodes. */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class IntegerCastNode extends RubyBaseNode {

    public abstract int execute(Node node, Object value);

    @Specialization
    static int doInt(int value) {
        return value;
    }

    @Specialization(guards = "fitsInInteger(value)")
    static int doLong(long value) {
        return (int) value;
    }

    @Specialization(guards = "!fitsInInteger(value)")
    static int doLongToBig(Node node, long value) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).rangeError("long too big to convert into 'int'", node));
    }

    @Specialization(guards = "!isImplicitLong(value)")
    static int doBasicObject(Node node, Object value) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).typeErrorIsNotA(Utils.toString(value), "Integer (fitting in int)", node));
    }
}
