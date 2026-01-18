/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.core.cast.HashCastNodeGen.HashCastASTNodeGen;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;

/** This node is used for splatting a Hash like {@code foo(**kw)} or {@code { a: 1, **b }}. Therefore it treats nil
 * specially. */
@GenerateCached(false)
@GenerateInline
public abstract class HashCastNode extends RubyBaseNode {

    public abstract RubyHash execute(Node node, Object value);

    @Specialization
    static RubyHash castHash(RubyHash hash) {
        return hash;
    }

    @Specialization
    static RubyHash castNil(Node node, Nil nil) {
        return getContext(node).getCoreLibrary().getEmptyFrozenHash();
    }

    @Specialization(guards = { "!isRubyHash(object)", "!isNil(object)" })
    static RubyHash cast(Node node, Object object,
            @Cached InlinedBranchProfile errorProfile,
            @Cached(inline = false) DispatchNode toHashNode) {
        final Object result = toHashNode.call(PRIVATE_RETURN_MISSING, object, "to_hash");

        if (result == DispatchNode.MISSING) {
            errorProfile.enter(node);
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).typeErrorNoImplicitConversion(object, "Hash", node));
        }

        if (!RubyGuards.isRubyHash(result)) {
            errorProfile.enter(node);
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).typeErrorCantConvertTo(object, "Hash", "to_hash", result, node));
        }

        return (RubyHash) result;
    }

    /** Must be a RubyNode because it's used for ** in the translator. */
    @NodeChild(value = "childNode", type = RubyNode.class)
    public abstract static class HashCastASTNode extends RubyContextSourceNodeCustomExecuteVoid {

        protected abstract RubyNode getChildNode();

        @Specialization
        RubyHash cast(Object object,
                @Cached HashCastNode hashCastNode) {
            return hashCastNode.execute(this, object);

        }

        @Override
        public final Nil executeVoid(VirtualFrame frame) {
            return getChildNode().executeVoid(frame);
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = HashCastASTNodeGen.create(getChildNode().cloneUninitialized());
            return copy.copyFlags(this);
        }
    }
}
