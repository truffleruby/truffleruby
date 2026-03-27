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
package org.truffleruby.language.defined;

import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class DefinedNode extends RubyContextSourceNodeCustomExecuteVoid {

    @Child private RubyNode child;

    public DefinedNode(RubyNode child) {
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return child.isDefined(frame, getLanguage(), getContext());
    }

    @Override
    public Nil executeVoid(VirtualFrame frame) {
        // do nothing
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new DefinedNode(child.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
