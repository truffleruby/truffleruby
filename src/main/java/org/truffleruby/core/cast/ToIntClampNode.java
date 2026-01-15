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

import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.utils.Utils;

/** Like {@link ToIntNode} but if the value is in long range and not in int range then clamp the long to an int instead
 * of raising an error. Still raise an error for Bignum for compatibility with CRuby. */
@GenerateUncached
public abstract class ToIntClampNode extends RubyBaseNode {

    public abstract int execute(Object object);

    @Specialization
    int coerceInt(int value) {
        return value;
    }

    @Specialization(guards = "fitsInInteger(value)")
    int coerceFittingLong(long value) {
        return (int) value;
    }

    @Specialization(guards = "!fitsInInteger(value)")
    int coerceTooBigLong(long value) {
        return value < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }

    @Specialization
    int coerceRubyBignum(RubyBignum value) {
        // not 'int' to stay as compatible as possible with MRI errors
        throw new RaiseException(
                getContext(),
                coreExceptions().rangeError("bignum too big to convert into 'long'", this));
    }

    @Specialization
    int coerceDouble(double value,
            @Cached InlinedBranchProfile errorProfile) {
        // emulate MRI logic + additional 32 bit restriction
        long longValue = (long) value;
        if (CoreLibrary.fitsIntoInteger(longValue)) {
            return (int) longValue;
        } else if (Long.MIN_VALUE <= value && value <= Long.MAX_VALUE) {
            return longValue < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        } else {
            errorProfile.enter(this);
            throw new RaiseException(getContext(), coreExceptions()
                    .rangeError(Utils.concat("float ", value, " out of range of integer"), this));
        }
    }

    @Specialization
    int coerceNil(Nil value) {
        // MRI hardcodes this specific error message, which is slightly different from the one we would get in the
        // catch-all case.
        throw new RaiseException(
                getContext(),
                coreExceptions().typeError("no implicit conversion from nil to integer", this));
    }

    @Fallback
    int coerceObject(Object object,
            @Cached DispatchNode toIntNode,
            @Cached ToIntClampNode fitNode) {
        final Object coerced = toIntNode
                .call(coreLibrary().truffleTypeModule, "rb_to_int_fallback", object);
        return fitNode.execute(coerced);
    }
}
