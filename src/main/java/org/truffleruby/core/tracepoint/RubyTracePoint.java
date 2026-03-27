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

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import java.util.Set;

public final class RubyTracePoint extends RubyDynamicObject implements ObjectGraphNode {

    TracePointEvent[] events;
    RubyProc proc;

    public RubyTracePoint(RubyClass rubyClass, Shape shape, TracePointEvent[] events, RubyProc proc) {
        super(rubyClass, shape);
        this.events = events;
        this.proc = proc;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, proc);
    }
}
