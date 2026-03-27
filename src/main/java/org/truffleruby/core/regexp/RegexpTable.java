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
package org.truffleruby.core.regexp;

import org.truffleruby.collections.WeakValueCache;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class RegexpTable {

    // A cache for j.l.String to Regexps. Entries are kept as long as the Regexp is alive.
    // However, this doesn't matter as the cache entries will be re-created when used.
    private final WeakValueCache<RegexpCacheKey, RubyRegexp> regexpTable = new WeakValueCache<>();

    public RegexpTable() {
    }

    @TruffleBoundary
    public RubyRegexp getRegexpIfExists(RegexpCacheKey key) {
        return regexpTable.get(key);
    }

    public void addRegexp(RegexpCacheKey key, RubyRegexp regexp) {
        regexpTable.put(key, regexp);
    }

}
