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

import org.truffleruby.core.array.ArrayBuilderNode.BuilderState;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

/** Concatenate argument arrays (translating a org.jruby.ast.ArgsCatParseNode). */
public final class ArrayConcatNode extends RubyContextSourceNodeCustomExecuteVoid {

    @Children private final RubyNode[] children;
    // Use an arrayBuilderNode to stabilize the array type for a given location.
    @Child private ArrayBuilderNode arrayBuilderNode;

    private final ConditionProfile isArrayProfile = ConditionProfile.create();

    public ArrayConcatNode(RubyNode[] children) {
        assert children.length > 1;
        this.children = children;
    }

    // getChildren method name is already used
    public RubyNode[] getElements() {
        return children;
    }

    @ExplodeLoop
    @Override
    public RubyArray execute(VirtualFrame frame) {
        if (arrayBuilderNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            arrayBuilderNode = insert(ArrayBuilderNode.create());
        }

        // Compute the total length
        int totalLength = 0;
        final Object[] values = new Object[children.length];
        for (int n = 0; n < children.length; n++) {
            final Object value = values[n] = children[n].execute(frame);

            if (isArrayProfile.profile(value instanceof RubyArray)) {
                totalLength += ((RubyArray) value).size;
            } else {
                totalLength++;
            }
        }

        // Create a builder with the right length and append values
        BuilderState state = arrayBuilderNode.start(totalLength);
        int index = 0;

        for (int n = 0; n < children.length; n++) {
            final Object value = values[n];

            if (isArrayProfile.profile(value instanceof RubyArray)) {
                final RubyArray childArray = (RubyArray) value;
                arrayBuilderNode.appendArray(state, index, childArray);
                index += childArray.size;
            } else {
                arrayBuilderNode.appendValue(state, index, value);
                index++;
            }
        }

        return createArray(arrayBuilderNode.finish(state, index), index);
    }

    @ExplodeLoop
    @Override
    public Nil executeVoid(VirtualFrame frame) {
        for (int n = 0; n < children.length; n++) {
            children[n].executeVoid(frame);
        }
        return nil;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ArrayConcatNode(cloneUninitialized(children));
        return copy.copyFlags(this);
    }

}
