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
package org.truffleruby.language.objects;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.LexicalScope;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class LexicalScopeNode extends RubyContextSourceNode {

    private final LexicalScope lexicalScope;

    public LexicalScopeNode(LexicalScope lexicalScope) {
        this.lexicalScope = lexicalScope;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return lexicalScope.getLiveModule();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new LexicalScopeNode(lexicalScope);
        return copy.copyFlags(this);
    }

}
