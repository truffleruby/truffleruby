/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

/** Take a Ruby object that has an encoding and extracts the Java-level encoding object. */

@GenerateInline
@GenerateCached(false)
public abstract class ToRubyEncodingNode extends RubyBaseNode {

    public abstract RubyEncoding execute(Node node, Object value);

    @Specialization
    static RubyEncoding stringToEncoding(Node node, RubyString value,
            @Cached @Exclusive RubyStringLibrary libString) {
        return libString.getEncoding(node, value);
    }

    @Specialization
    static RubyEncoding immutableStringToEncoding(Node node, ImmutableRubyString value,
            @Cached @Exclusive RubyStringLibrary libString) {
        return libString.getEncoding(node, value);
    }

    @Specialization
    static RubyEncoding symbolToEncoding(RubySymbol value) {
        return value.encoding;
    }

    @Specialization
    static RubyEncoding regexpToEncoding(RubyRegexp value) {
        return value.encoding;
    }

    @Specialization
    static RubyEncoding rubyEncodingToEncoding(RubyEncoding value) {
        return value;
    }

    @Fallback
    static RubyEncoding failure(Object value) {
        return null;
    }
}
