/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.module.ModuleFields;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SetTopLevelBindingNode extends RubyContextSourceNode {

    @Override
    public Object execute(VirtualFrame frame) {
        final MaterializedFrame mainScriptFrame = frame.materialize();
        updateTopLevelBindingFrame(mainScriptFrame);
        return nil;
    }

    @TruffleBoundary
    private void updateTopLevelBindingFrame(MaterializedFrame mainScriptFrame) {
        final ModuleFields fields = coreLibrary().objectClass.fields;
        final RubyBinding toplevelBinding = (RubyBinding) fields.getConstant("TOPLEVEL_BINDING").getValue();
        BindingNodes.insertAncestorFrame(getLanguage(), toplevelBinding, mainScriptFrame);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new SetTopLevelBindingNode();
        return copy.copyFlags(this);
    }

}
