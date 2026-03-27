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

import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

// Implemented in Java because the call to #deconstruct_keys needs to honor refinements
@NodeChild(value = "valueNode", type = RubyNode.class)
@NodeChild(value = "keysNode", type = RubyNode.class)
public abstract class HashDeconstructKeysNode extends RubyContextSourceNode {

    abstract RubyNode getValueNode();

    abstract RubyNode getKeysNode();

    @Specialization
    Object deconstructKeys(VirtualFrame frame, Object toMatch, Object keys,
            @Cached DispatchNode respondToNode,
            @Cached BooleanCastNode booleanCastNode,
            @Cached DispatchNode deconstructKeysNode,
            @Cached InlinedConditionProfile hasDeconstructKeysProfile,
            @Cached InlinedBranchProfile errorProfile) {
        if (hasDeconstructKeysProfile.profile(this, booleanCastNode.execute(this,
                respondToNode.callWithFrame(PUBLIC, frame, toMatch, "respond_to?", coreSymbols().DECONSTRUCT_KEYS)))) {
            Object deconstructed = deconstructKeysNode.callWithFrame(PUBLIC, frame, toMatch, "deconstruct_keys", keys);
            if (deconstructed instanceof RubyHash) {
                return deconstructed;
            } else {
                errorProfile.enter(this);
                throw new RaiseException(getContext(),
                        coreExceptions().typeError("deconstruct_keys must return Hash", this));
            }
        } else {
            return nil;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        return HashDeconstructKeysNodeGen
                .create(getValueNode().cloneUninitialized(), getKeysNode().cloneUninitialized()).copyFlags(this);
    }

}
