/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.read.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadHashValueNode extends FormatNode {

    private final RubySymbol key;

    public ReadHashValueNode(RubySymbol key) {
        this.key = key;
    }

    @Specialization
    Object read(Object[] source,
            @Cached DispatchNode lookupNode,
            @Cached InlinedBranchProfile notHashProfile) {
        if (source.length != 1 || !RubyGuards.isRubyHash(source[0])) {
            notHashProfile.enter(this);
            throw new RaiseException(getContext(), getContext().getCoreExceptions().argumentErrorOneHashRequired(this));
        }

        final RubyHash hash = (RubyHash) source[0];

        if (lookupNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupNode = insert(DispatchNode.create());
        }

        return lookupNode.call(coreLibrary().truffleHashOperationsModule, "lookup_raise_if_nil_default", hash, key);
    }

}
