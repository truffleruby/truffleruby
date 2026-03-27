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
import org.truffleruby.language.NotProvided;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.RubyNode;
import org.truffleruby.utils.Utils;

public final class ReadPreArgumentNode extends RubyContextSourceNode {

    private final int index;
    private final boolean keywordArguments;

    private final BranchProfile outOfRangeProfile = BranchProfile.create();
    private final MissingArgumentBehavior missingArgumentBehavior;

    public ReadPreArgumentNode(
            int index,
            boolean keywordArguments,
            MissingArgumentBehavior missingArgumentBehavior) {
        this.index = index;
        this.keywordArguments = keywordArguments;
        this.missingArgumentBehavior = missingArgumentBehavior;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final int positionalArgumentsCount = RubyArguments.getPositionalArgumentsCount(frame, keywordArguments);

        if (index < positionalArgumentsCount) {
            return RubyArguments.getArgument(frame, index);
        }

        outOfRangeProfile.enter();

        switch (missingArgumentBehavior) {
            case RUNTIME_ERROR:
                throw new IndexOutOfBoundsException();

            case NOT_PROVIDED:
                return NotProvided.INSTANCE;

            case NIL:
                return nil;

            default:
                throw Utils.unsupportedOperation("unknown missing argument behaviour");
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + index;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadPreArgumentNode(index, keywordArguments, missingArgumentBehavior);
        return copy.copyFlags(this);
    }

}
