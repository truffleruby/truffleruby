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
package org.truffleruby.core.format;

import java.util.Deque;
import java.util.List;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.format.control.RepeatExplodedNode;
import org.truffleruby.core.format.control.RepeatLoopNode;
import org.truffleruby.core.format.control.SequenceNode;
import org.truffleruby.core.format.control.StarNode;
import org.truffleruby.core.format.pack.SimplePackParser;

public final class SharedTreeBuilder {

    private final RubyLanguage language;

    public SharedTreeBuilder(RubyLanguage language) {
        this.language = language;
    }

    public FormatNode finishSubSequence(Deque<List<FormatNode>> sequenceStack, int count) {
        final List<FormatNode> sequence = sequenceStack.pop();
        final FormatNode sequenceNode = createSequence(sequence.toArray(FormatNode.EMPTY_ARRAY));

        if (count == SimplePackParser.COUNT_NONE) {
            return sequenceNode;
        } else {
            return createRepeatNode(count, sequenceNode);
        }
    }

    public static FormatNode createSequence(FormatNode... children) {
        if (children.length == 1) {
            return children[0];
        } else {
            return new SequenceNode(children);
        }
    }

    public FormatNode applyCount(int count, FormatNode node) {
        switch (count) {
            case SimplePackParser.COUNT_NONE:
                return node;

            case SimplePackParser.COUNT_STAR:
                return new StarNode(node);

            default:
                return createRepeatNode(count, node);
        }
    }

    private FormatNode createRepeatNode(int count, FormatNode node) {
        if (count == 1) {
            return node;
        }

        if (count > language.options.PACK_UNROLL_LIMIT) {
            return new RepeatLoopNode(count, node);
        } else {
            return new RepeatExplodedNode(count, node);
        }
    }

    public StarLength parseCountContext(int count) {
        final boolean star;
        final int length;

        if (count == SimplePackParser.COUNT_NONE) {
            star = false;
            length = 1;
        } else if (count == SimplePackParser.COUNT_STAR) {
            star = true;
            length = 0;
        } else {
            star = false;
            length = count;
        }

        return new StarLength(star, length);
    }

    public static final class StarLength {

        private final boolean star;
        private final int length;

        public StarLength(boolean star, int length) {
            this.star = star;
            this.length = length;
        }

        public boolean isStar() {
            return star;
        }

        public int getLength() {
            return length;
        }
    }

}
