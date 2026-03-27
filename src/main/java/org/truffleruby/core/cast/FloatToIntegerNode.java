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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.FixnumOrBignumNode;
import org.truffleruby.language.RubyBaseNode;

import org.truffleruby.language.control.RaiseException;

@GenerateCached(false)
@GenerateInline
public abstract class FloatToIntegerNode extends RubyBaseNode {

    public abstract Object execute(Node node, double value);

    @Specialization
    static Object fixnumOrBignum(Node node, double value,
            @Cached FixnumOrBignumNode fixnumOrBignumNode,
            @Cached InlinedBranchProfile errorProfile,
            @Cached InlinedConditionProfile longFromDoubleProfile,
            @Cached InlinedConditionProfile integerFromDoubleProfile) {
        if (integerFromDoubleProfile.profile(node, value > Integer.MIN_VALUE && value < Integer.MAX_VALUE)) {
            return (int) value;
        } else if (longFromDoubleProfile.profile(node, value > Long.MIN_VALUE && value < Long.MAX_VALUE)) {
            return (long) value;
        } else {
            if (Double.isInfinite(value)) {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).floatDomainError("Infinity", node));
            }

            if (Double.isNaN(value)) {
                errorProfile.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).floatDomainError("NaN", node));
            }

            return fixnumOrBignumNode.execute(node, BigIntegerOps.fromDouble(value));
        }
    }
}
