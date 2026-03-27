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
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.core.array.ArrayIndexNodes;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

public final class ReadBlockOptionalArgumentFromArrayNode extends RubyContextSourceNode {

    @Child private RubyNode readArrayNode;
    @Child private RubyNode defaultValue;
    @Child private ArrayIndexNodes.ReadNormalizedNode arrayReadNormalizedNode;
    private final int index;
    private final int minimum;
    private final BranchProfile defaultValueProfile = BranchProfile.create();

    public ReadBlockOptionalArgumentFromArrayNode(
            RubyNode readArrayNode,
            int index,
            int minimum,
            RubyNode defaultValue) {
        this.readArrayNode = readArrayNode;
        this.index = index;
        this.minimum = minimum;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RubyArray array = (RubyArray) readArrayNode.execute(frame);
        final int length = array.size;

        if (length >= minimum) {
            if (arrayReadNormalizedNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                arrayReadNormalizedNode = insert(ArrayIndexNodes.ReadNormalizedNode.create());
            }

            return arrayReadNormalizedNode.executeRead(array, index);
        } else {
            defaultValueProfile.enter();
            return defaultValue.execute(frame);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + index;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadBlockOptionalArgumentFromArrayNode(
                readArrayNode.cloneUninitialized(),
                index,
                minimum,
                defaultValue.cloneUninitialized());
        return copy.copyFlags(this);
    }
}
