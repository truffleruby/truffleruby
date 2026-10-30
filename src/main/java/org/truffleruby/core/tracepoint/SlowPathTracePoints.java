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
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/** The active TracePoints for events which are implemented without Truffle instrumentation. Instead, the code
 * triggering such an event explicitly checks whether there is any active TracePoint for it and calls them if so. This
 * avoids invalidating compiled code and the memory footprint of instrumentation wrappers when such a TracePoint is
 * enabled or disabled, which matters for events triggered by operations which are anyway slow, like defining a module
 * or class, switching fibers or starting a thread. */
public final class SlowPathTracePoints {

    /** The active TracePoints for one event, the most recently enabled first like in CRuby */
    public static final class ActiveTracePoints {

        private static final RubyTracePoint[] EMPTY = new RubyTracePoint[0];

        /** Whether there is any active TracePoint. This is a single field so the check on the fast path is as cheap as
         * possible. */
        private volatile boolean any = false;
        private volatile RubyTracePoint[] tracePoints = EMPTY;

        public boolean any() {
            return any;
        }

        synchronized void add(RubyTracePoint tracePoint) {
            final RubyTracePoint[] copy = new RubyTracePoint[tracePoints.length + 1];
            copy[0] = tracePoint;
            System.arraycopy(tracePoints, 0, copy, 1, tracePoints.length);
            tracePoints = copy;
            any = true;
        }

        synchronized void remove(RubyTracePoint tracePoint) {
            final RubyTracePoint[] copy = new RubyTracePoint[tracePoints.length - 1];
            int j = 0;
            for (RubyTracePoint element : tracePoints) {
                if (element != tracePoint) {
                    copy[j++] = element;
                }
            }
            assert j == copy.length : "TracePoint was not active";
            tracePoints = copy;
            any = copy.length > 0;
        }

    }

    private final RubyContext context;
    private final RubyLanguage language;

    public final ActiveTracePoints classTracePoints = new ActiveTracePoints();
    public final ActiveTracePoints endTracePoints = new ActiveTracePoints();
    public final ActiveTracePoints fiberSwitchTracePoints = new ActiveTracePoints();
    public final ActiveTracePoints threadBeginTracePoints = new ActiveTracePoints();
    public final ActiveTracePoints threadEndTracePoints = new ActiveTracePoints();
    /** For the :never event, which is used for not-yet-implemented events and is never triggered */
    public final ActiveTracePoints neverTracePoints = new ActiveTracePoints();

    public SlowPathTracePoints(RubyContext context) {
        this.context = context;
        this.language = context.getLanguageSlow();
    }

    @TruffleBoundary
    private ActiveTracePoints forEvent(RubySymbol event) {
        if (event == language.coreSymbols.CLASS) {
            return classTracePoints;
        } else if (event == language.coreSymbols.END) {
            return endTracePoints;
        } else if (event == language.coreSymbols.FIBER_SWITCH) {
            return fiberSwitchTracePoints;
        } else if (event == language.coreSymbols.THREAD_BEGIN) {
            return threadBeginTracePoints;
        } else if (event == language.coreSymbols.THREAD_END) {
            return threadEndTracePoints;
        } else if (event == language.coreSymbols.NEVER) {
            return neverTracePoints;
        } else {
            throw CompilerDirectives.shouldNotReachHere(event.getString());
        }
    }

    void add(RubySymbol event, RubyTracePoint tracePoint) {
        forEvent(event).add(tracePoint);
    }

    void remove(RubySymbol event, RubyTracePoint tracePoint) {
        forEvent(event).remove(tracePoint);
    }

    /** Calls the active :class TracePoints at the beginning of a class/module body at the given node. The frame is used
     * for TracePoint#binding, etc. */
    @TruffleBoundary
    public void fireClassEvent(MaterializedFrame frame, RubyNode node) {
        fireModuleBodyEvent(classTracePoints, language.coreSymbols.CLASS, frame, node);
    }

    /** Calls the active :end TracePoints at the end of a class/module body, the given node is at the end keyword. */
    @TruffleBoundary
    public void fireEndEvent(MaterializedFrame frame, RubyNode node) {
        fireModuleBodyEvent(endTracePoints, language.coreSymbols.END, frame, node);
    }

