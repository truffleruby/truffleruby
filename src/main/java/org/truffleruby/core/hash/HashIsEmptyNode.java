/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;

@NodeChild(value = "hashNode", type = RubyNode.class)
public abstract class HashIsEmptyNode extends RubyContextSourceNode {

    abstract RubyNode getHashNode();

    @Specialization(guards = "isBuiltinHash(hash)")
    boolean empty(RubyHash hash) {
        return hash.empty();
    }

    @Specialization(guards = "!isBuiltinHash(hash)")
    static boolean emptyOnSubclass(VirtualFrame frame, RubyHash hash,
            @Bind Node node,
            @Cached DispatchNode emptyNode,
            @Cached BooleanCastNode booleanCastNode) {
        return booleanCastNode.execute(node, emptyNode.callWithFrame(PUBLIC, frame, hash, "empty?"));
    }

    protected boolean isBuiltinHash(RubyHash hash) {
        return hash.getMetaClass() == coreLibrary().hashClass;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return HashIsEmptyNodeGen.create(getHashNode().cloneUninitialized()).copyFlags(this);
    }
}
