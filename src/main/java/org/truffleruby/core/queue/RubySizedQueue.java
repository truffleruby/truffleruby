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
package org.truffleruby.core.queue;

import java.util.Set;

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.Shape;

public final class RubySizedQueue extends RubyDynamicObject implements ObjectGraphNode {

    SizedQueue queue;

    public RubySizedQueue(RubyClass rubyClass, Shape shape, SizedQueue queue) {
        super(rubyClass, shape);
        this.queue = queue;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        for (Object element : queue.getContents()) {
            if (ObjectGraph.isRubyObject(element)) {
                reachable.add(element);
            }
        }
    }

}
