/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.ReRaiseInlinedExceptionNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.SharedMethodInfo;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.annotations.Split;
import org.truffleruby.parser.BlockDescriptorInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;

public class RubyRootNode extends RubyBaseRootNode {

    public static RubyRootNode of(RootCallTarget callTarget) {
        return (RubyRootNode) callTarget.getRootNode();
    }

    private final SharedMethodInfo sharedMethodInfo;
    private Split split;
    public final ReturnID returnID;

    @Child protected RubyNode body;
    protected final RubyNode bodyCopy;

    public RubyRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID) {
        super(language, frameDescriptor, sourceSection);
        assert sourceSection != null;
        assert body != null;

        this.sharedMethodInfo = sharedMethodInfo;
        this.body = body;
        this.split = split;
        this.returnID = returnID;

        // Ensure the body node is instrument-able, which requires a non-null SourceSection
        if (!body.hasSource()) {
            body.unsafeSetSourceSection(sourceSection);
        }

        body.unsafeSetIsCall();
        body.unsafeSetIsRoot();

        if (language.options.CHECK_CLONE_UNINITIALIZED_CORRECTNESS) {
            this.bodyCopy = NodeUtil.cloneNode(body);
        } else {
            this.bodyCopy = null;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @Override
    public FrameDescriptor getParentFrameDescriptor() {
        var info = getFrameDescriptor().getInfo();
        if (info instanceof BlockDescriptorInfo) {
            return ((BlockDescriptorInfo) info).getParentDescriptor();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCloningAllowed() {
        return split != Split.NEVER;
    }

    public boolean shouldAlwaysClone() {
        assert isCloningAllowed();

        if (getLanguage().options.ALWAYS_CLONE_ALL) {
            return split != Split.NEVER;
        }

        return split == Split.ALWAYS;
    }

    public Split getSplit() {
        return split;
    }

    public void setSplit(Split split) {
        this.split = split;
    }

    @Override
    public String getName() {
        return sharedMethodInfo.getRuntimeName();
    }

    @Override
    public String toString() {
        return getName();
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }

    public NodeFactory<? extends RubyBaseNode> getAlwaysInlinedNodeFactory() {
        return ((ReRaiseInlinedExceptionNode) body).nodeFactory;
    }

    public RubyNode copyBody() {
        return NodeUtil.cloneNode(body);
    }

    @Override
    protected boolean isCloneUninitializedSupported() {
        return true;
    }

    protected RubyRootNode cloneUninitializedRootNode() {
        return new RubyRootNode(getLanguage(), getSourceSection(), getFrameDescriptor(), sharedMethodInfo,
                body.cloneUninitialized(), split, returnID);
    }

    @Override
    public final RootNode cloneUninitialized() {
        RubyRootNode clone = cloneUninitializedRootNode();

        if (getLanguage().options.CHECK_CLONE_UNINITIALIZED_CORRECTNESS) {
            ensureCloneUninitializedCorrectness(clone);
        }

        return clone;
    }

    @SuppressWarnings("serial")
    private static final class CloningError extends Error {

        public final Node original;
        public final Node clone;

        CloningError(String message, Node original, Node clone) {
            super(message);

            this.original = original;
            this.clone = clone;
        }

    }

    private void ensureCloneUninitializedCorrectness(RubyRootNode clone) {
        if (this == clone) {
            throw CompilerDirectives.shouldNotReachHere("clone same as this");
        }
        assertSame(this.getClass(), clone.getClass());
        assertSame(getSourceSection(), clone.getSourceSection());
        assertSame(sharedMethodInfo, clone.sharedMethodInfo);
        assertSame(getSplit(), clone.getSplit());
        assertSame(returnID, clone.returnID);

        IdentityHashMap<Node, Boolean> specializedNodes = new IdentityHashMap<>();
        this.body.accept((node) -> {
            specializedNodes.put(node, true);
            return true;
        });

        try {
            ensureClonedCorrectly(bodyCopy, clone.body, specializedNodes);
        } catch (CloningError e) {
            System.err.println();

            System.err.println("#cloneUninitialized for RubyRootNode " + getName() + " created not identical AST");
            System.err.println(e.getMessage());

            System.err.println();

            System.err.println("Original node:");
            NodeUtil.printCompactTree(System.err, e.original);

            System.err.println("Cloned node:");
            NodeUtil.printCompactTree(System.err, e.clone);

            System.err.println();

            System.err.println("Original root node body:");
            NodeUtil.printCompactTree(System.err, bodyCopy);

            System.err.println("Cloned root node body:");
            NodeUtil.printCompactTree(System.err, clone.body);

            throw new Error("#cloneUninitialized for RubyRootNode " + getName() + " created not identical AST");
        }
    }

    private void assertSame(Object a, Object b) {
        if (a != b) {
            throw CompilerDirectives.shouldNotReachHere("different " + a + " vs " + b + " for: " + this);
        }
    }

    private void ensureClonedCorrectly(Node original, Node clone, IdentityHashMap<Node, Boolean> specializedNodes) {
        // A clone should be a new instance
        // Actually this should never happen since the bodyCopy is separate from the initialized AST
        if (original == clone) {
            throw new CloningError("Clone is the same instance as the original node", original, clone);
        }

        // A clone should never be a node from the initialized/specialized AST
        if (specializedNodes.containsKey(clone)) {
            throw new CloningError("Clone is a node from the initialized AST", clone, clone);
        }

        // Ignore instrumental wrappers (e.g. RubyNodeWrapper)
        if (original instanceof WrapperNode wrapperNode) {
            ensureClonedCorrectly(wrapperNode.getDelegateNode(), clone, specializedNodes);
            return;
        }

        // Should be instances of the same class
        if (original.getClass() != clone.getClass()) {
            throw new CloningError("Nodes are instances of different classes", original, clone);
        }

        if (original instanceof RubyNode) {
            var a = (RubyNode) original;
            var b = (RubyNode) clone;
            if (a.getFlags() != b.getFlags()) {
                throw new CloningError("flags not copied", original, clone);
            } else if (a.getSourceCharIndex() != b.getSourceCharIndex()) {
                throw new CloningError("sourceCharIndex not copied", original, clone);
            } else if (a.getSourceLength() != b.getSourceLength()) {
                throw new CloningError("sourceLength not copied", original, clone);
            }
        }

        Node[] originalChildren = childrenToArray(original);
        Node[] cloneChildren = childrenToArray(clone);

        // Should have the same number of children
        if (cloneChildren.length != originalChildren.length) {
            throw new CloningError("Nodes have different number of children", original, clone);
        }

        // Should have the same children
        for (int i = 0; i < cloneChildren.length; i++) {
            ensureClonedCorrectly(originalChildren[i], cloneChildren[i], specializedNodes);
        }
    }

    private static final Node[] EMPTY_NODE_ARRAY = new Node[0];

    private Node[] childrenToArray(Node node) {
        var childrenList = new ArrayList<Node>();
        for (Node child : node.getChildren()) {
            childrenList.add(child);
        }

        return childrenList.toArray(EMPTY_NODE_ARRAY);
    }
}
