/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import org.prism.Nodes;
import org.truffleruby.core.array.ArrayDeconstructNodeGen;
import org.truffleruby.core.array.ArrayFindPatternNodeGen;
import org.truffleruby.core.array.ArrayIndexNodes;
import org.truffleruby.core.array.ArrayPatternLengthCheckNodeGen;
import org.truffleruby.core.array.ArraySliceNodeGen;
import org.truffleruby.core.array.ArrayStaticLiteralNode;
import org.truffleruby.core.hash.HashDeconstructKeysNodeGen;
import org.truffleruby.core.hash.HashGetOrUndefinedNodeGen;
import org.truffleruby.core.hash.HashIsEmptyNode;
import org.truffleruby.core.hash.HashPatternLengthCheckNodeGen;
import org.truffleruby.core.hash.HashSubtractKeysNodeGen;
import org.truffleruby.core.support.IsNotUndefinedNode;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.AndNodeGen;
import org.truffleruby.language.control.ExecuteAndReturnTrueNode;
import org.truffleruby.language.control.NotNodeGen;
import org.truffleruby.language.control.OrNodeGen;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.locals.ReadLocalNode;
import org.truffleruby.language.locals.WriteLocalNode;

/** Translate the pattern of pattern matching. Executing the translated node must result in true or false. Every visit
 * method here should return a node which is the condition of whether it matched. Visit methods can change & restore
 * {@code currentValueToMatch} to change which expression is being matched against. */
public final class YARPPatternMatchingTranslator extends YARPBaseTranslator {

    private final YARPTranslator yarpTranslator;

    private RubyNode currentValueToMatch;

    public YARPPatternMatchingTranslator(
            TranslatorEnvironment environment,
            YARPTranslator yarpTranslator) {
        super(environment);
        this.yarpTranslator = yarpTranslator;
    }

    public RubyNode translatePatternNode(Nodes.Node patternNode, RubyNode expressionValue) {
        RubyNode prev = currentValueToMatch;
        currentValueToMatch = expressionValue;
        try {
            return patternNode.accept(this);
        } finally {
            currentValueToMatch = prev;
        }
    }

    @Override
    public RubyNode visitIfNode(Nodes.IfNode node) { // a guard like `in [a] if a.even?`
        assert node.statements.body.length == 1;
        var pattern = node.statements.body[0].accept(this);
        var condition = node.predicate.accept(yarpTranslator); // translate after the pattern which might introduce new variables
        return AndNodeGen.create(pattern, condition);
    }

    @Override
    public RubyNode visitUnlessNode(Nodes.UnlessNode node) { // a guard like `in [a] unless a.even?`
        assert node.statements.body.length == 1;
        var pattern = node.statements.body[0].accept(this);
        var condition = NotNodeGen.create(node.predicate.accept(yarpTranslator)); // translate after the pattern which might introduce new variables
        return AndNodeGen.create(pattern, condition);
    }

    @Override
    public RubyNode visitAlternationPatternNode(Nodes.AlternationPatternNode node) {
        var orNode = OrNodeGen.create(node.left.accept(this), node.right.accept(this));
        return assignPositionAndFlags(node, orNode);
    }

    @Override
    public RubyNode visitCapturePatternNode(Nodes.CapturePatternNode node) {
        RubyNode condition = AndNodeGen.create(
                node.value.accept(this),
                new ExecuteAndReturnTrueNode(node.target.accept(this)));
        return assignPositionAndFlags(node, condition);
    }

    @Override
    public RubyNode visitArrayPatternNode(Nodes.ArrayPatternNode node) {
        RubyNode condition;
        if (node.constant != null) { // Constant[a]
            condition = matchValue(node.constant);
        } else {
            condition = null;
        }

        var preNodes = node.requireds;
        var restNode = node.rest;
        var postNodes = node.posts;

        int preSize = preNodes.length;
        int postSize = postNodes.length;

        var deconstructed = ArrayDeconstructNodeGen.create(currentValueToMatch);

        final int deconstructedSlot = environment.declareLocalTemp("pattern_deconstruct_array");
        final ReadLocalNode readTemp = environment.readNode(deconstructedSlot, node);
        final RubyNode assignTemp = readTemp.makeWriteNode(deconstructed);

        RubyNode outerPrev = currentValueToMatch;
        currentValueToMatch = readTemp;
        try {
            RubyNode check = YARPTranslator.sequence(
                    assignTemp,
                    ArrayPatternLengthCheckNodeGen.create(preSize + postSize, restNode != null, readTemp));
            if (condition == null) {
                condition = check;
            } else {
                condition = AndNodeGen.create(condition, check);
            }

            for (int i = 0; i < preNodes.length; i++) {
                var preNode = preNodes[i];

                RubyNode prev = currentValueToMatch;
                currentValueToMatch = ArrayIndexNodes.ReadConstantIndexNode.create(readTemp, i);
                try {
                    condition = AndNodeGen.create(condition, preNode.accept(this));
                } finally {
                    currentValueToMatch = prev;
                }
            }

            if (restNode != null) {
                if (restNode instanceof Nodes.SplatNode splatNode) {
                    if (splatNode.expression != null) {
                        RubyNode prev = currentValueToMatch;
                        currentValueToMatch = ArraySliceNodeGen.create(preSize, -postSize, readTemp);
                        try {
                            condition = AndNodeGen.create(condition, splatNode.expression.accept(this));
                        } finally {
                            currentValueToMatch = prev;
                        }
                    } else { // in [1, *, 2]
                        // Nothing
                    }
                } else if (restNode instanceof Nodes.ImplicitRestNode) { // in [0, 1,]
                    // Nothing
                } else {
                    throw fail(node);
                }
            }

            for (int i = 0; i < postNodes.length; i++) {
                var postNode = postNodes[i];
                int index = -postNodes.length + i;
                RubyNode prev = currentValueToMatch;
                currentValueToMatch = ArrayIndexNodes.ReadConstantIndexNode.create(readTemp, index);
                try {
                    condition = AndNodeGen.create(condition, postNode.accept(this));
                } finally {
                    currentValueToMatch = prev;
                }
            }

            return assignPositionAndFlags(node, condition);
        } finally {
            currentValueToMatch = outerPrev;
        }
    }

