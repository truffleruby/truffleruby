/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
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
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

public abstract class InlinedByteSizeNode extends UnaryInlinedOperationNode {

    protected static final String METHOD = "bytesize";

    public InlinedByteSizeNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(language, callNodeParameters);
    }

    @Specialization(
            guards = { "lookupNode.lookupProtected(frame, self, METHOD) == coreMethods().STRING_BYTESIZE", },
            assumptions = "assumptions")
    int byteSize(VirtualFrame frame, RubyString self,
            @Cached @Shared LookupMethodOnSelfNode lookupNode,
            @Cached @Exclusive RubyStringLibrary libString,
            @Bind Node node) {
        return libString.byteLength(node, self);
    }

    @Specialization(
            guards = { "lookupNode.lookupProtected(frame, self, METHOD) == coreMethods().STRING_BYTESIZE", },
            assumptions = "assumptions")
    int byteSizeImmutable(VirtualFrame frame, ImmutableRubyString self,
            @Cached @Shared LookupMethodOnSelfNode lookupNode,
            @Cached @Exclusive RubyStringLibrary libString,
            @Bind Node node) {
        return libString.byteLength(node, self);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object self) {
        return rewriteAndCall(frame, self);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedByteSizeNodeGen.create(
                getLanguage(),
                this.parameters,
                getSelfNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
