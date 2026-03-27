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
package org.truffleruby.language.literal;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class FrozenStringLiteralNode extends RubyContextSourceNode {

    private final ImmutableRubyString frozenString;
    private final ImmutableRubyString definition;

    public FrozenStringLiteralNode(ImmutableRubyString frozenString, ImmutableRubyString definition) {
        this.frozenString = frozenString;
        this.definition = definition;
    }

    public ImmutableRubyString getFrozenString() {
        return frozenString;
    }

    @Override
    public ImmutableRubyString execute(VirtualFrame frame) {
        return frozenString;
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return definition;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new FrozenStringLiteralNode(frozenString, definition);
        return copy.copyFlags(this);
    }

}
