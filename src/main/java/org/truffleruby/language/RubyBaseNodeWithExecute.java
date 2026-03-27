/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.frame.VirtualFrame;

/** A {@link org.truffleruby.language.RubyBaseNode} that has a "fundamental execute method" taking a
 * {@link VirtualFrame} and returning an {@code Object} - such a method is automatically invoked by Truffle when the
 * node is used as child node.
 *
 * <p>
 * This method lives in this separate class so that {@link RubyBaseNode} can be used as base class for nodes that must
 * support an uncached version but do not have child (and thus cannot implement the frame-only {@code execute} method.
 * Similarly, it is not in {@link RubyNode}, because it has fields that would make it impossible to automatically
 * generate an uncached version of the node. */
public abstract class RubyBaseNodeWithExecute extends RubyBaseNode {
    // Fundamental execute methods
    public abstract Object execute(VirtualFrame frame);

    public abstract RubyBaseNodeWithExecute cloneUninitialized();

    protected static RubyBaseNodeWithExecute[] cloneUninitialized(RubyBaseNodeWithExecute[] nodes) {
        RubyBaseNodeWithExecute[] copies = new RubyBaseNodeWithExecute[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copies[i] = nodes[i].cloneUninitialized();
        }
        return copies;
    }

}
