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

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class ReadOptionalArgumentNode extends RubyContextSourceNode {

    private final int index;
    private final int minimum;
    private final boolean keywordArguments;
    @Child private RubyNode defaultValue;
    private final BranchProfile defaultValueProfile = BranchProfile.create();

    public ReadOptionalArgumentNode(
            int index,
            int minimum,
            boolean keywordArguments,
            RubyNode defaultValue) {
        this.index = index;
        this.minimum = minimum;
        this.keywordArguments = keywordArguments;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int positionalArgumentsCount = RubyArguments.getPositionalArgumentsCount(frame, keywordArguments);

        if (positionalArgumentsCount >= minimum) {
            // it's enough arguments to fulfill pre and post parameters and optional parameters till the current one:
            //   proc { |a, b=:b, c=:c, d| [a, b, c, d] }.call(1, 2, 3) # => [1, 2, :c, 3]
            return RubyArguments.getArgument(frame, index);
        } else {
            defaultValueProfile.enter();
            return defaultValue.execute(frame);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + index;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadOptionalArgumentNode(
                index,
                minimum,
                keywordArguments,
                defaultValue.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
