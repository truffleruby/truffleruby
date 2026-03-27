/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.cast.ToSNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.language.RubyNode;

/** A list of expressions to build up into a string. */
public final class InterpolatedStringNode extends RubyContextSourceNode {

    @Children private final ToSNode[] children;

    @Child private StringPrimitiveNodes.StringAppendPrimitiveNode appendNode;

    private final RubyEncoding encoding;
    private final TruffleString emptyTString;

    public InterpolatedStringNode(ToSNode[] children, RubyEncoding encoding) {
        assert children.length > 0;
        this.children = children;
        this.encoding = encoding;
        this.emptyTString = this.encoding.tencoding.getEmpty();
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {

        // Start with an empty string to ensure the result has class String and the proper encoding.
        RubyString builder = createString(emptyTString, encoding);

        // TODO (nirvdrum 11-Jan-16) Rewrite to avoid massively unbalanced trees.
        for (ToSNode child : children) {
            final Object toInterpolate = child.execute(frame);
            builder = executeStringAppend(builder, toInterpolate);
        }


        return builder;
    }

    private RubyString executeStringAppend(RubyString builder, Object string) {
        if (appendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendNode = insert(StringPrimitiveNodesFactory.StringAppendPrimitiveNodeFactory.create(null));
        }
        return appendNode.executeStringAppend(builder, string);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new InterpolatedStringNode(
                cloneUninitialized(children),
                encoding);
        return copy.copyFlags(this);
    }

    protected static ToSNode[] cloneUninitialized(ToSNode[] nodes) {
        ToSNode[] copies = new ToSNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copies[i] = (ToSNode) nodes[i].cloneUninitialized();
        }
        return copies;
    }

}
