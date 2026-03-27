/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.Nil;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import java.util.Set;

public final class RubyNoMethodError extends RubyNameError implements ObjectGraphNode {

    public Object args;

    public RubyNoMethodError(
            RubyClass rubyClass,
            Shape shape,
            Object message,
            Backtrace backtrace,
            Object cause,
            Object receiver,
            Object name,
            Object args) {
        super(rubyClass, shape, message, backtrace, cause, receiver, name);
        assert args == Nil.INSTANCE || args instanceof RubyArray;
        this.args = args;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        super.getAdjacentObjects(reachable);
        ObjectGraph.addProperty(reachable, args);
    }

}
