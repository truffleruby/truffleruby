/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.language.dispatch.DispatchNode;

public final class PreInitializationManager {

    private final List<ReHashable> reHashables = new ArrayList<>();

    private final Set<RubyHash> hashesCreatedDuringPreInit = Collections.newSetFromMap(new WeakHashMap<>());

    public void addReHashable(ReHashable reHashable) {
        // This might get called multiple times for the same ReHashable,
        // so only add it if it is not already in the List.
        for (ReHashable existing : reHashables) {
            if (reHashable == existing) {
                return;
            }
        }
        reHashables.add(reHashable);
    }

    @TruffleBoundary
    public void addPreInitHash(RubyHash hash) {
        hashesCreatedDuringPreInit.add(hash);
    }

    public void rehash() {
        for (ReHashable reHashable : reHashables) {
            reHashable.rehash();
        }
        reHashables.clear();

        rehashRubyHashes();
    }

    private void rehashRubyHashes() {
        for (RubyHash hash : hashesCreatedDuringPreInit) {
            if (!HashGuards.isCompareByIdentity(hash)) {
                DispatchNode.getUncached().call(hash, "rehash");
            }
        }
        hashesCreatedDuringPreInit.clear();
    }

}
