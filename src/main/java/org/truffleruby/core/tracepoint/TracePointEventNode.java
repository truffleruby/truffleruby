/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.tracepoint;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.arguments.RubyArguments;

final class TracePointEventNode extends TraceBaseEventNode {

    private final ConditionProfile inTracePointProfile = ConditionProfile.create();

    private final RubyTracePoint tracePoint;
    private final RubyProc proc;
    private final RubySymbol event;
    private final RubyLanguage language;
    private final boolean cCall;

    public TracePointEventNode(
            RubyContext context,
            RubyLanguage language,
            EventContext eventContext,
            RubyTracePoint tracePoint,
            RubySymbol event) {
        super(context, language, eventContext);
        this.tracePoint = tracePoint;
        this.proc = tracePoint.proc;
        this.event = event;
        this.language = language;
        this.cCall = event == language.coreSymbols.C_CALL;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        final RubyThread rubyThread = RubyLanguage.get(this).getCurrentThread();
        if (inTracePointProfile.profile(rubyThread.insideTracePointProc)) {
            return;
        }

        // A new object per event passed as the block argument, so the event data, the Binding and the frame can stay
        // virtual when the block is inlined, see RubyTracePointEvent
        final RubyTracePointEvent tracePointEvent;
        if (cCall) {
            // Like CRuby, the location of a :c_call event is the call site and there is no binding
            tracePointEvent = new RubyTracePointEvent(tracePoint, language.tracePointEventShape, event, Nil.INSTANCE,
                    0, null, null, RubyArguments.getSelf(frame), RubyArguments.getMethod(frame));
            setCallerLocation(tracePointEvent);
        } else {
            tracePointEvent = new RubyTracePointEvent(tracePoint, language.tracePointEventShape, event, getFile(),
                    getLine(), frame.materialize(), eventContext.getInstrumentedSourceSection(),
                    RubyArguments.getSelf(frame), RubyArguments.getMethod(frame));
        }

        rubyThread.insideTracePointProc = true;
        try {
            callBlock(proc, tracePointEvent);
        } finally {
            rubyThread.insideTracePointProc = false;
            tracePointEvent.deactivate();
        }
    }

    @TruffleBoundary
    private void setCallerLocation(RubyTracePointEvent tracePointEvent) {
        final SourceSection callerSourceSection = context.getCallStack().getCallerSourceSection();
        if (callerSourceSection != null) {
            tracePointEvent.path = language.getPathToTStringCache().createPathString(context,
                    callerSourceSection.getSource());
            tracePointEvent.line = language.getStartLineAdjusted(callerSourceSection);
        }
    }

}
