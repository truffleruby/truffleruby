/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core.tracepoint;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

/** Triggers the :class TracePoint event before the body of a class or module definition, and the :end event after it,
 * when the body completes normally like in CRuby. The source section of this node is the whole definition, as needed
 * for the :class event, and the :end event uses the location of the end keyword. */
public final class FireClassAndEndTracePointsNode extends RubyContextSourceNode {

    private static final int END_KEYWORD_LENGTH = 3;

    @Child private RubyNode body;
    /** The source char index of the end keyword of the definition */
    private final int endKeywordCharIndex;

    private final BranchProfile classTracePointsProfile = BranchProfile.create();
    private final BranchProfile endTracePointsProfile = BranchProfile.create();

    public FireClassAndEndTracePointsNode(RubyNode body, int endKeywordCharIndex) {
        this.body = body;
        this.endKeywordCharIndex = endKeywordCharIndex;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final SlowPathTracePoints slowPathTracePoints = getContext().getSlowPathTracePoints();
        if (slowPathTracePoints.classTracePoints.any()) {
            classTracePointsProfile.enter();
            fireClassEvent(slowPathTracePoints, frame.materialize());
        }

        final Object value = body.execute(frame);

        if (slowPathTracePoints.endTracePoints.any()) {
            endTracePointsProfile.enter();
            fireEndEvent(slowPathTracePoints, frame.materialize());
        }
        return value;
    }

    @TruffleBoundary
    private void fireClassEvent(SlowPathTracePoints slowPathTracePoints, MaterializedFrame frame) {
        slowPathTracePoints.fireClassEvent(frame, getSourceSection());
    }

    @TruffleBoundary
    private void fireEndEvent(SlowPathTracePoints slowPathTracePoints, MaterializedFrame frame) {
        final SourceSection endKeywordSourceSection = getSourceSection().getSource().createSection(
                endKeywordCharIndex,
                END_KEYWORD_LENGTH);
        slowPathTracePoints.fireEndEvent(frame, endKeywordSourceSection);
    }

    @Override
    public RubyNode cloneUninitialized() {
        return new FireClassAndEndTracePointsNode(body.cloneUninitialized(), endKeywordCharIndex).copyFlags(this);
    }

}
