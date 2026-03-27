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
package org.truffleruby.language.supercall;

import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.array.ArrayToObjectArrayNode;
import org.truffleruby.core.array.ArrayToObjectArrayNodeGen;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/** Get the arguments of a super call with implicit arguments (using the ones of the surrounding method). */
public final class ReadZSuperArgumentsNode extends RubyContextSourceNode {

    @Children private final RubyNode[] reloadNodes;
    @Child private ArrayToObjectArrayNode unsplatNode;

    private final int restArgIndex;
    private final ConditionProfile isArrayProfile = ConditionProfile.create();

    public ReadZSuperArgumentsNode(int restArgIndex, RubyNode[] reloadNodes) {
        this.restArgIndex = restArgIndex;
        this.reloadNodes = reloadNodes;
    }

    @ExplodeLoop
    @Override
    public final Object execute(VirtualFrame frame) {
        // Reload the arguments
        Object[] superArguments = new Object[reloadNodes.length];
        for (int n = 0; n < superArguments.length; n++) {
            superArguments[n] = reloadNodes[n].execute(frame);
        }

        if (restArgIndex != -1) {
            final Object restArg = superArguments[restArgIndex];

            final Object[] restArgs;
            if (isArrayProfile.profile(restArg instanceof RubyArray)) {
                restArgs = unsplat((RubyArray) restArg);
            } else {
                restArgs = new Object[]{ restArg };
            }

            final int after = superArguments.length - (restArgIndex + 1);
            Object[] splattedArguments = ArrayUtils.copyOf(superArguments, superArguments.length + restArgs.length - 1);
            ArrayUtils.arraycopy(
                    superArguments,
                    restArgIndex + 1,
                    splattedArguments,
                    restArgIndex + restArgs.length,
                    after);
            ArrayUtils.arraycopy(restArgs, 0, splattedArguments, restArgIndex, restArgs.length);
            superArguments = splattedArguments;
        }

        return superArguments;
    }

    private Object[] unsplat(RubyArray array) {
        if (unsplatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            unsplatNode = insert(ArrayToObjectArrayNodeGen.create());
        }
        return unsplatNode.executeToObjectArray(array);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadZSuperArgumentsNode(
                restArgIndex,
                cloneUninitialized(reloadNodes));
        return copy.copyFlags(this);
    }

}
