/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

public final class InsideModuleDefinitionNode extends RubyContextSourceNode {

    @Child private RubyNode body;

    public InsideModuleDefinitionNode(RubyNode body) {
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == TraceManager.ClassTag.class) {
            return true;
        }
        return super.hasTag(tag);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new InsideModuleDefinitionNode(body.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
