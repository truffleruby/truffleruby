/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.SpecialVariablesSendingNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

import com.oracle.truffle.api.frame.MaterializedFrame;

/** See {@link SpecialVariablesSendingNode} */
public class ReadCallerVariablesNode extends RubyBaseNode {

    private final ConditionProfile inArgumentsProfile = ConditionProfile.create();

    public static ReadCallerVariablesNode create() {
        return new ReadCallerVariablesNode();
    }

    public SpecialVariableStorage execute(Frame frame) {
        final SpecialVariableStorage data = RubyArguments.getCallerSpecialVariables(frame);
        if (inArgumentsProfile.profile(data != null)) {
            return data;
        } else {
            return getCallerSpecialVariables();
        }
    }

    @TruffleBoundary
    protected SpecialVariableStorage getCallerSpecialVariables() {
        final MaterializedFrame callerFrame = Truffle.getRuntime()
                .iterateFrames(f -> f.getFrame(FrameAccess.MATERIALIZE).materialize(), 1);
        if (!CallStackManager.isRubyFrame(callerFrame)) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().runtimeError(
                            "Cannot call Ruby method which needs caller special variables directly in a foreign language",
                            this));
        }

        return GetSpecialVariableStorage.executeUncached(callerFrame);
    }
}
