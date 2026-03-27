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
package org.truffleruby.core.binding;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.collections.Memo;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

@CoreModule("Truffle::Binding")
public abstract class TruffleBindingNodes {

    @CoreMethod(names = "of_caller", onSingleton = true)
    public abstract static class OfCallerNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object ofCaller() {
            /* When you use this method you're asking for the binding of the caller at the call site. When we get into
             * this method, that is then the binding of the caller of the caller. */

            final int[] frameCount = new int[]{ 0 };
            final Memo<SourceSection> sourceSection = new Memo<>(null);

            final MaterializedFrame frame = Truffle.getRuntime().iterateFrames(frameInstance -> {
                if (frameCount[0] == 2) {
                    sourceSection.set(frameInstance.getCallNode().getEncapsulatingSourceSection());
                    return frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
                } else {
                    frameCount[0] += 1;
                    return null;
                }
            });

            if (frame == null) {
                return nil;
            }

            return BindingNodes.createBinding(getContext(), getLanguage(), frame, sourceSection.get());
        }

    }

}
