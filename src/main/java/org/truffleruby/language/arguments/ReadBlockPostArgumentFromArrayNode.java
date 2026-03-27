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
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.core.array.ArrayIndexNodes;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

/** Read block's required post parameter (that is located after optional/rest parameters) from a single Array argument
 * that is destructured. Based on org.truffleruby.language.arguments.ReadPostArgumentNode. */
public final class ReadBlockPostArgumentFromArrayNode extends RubyContextSourceNode {

    @Child private RubyNode readArrayNode;
    @Child private ArrayIndexNodes.ReadNormalizedNode arrayReadNormalizedNode;
    /** parameter index from the end */
    private final int indexFromCount;
    /** number of block required parameters (pre and post) */
    private final int required;
    private final int optional;
    private final boolean hasRest;
    private final ConditionProfile enoughArguments = ConditionProfile.create();

    public ReadBlockPostArgumentFromArrayNode(
            RubyNode readArrayNode,
            int indexFromCount,
            int required,
            int optional,
            boolean hasRest) {
        this.readArrayNode = readArrayNode;
        this.indexFromCount = indexFromCount;
        this.required = required;
        this.optional = optional;
        this.hasRest = hasRest;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RubyArray array = (RubyArray) readArrayNode.execute(frame);
        final int length = array.size;

        if (enoughArguments.profile(length >= required)) {
            final int effectiveIndex;

            if (hasRest || length <= optional + required) {
                effectiveIndex = length - indexFromCount;
            } else {
                effectiveIndex = optional + required - indexFromCount;
            }

            return getReadNormalizedNode().executeRead(array, effectiveIndex);
        } else {
            final int effectiveIndex = required - indexFromCount;

            if (effectiveIndex < length) {
                return getReadNormalizedNode().executeRead(array, effectiveIndex);
            } else {
                return nil;
            }
        }
    }

    private ArrayIndexNodes.ReadNormalizedNode getReadNormalizedNode() {
        if (arrayReadNormalizedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            arrayReadNormalizedNode = insert(ArrayIndexNodes.ReadNormalizedNode.create());
        }

        return arrayReadNormalizedNode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " -" + indexFromCount;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadBlockPostArgumentFromArrayNode(readArrayNode.cloneUninitialized(), indexFromCount, required,
                optional, hasRest);
        return copy.copyFlags(this);
    }
}
