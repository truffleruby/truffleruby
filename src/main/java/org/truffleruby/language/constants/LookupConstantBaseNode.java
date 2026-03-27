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
package org.truffleruby.language.constants;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.WarnNode;

import com.oracle.truffle.api.CompilerDirectives;

public abstract class LookupConstantBaseNode extends RubyBaseNode {

    @Child private WarnDeprecatedConstantNode warnDeprecatedConstantNode;

    protected void warnDeprecatedConstant(RubyModule module, String name) {
        if (warnDeprecatedConstantNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warnDeprecatedConstantNode = insert(new WarnDeprecatedConstantNode());
        }

        warnDeprecatedConstantNode.warnDeprecatedConstant(module, name);
    }

    protected static void warnDeprecatedConstant(Node node, WarnNode warnNode, RubyModule module, String name) {
        WarnDeprecatedConstantNode.warnDeprecatedConstant(node, warnNode, module, name);
    }

    protected int getCacheLimit() {
        return getLanguage().options.CONSTANT_CACHE;
    }

}
