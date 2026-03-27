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
package org.truffleruby.core.array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayIndexNodes.ReadNormalizedNode;
import org.truffleruby.core.cast.SplatCastNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceAssignableNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.profiles.ConditionProfile;

public final class MultipleAssignmentNode extends RubyContextSourceAssignableNode {

    /** Nodes that should be executed before assignment */
    @Children final RubyNode[] prolog;

    @Child RubyNode rhsNode;
    @Child SplatCastNode splatCastNode;

    @Children final AssignableNode[] preNodes;
    @Child AssignableNode restNode;
    @Children final AssignableNode[] postNodes;

    @Child ReadNormalizedNode arrayReadNormalizedNode;
    @Child ArraySliceNode arraySliceNode;

    private final ConditionProfile enoughElementsForAllPost;

    public MultipleAssignmentNode(
            RubyNode[] prolog,
            AssignableNode[] preNodes,
            AssignableNode restNode,
            AssignableNode[] postNodes,
            SplatCastNode splatCastNode,
            RubyNode rhsNode) {
        this.prolog = prolog;
        this.preNodes = preNodes;
        this.restNode = restNode;
        this.postNodes = postNodes;
        this.splatCastNode = splatCastNode;
        this.rhsNode = rhsNode;
        this.enoughElementsForAllPost = postNodes.length == 0 ? null : ConditionProfile.create();
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        if (prolog != null) {
            for (var node : prolog) {
                node.execute(frame);
            }
        }

        final Object rhs = rhsNode.execute(frame);
        assign(frame, rhs);
        return rhs;
    }

    @ExplodeLoop
    @Override
    public void assign(VirtualFrame frame, Object rhs) {
        final RubyArray array = (RubyArray) splatCastNode.execute(rhs);

        for (int i = 0; i < preNodes.length; i++) {
            preNodes[i].assign(frame, read(array, i));
        }

        if (restNode != null) {
            restNode.assign(frame, readSlice(array));
        }

        if (postNodes.length > 0) {
            final int size = array.size;
            for (int i = 0; i < postNodes.length; i++) {
                // a, b, *, c, d = ary
                final int index;
                if (enoughElementsForAllPost.profile(size >= preNodes.length + postNodes.length)) {
                    index = size - postNodes.length + i; // post take size-2, size-1 (size >= 4)
                } else {
                    index = preNodes.length + i; // post take pre, pre+1, some of them (the tail) will read nil
                }
                postNodes[i].assign(frame, read(array, index));
            }
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    private Object read(RubyArray array, int i) {
        if (arrayReadNormalizedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            arrayReadNormalizedNode = insert(ReadNormalizedNode.create());
        }

        return arrayReadNormalizedNode.executeRead(array, i);
    }

    private Object readSlice(RubyArray array) {
        if (arraySliceNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            arraySliceNode = insert(ArraySliceNodeGen.create(preNodes.length, -postNodes.length, null));
        }

        return arraySliceNode.execute(array);
    }

    @Override
    public AssignableNode toAssignableNode() {
        this.rhsNode = null;
        return this;
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new MultipleAssignmentNode(
                RubyNode.cloneUninitialized(prolog),
                cloneUninitializedAssignable(preNodes),
                cloneUninitializedAssignable(restNode),
                cloneUninitializedAssignable(postNodes),
                (SplatCastNode) splatCastNode.cloneUninitialized(),
                RubyNode.cloneUninitialized(rhsNode));
        return copy.copyFlags(this);
    }

    protected AssignableNode[] cloneUninitializedAssignable(AssignableNode[] nodes) {
        if (nodes.length == 0) {
            return AssignableNode.EMPTY_ARRAY;
        }

        AssignableNode[] copies = new AssignableNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copies[i] = nodes[i].cloneUninitializedAssignable();
        }
        return copies;
    }

    protected AssignableNode cloneUninitializedAssignable(AssignableNode node) {
        return (node == null) ? null : node.cloneUninitializedAssignable();
    }

}
