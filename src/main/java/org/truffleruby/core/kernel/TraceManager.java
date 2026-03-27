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
package org.truffleruby.core.kernel;

import java.util.ArrayList;
import java.util.Collection;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.tracepoint.TraceBaseEventNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.objects.LogicalClassNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class TraceManager {
    public static final class LineTag extends Tag {
    }

    public static final class CallTag extends Tag {
    }

    public static final class ClassTag extends Tag {
    }

    /** A tag which applies to no Node, but is useful to handle not-yet-implemented TracePoint events. */
    public static final class NeverTag extends Tag {
    }

    private final RubyContext context;
    private final RubyLanguage language;
    private final Instrumenter instrumenter;

    private Collection<EventBinding<?>> instruments;
    private boolean isInTraceFunc = false;

    public TraceManager(RubyLanguage language, RubyContext context, Instrumenter instrumenter) {
        this.language = language;
        this.context = context;
        this.instrumenter = instrumenter;
    }

    @TruffleBoundary
    public synchronized void setTraceFunc(RubyProc traceFunc) {

        if (instruments != null) {
            for (EventBinding<?> instrument : instruments) {
                instrument.dispose();
            }
        }

        if (traceFunc == null) {
            // Update to a new valid assumption
            language.traceFuncUnusedAssumption.invalidate();
            instruments = null;
            return;
        }

        // Invalidate current assumption
        language.traceFuncUnusedAssumption.getAssumption().invalidate();

        instruments = new ArrayList<>();

        instruments.add(
                instrumenter.attachExecutionEventFactory(
                        SourceSectionFilter
                                .newBuilder()
                                .mimeTypeIs(RubyLanguage.MIME_TYPES)
                                .tagIs(LineTag.class)
                                .build(),
                        eventContext -> new BaseEventEventNode(
                                context,
                                language,
                                eventContext,
                                traceFunc,
                                language.coreStrings.LINE.createInstance(context))));

        instruments.add(
                instrumenter.attachExecutionEventFactory(
                        SourceSectionFilter
                                .newBuilder()
                                .mimeTypeIs(RubyLanguage.MIME_TYPES)
                                .tagIs(ClassTag.class)
                                .build(),
                        eventContext -> new BaseEventEventNode(
                                context,
                                language,
                                eventContext,
                                traceFunc,
                                language.coreStrings.CLASS.createInstance(context))));

        if (context.getOptions().TRACE_CALLS) {
            instruments.add(
                    instrumenter.attachExecutionEventFactory(
                            SourceSectionFilter
                                    .newBuilder()
                                    .mimeTypeIs(RubyLanguage.MIME_TYPES)
                                    .tagIs(CallTag.class)
                                    .includeInternal(false)
                                    .build(),
                            eventContext -> new CallEventEventNode(
                                    context,
                                    language,
                                    eventContext,
                                    traceFunc,
                                    language.coreStrings.CALL.createInstance(context))));
        }
    }

    private class BaseEventEventNode extends TraceBaseEventNode {

        protected final ConditionProfile inTraceFuncProfile = ConditionProfile.create();

        protected final RubyProc traceFunc;
        protected final Object event;

        public BaseEventEventNode(
                RubyContext context,
                RubyLanguage language,
                EventContext eventContext,
                RubyProc traceFunc,
                Object event) {
            super(context, language, eventContext);
            this.traceFunc = traceFunc;
            this.event = event;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (inTraceFuncProfile.profile(isInTraceFunc)) {
                return;
            }

            isInTraceFunc = true;
            try {
                callBlock(
                        traceFunc,
                        event,
                        getFile(),
                        getLine(),
                        Nil.INSTANCE,
                        BindingNodes.createBinding(
                                context,
                                language,
                                frame.materialize(),
                                eventContext.getInstrumentedSourceSection()),
                        Nil.INSTANCE);
            } finally {
                isInTraceFunc = false;
            }
        }

    }

    private final class CallEventEventNode extends BaseEventEventNode {

        @Child private LogicalClassNode logicalClassNode;

        public CallEventEventNode(
                RubyContext context,
                RubyLanguage language,
                EventContext eventContext,
                RubyProc traceFunc,
                Object event) {
            super(context, language, eventContext, traceFunc, event);
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (inTraceFuncProfile.profile(isInTraceFunc)) {
                return;
            }

            isInTraceFunc = true;
            try {
                callBlock(
                        traceFunc,
                        event,
                        getFile(),
                        getLine(),
                        getSymbol(context, RubyArguments.getMethod(frame).getName()),
                        BindingNodes.createBinding(
                                context,
                                language,
                                frame.materialize(),
                                eventContext.getInstrumentedSourceSection()),
                        getLogicalClass(RubyArguments.getSelf(frame)));
            } finally {
                isInTraceFunc = false;
            }
        }

        private RubyClass getLogicalClass(Object object) {
            if (logicalClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                logicalClassNode = insert(LogicalClassNode.create());
            }

            return logicalClassNode.execute(object);
        }

    }

    @TruffleBoundary
    private RubySymbol getSymbol(RubyContext context, String string) {
        return context.getLanguageSlow().getSymbol(string);
    }
}
