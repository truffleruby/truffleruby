/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.language.arguments.ReadCallerVariablesNode;

import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.parser.FrameDescriptorInfo;

/** Some Ruby methods need access to the caller special variables: see usages of {@link ReadCallerVariablesNode}. This
 * is used for methods which need to access the last regexp MatchData or the last IO line.
 *
 * <p>
 * This means that when making a method call, we might need to pass down its {@link SpecialVariableStorage} active when
 * the method call was made.
 *
 * <p>
 * Always passing the {@link SpecialVariableStorage} would be expensive, and the point of this base node is to only pass
 * it when we know for sure it has been requested by the callee. It is also possible to walk the stack to retrieve the
 * caller special variables - but this is even slower and causes a deoptimization in the callee every time we walk the
 * stack.
 *
 * <p>
 * This class works in tandem with {@link GetSpecialVariableStorage} and {@link ReadCallerVariablesNode}. When those two
 * classes don't have {@link SpecialVariableStorage} in the frame they will invalidate the exception, so that the next
 * time the method is called, the special variables will be passed down. */
@SuppressFBWarnings("IS")
public abstract class SpecialVariablesSendingNode extends RubyBaseNode {

    @NeverDefault
    protected Assumption getSpecialVariableAssumption(Frame frame) {
        if (frame == null) {
            return Assumption.ALWAYS_VALID;
        }

        var descriptor = frame.getFrameDescriptor();
        return FrameDescriptorInfo.of(descriptor).getSpecialVariableAssumption();
    }

}
