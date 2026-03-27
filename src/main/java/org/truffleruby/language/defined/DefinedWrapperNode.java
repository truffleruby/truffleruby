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
package org.truffleruby.language.defined;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.CoreString;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class DefinedWrapperNode extends RubyContextSourceNode {

    private final CoreString definition;

    @Child private RubyNode child;

    public DefinedWrapperNode(CoreString definition, RubyNode child) {
        this.definition = definition;
        this.child = child;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return child.execute(frame);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return definition.createInstance(context);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new DefinedWrapperNode(
                definition,
                child.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