    private void fireModuleBodyEvent(ActiveTracePoints tracePoints, RubySymbol event, MaterializedFrame frame,
            RubyNode node) {
        final SourceSection sourceSection = node.getSourceSection();
        if (sourceSection.getSource().isInternal()) {
            return;
        }

        fire(tracePoints, event, RubyArguments.getSelf(frame), frame, sourceSection);
    }

    /** Calls the active :fiber_switch TracePoints in a fiber which just started, before running its block. There is no
     * Ruby frame yet, so there is no location and binding, like in CRuby. */
    @TruffleBoundary
    public void fireFiberStartEvent() {
        if (!fiberSwitchTracePoints.any()) {
            return;
        }

        fire(fiberSwitchTracePoints, language.coreSymbols.FIBER_SWITCH, language.getCurrentThread(), Nil.INSTANCE, 0,
                null, null, null);
    }

    /** Calls the active :fiber_switch TracePoints in a fiber which just gained control back after calling a method
     * switching fibers. The location is that of the Ruby caller of that method, like in CRuby. However
     * TracePoint#binding is nil, unlike CRuby: materializing the caller frame here would deoptimize it on every fiber
     * switch, and the frame would need to be captured before switching away from this fiber. It's also quite odd that
     * CRuby gives a Binding of the frame of the caller Fiber, not the current one, so we suspect no one uses it. */
    @TruffleBoundary
    public void fireFiberSwitchEvent() {
        if (!fiberSwitchTracePoints.any()) {
            return;
        }

        final RubyThread thread = language.getCurrentThread();
        final Node callerNode = context.getCallStack().getCallerNode();
        final SourceSection sourceSection = callerNode == null ? null : callerNode.getEncapsulatingSourceSection();

        if (sourceSection != null && sourceSection.isAvailable()) {
            final Object path = language.getPathToTStringCache().createPathString(context, sourceSection.getSource());
            final int line = language.getStartLineAdjusted(sourceSection);
            fire(fiberSwitchTracePoints, language.coreSymbols.FIBER_SWITCH, thread, path, line, null, null, null);
        } else {
            fire(fiberSwitchTracePoints, language.coreSymbols.FIBER_SWITCH, thread, Nil.INSTANCE, 0, null, null, null);
        }
    }

    /** Calls the active :thread_begin TracePoints in the given thread, before running its block. There is no Ruby frame
     * yet, so there is no location and binding, like in CRuby. */
    @TruffleBoundary
    public void fireThreadBeginEvent(RubyThread thread) {
        if (!threadBeginTracePoints.any()) {
            return;
        }

        fire(threadBeginTracePoints, language.coreSymbols.THREAD_BEGIN, thread, Nil.INSTANCE, 0, null, null, null);
    }

    /** Calls the active :thread_end TracePoints in the given thread, after running its block. */
    @TruffleBoundary
    public void fireThreadEndEvent(RubyThread thread) {
        if (!threadEndTracePoints.any()) {
            return;
        }

        fire(threadEndTracePoints, language.coreSymbols.THREAD_END, thread, Nil.INSTANCE, 0, null, null, null);
    }

    private void fire(ActiveTracePoints tracePoints, RubySymbol event, Object self, MaterializedFrame frame,
            SourceSection sourceSection) {
        final Source source = sourceSection.getSource();
        final Object path = language.getPathToTStringCache().createPathString(context, source);
        final int line = language.getStartLineAdjusted(sourceSection);
        final InternalMethod method = RubyArguments.getMethod(frame);
        fire(tracePoints, event, self, path, line, frame, sourceSection, method);
    }

    private void fire(ActiveTracePoints tracePoints, RubySymbol event, Object self, Object path, int line,
            MaterializedFrame frame, SourceSection sourceSection, InternalMethod method) {
        final RubyThread thread = language.getCurrentThread();
        if (thread.insideTracePointProc) {
            return;
        }

        for (RubyTracePoint tracePoint : tracePoints.tracePoints) {
            var tracePointEvent = new RubyTracePointEvent(tracePoint, language.tracePointEventShape, event, path,
                    line, frame, sourceSection, self, method);

            thread.insideTracePointProc = true;
            try {
                CallBlockNode.yieldUncached(tracePoint.proc, tracePointEvent);
            } finally {
                thread.insideTracePointProc = false;
                tracePointEvent.deactivate();
            }
        }
    }

}
