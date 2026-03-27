/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import org.ruby_lang.prism.Nodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.locals.LocalVariableType;
import org.truffleruby.language.locals.ReadLocalVariableNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.objects.SelfNode;

import static org.truffleruby.parser.TranslatorEnvironment.CURRENT_FRAME_DEPTH;

/** Similar to ValueFromNode class but for YARP nodes */
public final class YARPExecutedOnceExpression {

    final String name;
    final int slot;
    final Nodes.Node node;
    final RubyNode valueNode;
    final YARPTranslator yarpTranslator;

    public YARPExecutedOnceExpression(String baseName, Nodes.Node node, YARPTranslator yarpTranslator) {
        this.node = node;
        this.yarpTranslator = yarpTranslator;

        if (node instanceof Nodes.SelfNode) {
            // `self` could be a method call receiver.
            // Keep `self` as-is so that the check for visibility when translating the call node works
            name = null;
            slot = -1;
            valueNode = null;
        } else if (node instanceof Nodes.SplatNode splatNode) {
            // Method call argument could be *a. So just storing its result in a local variable
            // and passing it later as a call argument will lose splatting. So handle it independently.
            TranslatorEnvironment environment = yarpTranslator.getEnvironment();
            name = environment.getParseEnvironment().allocateLocalTemp(baseName);
            slot = environment.declareVar(name);
            valueNode = splatNode.expression.accept(yarpTranslator);
        } else {
            TranslatorEnvironment environment = yarpTranslator.getEnvironment();
            name = environment.getParseEnvironment().allocateLocalTemp(baseName);
            slot = environment.declareVar(name);
            valueNode = node.accept(yarpTranslator);
        }
    }

    public Nodes.Node getReadYARPNode() {
        if (node instanceof Nodes.SelfNode) {
            return node;
        } else if (node instanceof Nodes.SplatNode) {
            var read = new Nodes.LocalVariableReadNode(0, 0, name, CURRENT_FRAME_DEPTH);
            return new Nodes.SplatNode(node.startOffset, node.length, read);
        } else {
            return new Nodes.LocalVariableReadNode(node.startOffset, node.length, name, CURRENT_FRAME_DEPTH);
        }
    }

    public RubyNode getReadNode() {
        if (node instanceof Nodes.SelfNode) {
            return new SelfNode();
        } else if (node instanceof Nodes.SplatNode) {
            return getReadYARPNode().accept(yarpTranslator);
        } else {
            return new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, slot);
        }
    }

    public RubyNode getWriteNode() {
        if (node instanceof Nodes.SelfNode) {
            return null;
        } else {
            return new WriteLocalVariableNode(slot, valueNode);
        }
    }
}
