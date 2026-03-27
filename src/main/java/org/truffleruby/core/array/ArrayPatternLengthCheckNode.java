/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class ArrayPatternLengthCheckNode extends RubyContextSourceNode {

    final int patternLength;
    final boolean hasRest;

    public ArrayPatternLengthCheckNode(int patternLength, boolean hasRest) {
        this.patternLength = patternLength;
        this.hasRest = hasRest;
    }

    abstract RubyNode getValueNode();

    @Specialization
    boolean arrayLengthCheck(RubyArray matchArray) {
        int size = matchArray.size;
        if (hasRest) {
            return patternLength <= size;
        } else {
            return patternLength == size;
        }
    }

    @Fallback
    boolean notArray(Object value) {
        return false;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return ArrayPatternLengthCheckNodeGen.create(patternLength, hasRest, getValueNode().cloneUninitialized())
                .copyFlags(this);
    }
}
