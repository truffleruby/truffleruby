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

import java.util.Set;

import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;

/** The object yielded to a TracePoint block for one event. It has the same Ruby class as the {@link RubyTracePoint} it
 * belongs to, so all TracePoint methods work on it, and TracePoint#enable/disable/enabled? forward to
 * {@link #tracePoint}. Allocating one object per event and passing it as the block argument, instead of storing the
 * event data in a per-thread object, keeps the event data, the Binding and the traced frame virtual when the block is
 * inlined by the compiler, so the traced frame does not need to be materialized. */
public final class RubyTracePointEvent extends TracePointObject {

    /** The TracePoint this event belongs to */
    final RubyTracePoint tracePoint;
    final RubySymbol event;
    /** The RubyString path, or nil for events without a location like :thread_begin */
    Object path;
    int line;
    /** The frame of the event, or null for events without a frame or binding like :c_call and :thread_begin */
    MaterializedFrame frame;
    SourceSection sourceSection;
    /** The RubyBinding created lazily from {@link #frame}, or nil if there is no frame */
    Object binding = null;
    Object self;
    /** The InternalMethod of the frame, or null for events without a frame like :thread_begin */
    InternalMethod method;
    /** Whether the TracePoint block for this event is still running, the event data is only accessible then */
    boolean active = true;

    public RubyTracePointEvent(
            RubyTracePoint tracePoint,
            Shape shape,
            RubySymbol event,
            Object path,
            int line,
            MaterializedFrame frame,
            SourceSection sourceSection,
            Object self,
            InternalMethod method) {
        super(tracePoint.getLogicalClass(), shape);
        this.tracePoint = tracePoint;
        this.event = event;
        this.path = path;
        this.line = line;
        this.frame = frame;
        this.sourceSection = sourceSection;
        this.self = self;
        this.method = method;
    }

    /** Called once the TracePoint block returns: the event data is no longer accessible, like in CRuby, and the
     * references are cleared in case the object escaped the block */
    void deactivate() {
        active = false;
        frame = null;
        sourceSection = null;
        binding = Nil.INSTANCE;
        self = Nil.INSTANCE;
        method = null;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, tracePoint);
        ObjectGraph.addProperty(reachable, self);
        ObjectGraph.addProperty(reachable, binding);
    }

}
