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
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class ReadPostArgumentNode extends RubyContextSourceNode {

    private final int indexFromCount;
    private final int required;
    private final int optional;
    private final boolean hasRest;
    private final boolean keywordArguments;
    private final ConditionProfile enoughArguments = ConditionProfile.create();

    public ReadPostArgumentNode(
            int indexFromCount,
            int required,
            int optional,
            boolean hasRest,
            boolean keywordArguments) {
        this.indexFromCount = indexFromCount;
        this.required = required;
        this.optional = optional;
        this.hasRest = hasRest;
        this.keywordArguments = keywordArguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int length = RubyArguments.getPositionalArgumentsCount(frame, keywordArguments);

        // required parameters - parameters before optional/rest and parameters after them
        if (enoughArguments.profile(length >= required)) {
            final int effectiveIndex;

            if (hasRest || length <= optional + required) {
                // rest parameter/optional parameters consume **all** the extra arguments
                // and post parameters consume trailing arguments:
                //   proc { |a, *b, c, d| [a, b, c, d] }.call(1, 2, 3, 4) # => [1, [2], 3, 4]
                //   proc { |a, b=:b, c=:c, d| [a, b, c, d] }.call(1, 2, 3, 4) # => [1, 2, 3, 4]
                //   proc { |a, b=:b, c=:c, d| [a, b, c, d] }.call(1, 2, 3) # => [1, 2, :c, 3]
                effectiveIndex = length - indexFromCount;
            } else {
                // optional and post parameters are fulfilled, extra arguments - skipped:
                //   proc { |a, b=:b, c| [a, b, c] }.call(1, 2, 3, 4) # => [1, 2, 3]
                effectiveIndex = optional + required - indexFromCount;
            }

            return RubyArguments.getArgument(frame, effectiveIndex);
        } else {
            // CheckArityNode will prevent this case for methods & lambdas, but it is still possible for procs.

            // it's the  simplest case:
            // - optional and rest parameters don't capture anything
            // - pre and post parameters capture arguments from left to right:
            //   proc { |a, b=:b, *c, d, e| [a, b, c, d, e] }.call(1, 2) # => [1, :b, [], 2, nil]

            final int effectiveIndex = required - indexFromCount;

            if (effectiveIndex < length) {
                return RubyArguments.getArgument(frame, effectiveIndex);
            } else {
                return nil;
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " -" + indexFromCount;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadPostArgumentNode(indexFromCount, required, optional, hasRest, keywordArguments);
        return copy.copyFlags(this);
    }

}
