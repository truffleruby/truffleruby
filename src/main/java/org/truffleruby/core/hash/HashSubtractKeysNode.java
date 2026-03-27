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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.hash.library.HashStoreLibrary;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/** Based on {@link org.truffleruby.language.arguments.ReadKeywordRestArgumentNode} */
@NodeChild(value = "hashNode", type = RubyNode.class)
public abstract class HashSubtractKeysNode extends RubyContextSourceNode implements EachEntryCallback {

    @CompilationFinal(dimensions = 1) private final RubySymbol[] excludedKeys;

    @Child private HashStoreLibrary hashes = HashStoreLibrary.createDispatched();

    public HashSubtractKeysNode(RubySymbol[] excludedKeys) {
        assert excludedKeys.length > 0 : "unnecessary";
        this.excludedKeys = excludedKeys;
    }

    abstract RubyNode getHashNode();

    @Specialization
    RubyHash remainingKeys(RubyHash hash) {
        RubyHash rest = HashOperations.newEmptyHash(getContext(), getLanguage());
        hashes.eachEntry(hash.store, hash, this, rest);
        return rest;
    }

    @Override
    public void accept(int index, Object key, Object value, Object state) {
        if (!keyExcluded(key)) {
            RubyHash rest = (RubyHash) state;
            hashes.set(rest.store, rest, key, value, false);
        }
    }

    @ExplodeLoop
    private boolean keyExcluded(Object key) {
        for (RubySymbol excludedKey : excludedKeys) {
            if (excludedKey == key) {
                return true;
            }
        }

        return false;
    }

    @Override
    public RubyNode cloneUninitialized() {
        return HashSubtractKeysNodeGen.create(excludedKeys, getHashNode().cloneUninitialized()).copyFlags(this);
    }

}
