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
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.thread.RubyThread;

final class TracePointEventNode extends TraceBaseEventNode {

    private final ConditionProfile inTracePointProfile = ConditionProfile.create();

    private final RubyTracePoint tracePoint;
    private final RubyProc proc;
    private final RubySymbol event;
    private final RubyLanguage language;

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
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        final RubyThread rubyThread = RubyLanguage.get(this).getCurrentThread();
        final TracePointState state = rubyThread.tracePointState;

        if (inTracePointProfile.profile(state.insideProc)) {
            return;
        }

        state.event = event;
        state.path = getFile();
        state.line = getLine();
        state.binding = BindingNodes
                .createBinding(context, language, frame.materialize(), eventContext.getInstrumentedSourceSection());

        state.insideProc = true;
        try {
            callBlock(proc, tracePoint);
        } finally {
            state.insideProc = false;
            // Reset the binding so it can be escaped analyzed. This is also semantically correct
            // because the TracePoint information is reset outside the TracePoint block in MRI.
            state.binding = null;
        }
    }

}
