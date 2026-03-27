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
package org.truffleruby.debug;

import java.util.function.Supplier;

import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

final class MetricsBodyNode<T> extends RubyContextSourceNode {

    @Override
    public Object execute(VirtualFrame frame) {
        return call((Supplier<?>) frame.getArguments()[0]);
    }

    @TruffleBoundary
    private Object call(Supplier<?> supplier) {
        return supplier.get();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new MetricsBodyNode<T>();
        return copy.copyFlags(this);
    }

}
