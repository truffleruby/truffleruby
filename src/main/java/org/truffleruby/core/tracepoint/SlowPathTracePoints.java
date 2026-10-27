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

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/** The active TracePoints for events which are implemented without Truffle instrumentation. Instead, the code
 * triggering such an event explicitly checks whether there is any active TracePoint for it and calls them if so. This
 * avoids invalidating compiled code and the memory footprint of instrumentation wrappers when such a TracePoint is
 * enabled or disabled, which matters for events triggered by operations which are anyway slow, like defining a module
 * or class. */
public final class SlowPathTracePoints {

    private static final RubyTracePoint[] EMPTY = new RubyTracePoint[0];

    private final RubyContext context;
    private final RubyLanguage language;

    /** Whether there is any active :class TracePoint. This is a single field so the check on the fast path is as cheap
     * as possible. */
    private volatile boolean hasClassTracePoints = false;
    /** The active :class TracePoints, the most recently enabled first like in CRuby */
    private volatile RubyTracePoint[] classTracePoints = EMPTY;

    public SlowPathTracePoints(RubyContext context) {
        this.context = context;
        this.language = context.getLanguageSlow();
    }

    public boolean hasClassTracePoints() {
        return hasClassTracePoints;
    }

    @TruffleBoundary
    synchronized void add(RubySymbol event, RubyTracePoint tracePoint) {
        if (event == language.coreSymbols.CLASS) {
            classTracePoints = prepend(tracePoint, classTracePoints);
            hasClassTracePoints = true;
        } else {
            throw CompilerDirectives.shouldNotReachHere(event.getString());
        }
    }

    @TruffleBoundary
    synchronized void remove(RubySymbol event, RubyTracePoint tracePoint) {
        if (event == language.coreSymbols.CLASS) {
            classTracePoints = remove(classTracePoints, tracePoint);
            hasClassTracePoints = classTracePoints.length > 0;
        } else {
            throw CompilerDirectives.shouldNotReachHere(event.getString());
        }
    }

    private static RubyTracePoint[] prepend(RubyTracePoint tracePoint, RubyTracePoint[] tracePoints) {
        final RubyTracePoint[] copy = new RubyTracePoint[tracePoints.length + 1];
        copy[0] = tracePoint;
        System.arraycopy(tracePoints, 0, copy, 1, tracePoints.length);
        return copy;
    }

    private static RubyTracePoint[] remove(RubyTracePoint[] tracePoints, RubyTracePoint tracePoint) {
        final RubyTracePoint[] copy = new RubyTracePoint[tracePoints.length - 1];
        int j = 0;
        for (RubyTracePoint element : tracePoints) {
            if (element != tracePoint) {
                copy[j++] = element;
            }
        }
        assert j == copy.length : "TracePoint was not active";
        return copy;
    }

    /** Calls the active :class TracePoints for a class/module body at the given node. The frame is used for
     * TracePoint#binding, etc. */
    @TruffleBoundary
    public void fireClassEvent(MaterializedFrame frame, RubyNode node) {
        final SourceSection sourceSection = node.getSourceSection();
        final Source source = sourceSection.getSource();
        if (source.isInternal()) {
            return;
        }

        final int line = language.getStartLineAdjusted(sourceSection);

        fire(classTracePoints, language.coreSymbols.CLASS, frame, sourceSection, line);
    }

    private void fire(RubyTracePoint[] tracePoints, RubySymbol event, MaterializedFrame frame,
            SourceSection sourceSection, int line) {
        final TracePointState state = language.getCurrentThread().tracePointState;
        if (state.insideProc) {
            return;
        }

        for (RubyTracePoint tracePoint : tracePoints) {
            state.event = event;
            state.path = language.getPathToTStringCache().createPathString(context, sourceSection.getSource());
            state.line = line;
            state.binding = BindingNodes.createBinding(context, language, frame, sourceSection);

            state.insideProc = true;
            try {
                CallBlockNode.yieldUncached(tracePoint.proc, tracePoint);
            } finally {
                state.insideProc = false;
                // Reset the binding so it can be escaped analyzed. This is also semantically correct
                // because the TracePoint information is reset outside the TracePoint block in MRI.
                state.binding = null;
            }
        }
    }

}
