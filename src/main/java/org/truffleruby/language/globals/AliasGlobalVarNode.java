/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.globals;

import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyNode;

public final class AliasGlobalVarNode extends RubyContextSourceNode {

    private final String oldName;
    private final String newName;

    public AliasGlobalVarNode(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        getContext().getCoreLibrary().globalVariables.alias(oldName, newName);
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new AliasGlobalVarNode(oldName, newName);
        return copy.copyFlags(this);
    }

}
