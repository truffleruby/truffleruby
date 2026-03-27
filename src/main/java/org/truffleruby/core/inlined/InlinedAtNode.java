/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.inlined;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayIndexNodes;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedAtNode extends BinaryInlinedOperationNode {

    protected static final String METHOD = "at";

    public InlinedAtNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(language, callNodeParameters);
    }

    @Specialization(
            guards = "lookupNode.lookupProtected(frame, array, METHOD) == coreMethods().ARRAY_AT",
            assumptions = "assumptions",
            limit = "1")
    static Object arrayAt(VirtualFrame frame, RubyArray array, int index,
            @Cached LookupMethodOnSelfNode lookupNode,
            @Cached ArrayIndexNodes.ReadNormalizedNode readNormalizedNode,
            @Cached InlinedConditionProfile denormalized,
            @Bind Node node) {
        if (denormalized.profile(node, index < 0)) {
            index += array.size;
        }
        return readNormalizedNode.executeRead(array, index);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedAtNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
