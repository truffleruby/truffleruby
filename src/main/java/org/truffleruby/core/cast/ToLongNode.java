/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.utils.Utils;

/** See {@link ToIntNode} for a comparison of different integer conversion nodes. */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class ToLongNode extends RubyBaseNode {

    public abstract long execute(Node node, Object object);

    public final long executeCached(Object object) {
        return execute(this, object);
    }

    @Specialization
    static long coerceInt(int value) {
        return value;
    }

    @Specialization
    static long coerceLong(long value) {
        return value;
    }

    @Specialization
    static long coerceRubyBignum(Node node, RubyBignum value) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).rangeError("bignum too big to convert into 'long'", node));
    }

    @Specialization
    static long coerceDouble(Node node, double value,
            @Cached InlinedBranchProfile errorProfile) {
        // emulate MRI logic
        // We check for `value < MAX_VALUE` because casting Long.MAX_VALUE to double yields a double value of 2^63 which is >
        // Long.MAX_VALUE.
        if (Long.MIN_VALUE <= value && value < Long.MAX_VALUE) {
            return (long) value;
        } else {
            errorProfile.enter(node);
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).rangeError(Utils.concat("float ", value, " out of range of integer"), node));
        }
    }

    @Specialization
    static long coerceNil(Node node, Nil nil) {
        // MRI hardcodes this specific error message, which is slightly different from the one we would get in the
        // catch-all case.
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).typeError("no implicit conversion from nil to integer", node));
    }

    @Fallback
    static long coerceObject(Node node, Object object,
            @Cached(inline = false) DispatchNode toIntNode,
            @Cached(inline = false) ToLongNode fitNode) {
        final Object coerced = toIntNode
                .call(coreLibrary(node).truffleTypeModule, "rb_to_int_fallback", object);
        return fitNode.executeCached(coerced);
    }
}
