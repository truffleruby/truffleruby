/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.truffleruby.shadowed.org.joni.Regex;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.encoding.RubyEncoding;

public final class EncodingCache {
    private final Map<RubyEncoding, Regex> encodings;

    @TruffleBoundary
    public EncodingCache() {
        this.encodings = new ConcurrentHashMap<>();
    }

    public Regex getOrCreate(RubyEncoding encoding, Function<RubyEncoding, Regex> function) {
        return ConcurrentOperations.getOrCompute(encodings, encoding, function);
    }
}