    // See the Ruby logic in https://github.com/ruby/ruby/commit/ddded1157a90d21cb54b9f07de35ab9b4cc472e1
    @Override
    public RubyNode visitFindPatternNode(Nodes.FindPatternNode node) {
        RubyNode condition;
        if (node.constant != null) { // Constant[*, a, *]
            condition = matchValue(node.constant);
        } else {
            condition = null;
        }

        var middle = node.requireds;
        var middleSize = middle.length;

        var deconstructed = ArrayDeconstructNodeGen.create(currentValueToMatch);

        final int deconstructedSlot = environment.declareLocalTemp("pattern_deconstruct_find");
        final ReadLocalNode readTemp = environment.readNode(deconstructedSlot, node);
        final RubyNode assignTemp = readTemp.makeWriteNode(deconstructed);

        RubyNode outerPrev = currentValueToMatch;
        currentValueToMatch = readTemp;
        try {
            RubyNode check = YARPTranslator.sequence(
                    assignTemp,
                    ArrayPatternLengthCheckNodeGen.create(middleSize, true, readTemp));
            if (condition == null) {
                condition = check;
            } else {
                condition = AndNodeGen.create(condition, check);
            }

            var writeSlots = new WriteLocalNode[middleSize];
            var conditions = new RubyNode[middleSize];
            for (int i = 0; i < middleSize; i++) {
                int slot = environment.declareLocalTemp("pattern_find_middle");
                var readSlot = environment.readNode(slot, node);
                writeSlots[i] = readSlot.makeWriteNode(null);

                RubyNode prev = currentValueToMatch;
                currentValueToMatch = readSlot;
                try {
                    conditions[i] = middle[i].accept(this);
                } finally {
                    currentValueToMatch = prev;
                }
            }

            Nodes.SplatNode leftSplat = node.left;
            int leftSlot = environment.declareLocalTemp("pattern_find_left");
            var readLeftSlot = environment.readNode(leftSlot, node);
            var writeLeftSlot = readLeftSlot.makeWriteNode(null);
            RubyNode leftCondition;

            RubyNode prev = currentValueToMatch;
            currentValueToMatch = readLeftSlot;
            try {
                leftCondition = translateNodeOrTrue(leftSplat.expression);
            } finally {
                currentValueToMatch = prev;
            }

            Nodes.SplatNode rightSplat = node.right;
            int rightSlot = environment.declareLocalTemp("pattern_find_right");
            var readRightSlot = environment.readNode(rightSlot, node);
            var writeRightSlot = readRightSlot.makeWriteNode(null);
            RubyNode rightCondition;

            prev = currentValueToMatch;
            currentValueToMatch = readRightSlot;
            try {
                rightCondition = translateNodeOrTrue(rightSplat.expression);
            } finally {
                currentValueToMatch = prev;
            }

            var findPatternNode = ArrayFindPatternNodeGen.create(writeSlots, conditions, writeLeftSlot, leftCondition,
                    writeRightSlot, rightCondition, currentValueToMatch);
            condition = AndNodeGen.create(condition, findPatternNode);
        } finally {
            currentValueToMatch = outerPrev;
        }

        return assignPositionAndFlags(node, condition);
    }

