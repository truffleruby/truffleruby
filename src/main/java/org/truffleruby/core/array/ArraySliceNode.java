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
package org.truffleruby.core.array;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "arrayNode", type = RubyNode.class)
@ImportStatic(ArrayGuards.class)
public abstract class ArraySliceNode extends RubyContextSourceNode {

    final int from; // positive
    final int to; // negative, exclusive

    public ArraySliceNode(int from, int to) {
        assert from >= 0;
        assert to <= 0;
        this.from = from;
        this.to = to;
    }

    public abstract RubyArray execute(RubyArray array);

    public abstract RubyNode getArrayNode();

    @Specialization
    RubyArray readSlice(RubyArray array,
            @Cached ArrayCopyOnWriteNode cowNode,
            @Cached ConditionProfile emptyArray) {
        final int length = array.size + to - from;

        if (emptyArray.profile(length <= 0)) {
            return createEmptyArray();
        } else {
            final Object slice = cowNode.execute(array, from, length);
            return createArray(slice, length);
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ArraySliceNodeGen.create(
                from,
                to,
                getArrayNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
