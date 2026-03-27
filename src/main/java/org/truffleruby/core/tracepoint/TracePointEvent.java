/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
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
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.symbol.RubySymbol;

public final class TracePointEvent {

    public final Class<? extends Tag> tagClass;
    public final RubySymbol eventSymbol;

    private EventBinding<ExecutionEventNodeFactory> eventBinding;

    public TracePointEvent(Class<? extends Tag> tagClass, RubySymbol eventSymbol) {
        this.tagClass = tagClass;
        this.eventSymbol = eventSymbol;
    }

    public synchronized boolean hasEventBinding() {
        return eventBinding != null;
    }

    /** Returns whether the event was setup */
    @TruffleBoundary
    public synchronized boolean setupEventBinding(RubyContext context, RubyLanguage language,
            RubyTracePoint tracePoint) {
        if (eventBinding != null) {
            return false;
        }

        final SourceSectionFilter sourceSectionFilter = SourceSectionFilter
                .newBuilder()
                .mimeTypeIs(RubyLanguage.MIME_TYPES)
                .tagIs(tagClass)
                .includeInternal(false)
                .build();
        this.eventBinding = context.getInstrumenter().attachExecutionEventFactory(
                sourceSectionFilter,
                eventContext -> new TracePointEventNode(context, language, eventContext, tracePoint, eventSymbol));
        return true;
    }

    /** Returns whether the event was disposed */
    @TruffleBoundary
    public synchronized boolean diposeEventBinding() {
        if (eventBinding != null) {
            this.eventBinding.dispose();
            this.eventBinding = null;
            return true;
        } else {
            return false;
        }
    }

}
