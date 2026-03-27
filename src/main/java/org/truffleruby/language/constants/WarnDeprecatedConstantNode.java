/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.WarnNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

public final class WarnDeprecatedConstantNode extends RubyBaseNode {

    @Child private WarnNode warnNode;

    public void warnDeprecatedConstant(RubyModule module, String name) {
        if (warnNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warnNode = insert(new WarnNode());
        }

        if (warnNode.shouldWarnForDeprecation()) {
            warnNode.warningMessage(getSection(this), formatMessage(this, module, name));
        }
    }

    public static void warnDeprecatedConstant(Node node, WarnNode warnNode, RubyModule module, String name) {
        if (warnNode.shouldWarnForDeprecation()) {
            warnNode.warningMessage(getSection(node), formatMessage(node, module, name));
        }
    }

    @TruffleBoundary
    private static SourceSection getSection(Node node) {
        return getContext(node).getCallStack()
                .getTopMostUserSourceSection(getAdoptedNode(node).getEncapsulatingSourceSection());
    }

    @TruffleBoundary
    private static String formatMessage(Node node, RubyModule module, String name) {
        String fullyQualifiedName = ModuleOperations.constantName(getContext(node), module, name);
        return "constant " + fullyQualifiedName + " is deprecated";
    }

}
