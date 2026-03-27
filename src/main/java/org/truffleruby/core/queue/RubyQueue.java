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

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import java.util.Set;

public final class RubyQueue extends RubyDynamicObject implements ObjectGraphNode {

    public final UnsizedQueue queue;

    public RubyQueue(RubyClass rubyClass, Shape shape, UnsizedQueue queue) {
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
