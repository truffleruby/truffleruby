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
package org.truffleruby.core.numeric;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.core.numeric.FixnumLowerNodeGen.FixnumLowerASTNodeGen;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyNode;

/** Passes through {@code int} values unmodified, but will convert a {@code long} value to an {@code int}, if it fits
 * within the range of an {@code int}. Leaves all other values unmodified. Used where a specialization only accepts
 * {@code int}, such as Java array indexing, but we would like to also handle {@code long} if they also fit within an
 * {@code int}.
 *
 * <p>
 * See {@link org.truffleruby.core.cast.ToIntNode} for a comparison of different integer conversion nodes. */
@GenerateCached(false)
@GenerateInline
public abstract class FixnumLowerNode extends RubyBaseNode {

    public abstract Object execute(Node node, Object value);

    @Specialization
    static int lower(int value) {
        return value;
    }

    @Specialization(guards = "fitsInInteger(value)")
    static int lower(long value) {
        return (int) value;
    }

    @Specialization(guards = "!fitsInInteger(value)")
    static long lowerFails(long value) {
        return value;
    }

    @Specialization(guards = "!isImplicitLong(value)")
    static Object passThrough(Object value) {
        return value;
    }

    @NodeChild(value = "valueNode", type = RubyBaseNodeWithExecute.class)
    public abstract static class FixnumLowerASTNode extends RubyContextSourceNode {

        protected abstract RubyBaseNodeWithExecute getValueNode();

        @Specialization
        Object doFixnumLower(Object value,
                @Cached FixnumLowerNode fixnumLowerNode) {
            return fixnumLowerNode.execute(this, value);

        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = FixnumLowerASTNodeGen.create(getValueNode().cloneUninitialized());
            return copy.copyFlags(this);
        }
    }

}

