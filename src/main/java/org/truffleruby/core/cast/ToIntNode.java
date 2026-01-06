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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.numeric.IntegerNodes.IntegerLowerNode;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.utils.Utils;

/** Node used to convert a value into a 32-bits Java int, calling {@code to_int} if the value is not yet a Ruby integer.
 * Use this whenever Ruby allows conversions using {@code to_int} and you need a 32-bits int for implementation reasons.
 * This is equivalent to Ruby's C function {@code rb_num2int}.
 *
 * <p>
 * Alternatively, consider:
 * <ul>
 * <li>{@link ToLongNode}: similar, but for 64-bits Java long. Equivalent to Ruby's C function {@code rb_num2long},
 * which is used a lot by MRI (we replace some of these uses by {@link ToIntNode} in TruffleRuby, because arrays are
 * {@code int}-sized.</li>
 * <li>{@link ToRubyIntegerNode}: when only {@code to_int} conversion is needed, but the resulting value can be a
 * Bignum. It matches Ruby's C function {@code rb_to_int}, and is a wrapper around our own (Ruby) implementation of that
 * function, in order to specialize more efficiently when the value is already an integer. Unlike {@link ToIntNode} and
 * {@link ToLongNode}, it does not handle {@code Float} values explicitly.</li>
 * <li>{@link IntegerCastNode}, {@link LongCastNode}: whenever {@code to_int} conversion is not required. Beware that
 * this will fail with a {@code TypeError} whenever the argument is out of range, whereas {@link ToLongNode} and
 * {@link ToIntNode} would have failed with a {@code RangeError}. Those are typically used when some other part of the
 * implementation should have guarantees that these values were {@code int} or {@code long}-sized in the first place.
 * <li>{@link IntegerLowerNode}: to lower {@code long} into {@code int} values if they fit. Can be useful conjointly
 * with {@link ToLongNode}.</li>
 * </ul>
 */
@GenerateUncached
public abstract class ToIntNode extends RubyBaseNode {

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
        // MRI does not have this error
        throw new RaiseException(
                getContext(),
                coreExceptions().rangeError("long too big to convert into 'int'", this));
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
        if (CoreLibrary.fitsIntoInteger((long) value)) {
            return (int) value;
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
            @Cached ToIntNode fitNode) {
        final Object coerced = toIntNode
                .call(coreLibrary().truffleTypeModule, "rb_to_int_fallback", object);
        return fitNode.execute(coerced);
    }
}
