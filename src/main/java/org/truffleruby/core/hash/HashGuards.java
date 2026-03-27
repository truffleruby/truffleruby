/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import org.truffleruby.core.hash.library.PackedHashStoreLibrary;

public abstract class HashGuards {

    // Storage strategies

    public static int hashStrategyLimit() {
        return 3;
    }

    public static int packedHashLimit() {
        // + 1 for packed Hash with size = 0
        return PackedHashStoreLibrary.MAX_ENTRIES + 1;
    }

    // Higher level properties

    public static boolean isCompareByIdentity(RubyHash hash) {
        return hash.compareByIdentity;
    }
}
