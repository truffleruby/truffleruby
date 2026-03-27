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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class HashPatternLengthCheckNode extends RubyContextSourceNode {

    private final int minimumKeys;

    public HashPatternLengthCheckNode(int minimumKeys) {
        this.minimumKeys = minimumKeys;
    }

    abstract RubyNode getValueNode();

    @Specialization
    boolean hashLengthCheck(RubyHash matchHash) {
        return minimumKeys <= matchHash.size;
    }

    @Fallback
    boolean notHash(Object value) {
        return false;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return HashPatternLengthCheckNodeGen.create(minimumKeys, getValueNode().cloneUninitialized()).copyFlags(this);
    }
}