    @Override
    public RubyNode visitHashPatternNode(Nodes.HashPatternNode node) {
        RubyNode condition;
        if (node.constant != null) { // Constant(a: 0)
            condition = matchValue(node.constant);
        } else {
            condition = null;
        }

        Nodes.Node[] pairs = node.elements;
        RubySymbol[] keys = new RubySymbol[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            Nodes.AssocNode assocNode = (Nodes.AssocNode) pairs[i];
            Nodes.Node keyNode = assocNode.key;

            final RubySymbol key;
            if (keyNode instanceof Nodes.SymbolNode symbolNode) {
                key = yarpTranslator.translateSymbol(symbolNode);
            } else {
                throw fail(node);
            }
            keys[i] = key;
        }

        final RubyNode keysForDeconstructKeys;
        if (node.rest == null || node.rest instanceof Nodes.AssocSplatNode splatNode && splatNode.value == null) {
            keysForDeconstructKeys = new ArrayStaticLiteralNode(keys);
        } else {
            keysForDeconstructKeys = new NilLiteralNode();
        }

        var deconstructed = HashDeconstructKeysNodeGen.create(currentValueToMatch, keysForDeconstructKeys);

        final int deconstructedSlot = environment.declareLocalTemp("pattern_deconstruct_hash");
        final ReadLocalNode readTemp = environment.readNode(deconstructedSlot, node);
        final RubyNode assignTemp = readTemp.makeWriteNode(deconstructed);

        RubyNode outerPrev = currentValueToMatch;
        currentValueToMatch = readTemp;
        try {
            RubyNode check = YARPTranslator.sequence(
                    assignTemp,
                    HashPatternLengthCheckNodeGen.create(node.elements.length, readTemp));
            if (condition == null) {
                condition = check;
            } else {
                condition = AndNodeGen.create(condition, check);
            }

            for (int i = 0; i < pairs.length; i++) {
                Nodes.AssocNode assocNode = (Nodes.AssocNode) pairs[i];
                RubySymbol key = keys[i];

                RubyNode valueOrUndefined = HashGetOrUndefinedNodeGen.create(key, readTemp);

                final int valueSlot = environment.declareLocalTemp("pattern_hash_value");
                final ReadLocalNode readValue = environment.readNode(valueSlot, assocNode);
                final RubyNode writeValue = readValue.makeWriteNode(valueOrUndefined);

                RubyNode prev = currentValueToMatch;
                currentValueToMatch = readValue;
                try {
                    RubyNode valueCondition = YARPTranslator.sequence(assocNode,
                            writeValue,
                            AndNodeGen.create(new IsNotUndefinedNode(readValue), assocNode.value.accept(this)));
                    condition = AndNodeGen.create(condition, valueCondition);
                } finally {
                    currentValueToMatch = prev;
                }
            }

            var rest = node.rest;
            if (rest != null) {
                RubyNode withoutMatchedKeys = keys.length == 0
                        ? readTemp
                        : HashSubtractKeysNodeGen.create(keys, readTemp);
                if (rest instanceof Nodes.AssocSplatNode assocSplatNode) {
                    if (assocSplatNode.value != null) {
                        RubyNode prev = currentValueToMatch;
                        currentValueToMatch = withoutMatchedKeys;
                        try {
                            condition = AndNodeGen.create(condition, assocSplatNode.value.accept(this));
                        } finally {
                            currentValueToMatch = prev;
                        }
                    } else {
                        // nothing
                    }
                } else if (rest instanceof Nodes.NoKeywordsParameterNode) {
                    condition = AndNodeGen.create(condition, new HashIsEmptyNode(withoutMatchedKeys));
                } else {
                    throw fail(rest);
                }
            } else if (pairs.length == 0) {
                // rest == null && pairs.length == 0 means `in {}` which checks if empty
                condition = AndNodeGen.create(condition, new HashIsEmptyNode(readTemp));
            }

            return assignPositionAndFlags(node, condition);
        } finally {
            currentValueToMatch = outerPrev;
        }
    }

    @Override
    public RubyNode visitImplicitNode(Nodes.ImplicitNode node) {
        return node.value.accept(this);
    }

    @Override
    public RubyNode visitLocalVariableTargetNode(Nodes.LocalVariableTargetNode node) {
        WriteLocalNode writeLocalNode = yarpTranslator.visitLocalVariableTargetNode(node);
        writeLocalNode.setValueNode(currentValueToMatch);
        return new ExecuteAndReturnTrueNode(writeLocalNode);
    }

    @Override
    public RubyNode visitPinnedVariableNode(Nodes.PinnedVariableNode node) {
        return matchValue(node.variable);
    }

    @Override
    public RubyNode visitPinnedExpressionNode(Nodes.PinnedExpressionNode node) {
        return matchValue(node.expression);
    }

    @Override
    protected RubyNode defaultVisit(Nodes.Node node) {
        return matchValue(node);
    }

    private RubyNode matchValue(Nodes.Node value) {
        RubyNode translatedValue = value.accept(yarpTranslator);
        var rubyNode = createCallNode(translatedValue, "===", currentValueToMatch);
        return assignPositionAndFlags(value, rubyNode);
    }

}
