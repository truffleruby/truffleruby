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
package org.truffleruby.core.exception;

import java.util.Set;

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.Shape;

public final class RubyFrozenError extends RubyException implements ObjectGraphNode {

    public Object receiver;

    public RubyFrozenError(
            RubyClass rubyClass,
            Shape shape,
            Object message,
            Backtrace backtrace,
            Object cause,
            Object receiver) {
        super(rubyClass, shape, message, backtrace, cause);
        this.receiver = receiver;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        super.getAdjacentObjects(reachable);
        ObjectGraph.addProperty(reachable, receiver);
    }

}
