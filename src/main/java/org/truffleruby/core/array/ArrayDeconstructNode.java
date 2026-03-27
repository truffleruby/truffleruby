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
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

// Implemented in Java because the call to #deconstruct needs to honor refinements
@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class ArrayDeconstructNode extends RubyContextSourceNode {

    abstract RubyNode getValueNode();

    @Specialization
    Object deconstruct(VirtualFrame frame, Object toMatch,
            @Cached DispatchNode respondToNode,
            @Cached BooleanCastNode booleanCastNode,
            @Cached DispatchNode deconstructNode,
            @Cached InlinedConditionProfile hasDeconstructProfile,
            @Cached InlinedBranchProfile errorProfile) {
        if (hasDeconstructProfile.profile(this, booleanCastNode.execute(this,
                respondToNode.callWithFrame(PUBLIC, frame, toMatch, "respond_to?", coreSymbols().DECONSTRUCT)))) {
            Object deconstructed = deconstructNode.callWithFrame(PUBLIC, frame, toMatch, "deconstruct");
            if (deconstructed instanceof RubyArray) {
                return deconstructed;
            } else {
                errorProfile.enter(this);
                throw new RaiseException(getContext(),
                        coreExceptions().typeError("deconstruct must return Array", this));
            }
        } else {
            return nil;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        return ArrayDeconstructNodeGen.create(getValueNode().cloneUninitialized()).copyFlags(this);
    }

}
