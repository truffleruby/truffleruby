/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.tracepoint;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.shared.TruffleRuby;

/** One event of a TracePoint, either implemented with Truffle instrumentation ({@link Instrumented}) or by registering
 * the TracePoint in {@link SlowPathTracePoints} ({@link SlowPath}). */
public abstract class TracePointEvent {

    public final RubySymbol eventSymbol;

    protected TracePointEvent(RubySymbol eventSymbol) {
        this.eventSymbol = eventSymbol;
    }

    public abstract boolean isEnabled();

    /** Returns whether the event was enabled by this call, false if it was already enabled */
    public abstract boolean enable(RubyContext context, RubyLanguage language, RubyTracePoint tracePoint);

    /** Returns whether the event was disabled by this call, false if it was already disabled */
    public abstract boolean disable(RubyContext context, RubyTracePoint tracePoint);

    /** A TracePoint event implemented by attaching an instrument on all nodes with the given tag. */
    public static final class Instrumented extends TracePointEvent {

        private final Class<? extends Tag> tagClass;

        private EventBinding<ExecutionEventNodeFactory> eventBinding;

        public Instrumented(Class<? extends Tag> tagClass, RubySymbol eventSymbol) {
            super(eventSymbol);
            this.tagClass = tagClass;
        }

        @Override
        public synchronized boolean isEnabled() {
            return eventBinding != null;
        }

        @Override
        @TruffleBoundary
        public synchronized boolean enable(RubyContext context, RubyLanguage language, RubyTracePoint tracePoint) {
            if (eventBinding != null) {
                return false;
            }

            final SourceSectionFilter sourceSectionFilter = SourceSectionFilter
                    .newBuilder()
                    .sourceFilter(SourceFilter.newBuilder().languageIs(TruffleRuby.LANGUAGE_ID).build())
                    .tagIs(tagClass)
                    .includeInternal(false)
                    .build();
            if (tagClass == TraceManager.CallTag.class) {
                language.startTracingCalls();
            }
            this.eventBinding = context.getInstrumenter().attachExecutionEventFactory(
                    sourceSectionFilter,
                    eventContext -> new TracePointEventNode(context, language, eventContext, tracePoint, eventSymbol));
            return true;
        }

        @Override
        @TruffleBoundary
        public synchronized boolean disable(RubyContext context, RubyTracePoint tracePoint) {
            if (eventBinding != null) {
                this.eventBinding.dispose();
                this.eventBinding = null;
                if (tagClass == TraceManager.CallTag.class) {
                    context.getLanguageSlow().stopTracingCalls();
                }
                return true;
            } else {
                return false;
            }
        }

    }

    /** A TracePoint event implemented by registering the TracePoint in {@link SlowPathTracePoints}. */
    public static final class SlowPath extends TracePointEvent {

        private boolean enabled = false;

        public SlowPath(RubySymbol eventSymbol) {
            super(eventSymbol);
        }

        @Override
        public synchronized boolean isEnabled() {
            return enabled;
        }

        @Override
        public synchronized boolean enable(RubyContext context, RubyLanguage language, RubyTracePoint tracePoint) {
            if (enabled) {
                return false;
            }

            context.getSlowPathTracePoints().add(eventSymbol, tracePoint);
            enabled = true;
            return true;
        }

        @Override
        public synchronized boolean disable(RubyContext context, RubyTracePoint tracePoint) {
            if (!enabled) {
                return false;
            }

            context.getSlowPathTracePoints().remove(eventSymbol, tracePoint);
            enabled = false;
            return true;
        }

    }

}
