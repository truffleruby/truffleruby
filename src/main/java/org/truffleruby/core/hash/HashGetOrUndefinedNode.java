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

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.hash.library.HashStoreLibrary;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

/** The same as {@link HashNodes.GetOrUndefinedNode} but with a static key. */
@ImportStatic(HashGuards.class)
@NodeChild(value = "hashNode", type = RubyNode.class)
public abstract class HashGetOrUndefinedNode extends RubyContextSourceNode implements PEBiFunction {

    private final RubySymbol key;

    public HashGetOrUndefinedNode(RubySymbol key) {
        this.key = key;
    }

    abstract RubyNode getHashNode();

    @Specialization(limit = "hashStrategyLimit()")
    Object get(RubyHash hash,
            @CachedLibrary("hash.store") HashStoreLibrary hashes) {
        return hashes.lookupOrDefault(hash.store, null, hash, key, this);
    }

    @Override
    public Object accept(Frame frame, Object hash, Object key) {
        return NotProvided.INSTANCE;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = HashGetOrUndefinedNodeGen.create(key, getHashNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
