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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.cast.LongCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.DispatchNode;

@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class HashPatternLengthCheckNode extends RubyContextSourceNode {

    private final int minimumKeys;

    public HashPatternLengthCheckNode(int minimumKeys) {
        this.minimumKeys = minimumKeys;
    }

    int getMinimumKeys() {
        return minimumKeys;
    }

    abstract RubyNode getValueNode();

    @Specialization(guards = "isBuiltinHash(matchHash)")
    boolean hashLengthCheck(RubyHash matchHash) {
        return minimumKeys <= matchHash.size;
    }

    @Specialization(guards = "!isBuiltinHash(matchHash)")
    static boolean hashLengthCheckOnSubclass(VirtualFrame frame, RubyHash matchHash,
            @Bind Node node,
            @Bind("getMinimumKeys()") int minimumKeys,
            @Cached DispatchNode sizeNode,
            @Cached LongCastNode toLongNode) {
        Object size = sizeNode.callWithFrame(PUBLIC, frame, matchHash, "size");
        return minimumKeys <= toLongNode.executeCastLong(node, size);
    }

    @Fallback
    boolean notHash(Object value) {
        return false;
    }

    protected boolean isBuiltinHash(RubyHash hash) {
        return hash.getMetaClass() == coreLibrary().hashClass;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return HashPatternLengthCheckNodeGen.create(minimumKeys, getValueNode().cloneUninitialized()).copyFlags(this);
    }
}
