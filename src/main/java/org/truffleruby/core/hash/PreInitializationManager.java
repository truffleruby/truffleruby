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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.language.dispatch.DispatchNode;

public final class PreInitializationManager {

    private final Set<RubyHash> hashesCreatedDuringPreInit = Collections.newSetFromMap(new WeakHashMap<>());

    @TruffleBoundary
    public void addPreInitHash(RubyHash hash) {
        hashesCreatedDuringPreInit.add(hash);
    }

    public void rehash() {
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
