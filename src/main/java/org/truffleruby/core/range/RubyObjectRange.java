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
package org.truffleruby.core.range;

import java.util.Set;

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

public final class RubyObjectRange extends RubyDynamicObject implements ObjectGraphNode {

    public boolean excludedEnd;
    public boolean frozen;
    public Object begin;
    public Object end;

    public RubyObjectRange(
            RubyClass rubyClass,
            Shape shape,
            boolean excludedEnd,
            Object begin,
            Object end,
            boolean frozen) {
        super(rubyClass, shape);
        this.excludedEnd = excludedEnd;
        this.begin = begin;
        this.end = end;
        this.frozen = frozen;
    }

    public boolean isBoundless() {
        return begin == Nil.INSTANCE && end == Nil.INSTANCE;
    }

    public boolean isEndless() {
        return begin != Nil.INSTANCE && end == Nil.INSTANCE;
    }

    public boolean isBeginless() {
        return begin == Nil.INSTANCE && end != Nil.INSTANCE;
    }

    public boolean isBounded() {
        return begin != Nil.INSTANCE && end != Nil.INSTANCE;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, begin);
        ObjectGraph.addProperty(reachable, end);
    }
}
