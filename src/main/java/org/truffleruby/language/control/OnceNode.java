/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Objects;

/** Executes a child node just once, and uses the same value each subsequent time the node is executed. */
public final class OnceNode extends RubyContextSourceNode {

    static class Holder { // Not NodeCloneable, on purpose
        @CompilationFinal private volatile Object cachedValue;
    }

    @Child private RubyNode child;

    // An extra indirection so with splitting we compute the value only once, and the Holder is shared across splits.
    private final Holder holder = new Holder();

    public OnceNode(RubyNode child) {
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = holder.cachedValue;

        if (value == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                // Read `cachedValue` again to check if the value was updated by another thread while this thread
                // was waiting on the lock. If it's still null, this thread is the first one to get the lock and
                // must update the cache.
                value = holder.cachedValue;
                if (value == null) {
                    value = holder.cachedValue = Objects.requireNonNull(child.execute(frame));
                }
            }
        }

        return value;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new OnceNode(child.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
