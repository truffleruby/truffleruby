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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.NotProvided;

import com.oracle.truffle.api.dsl.Specialization;

/** Casts a value into a boolean and defaults to the given value if not provided. */
@GenerateCached(false)
@GenerateInline
public abstract class BooleanCastWithDefaultNode extends RubyBaseNode {

    public abstract boolean execute(Node node, Object value, boolean defaultValue);

    @Specialization
    static boolean doDefault(NotProvided value, boolean defaultValue) {
        return defaultValue;
    }

    @Fallback
    static boolean fallback(Node node, Object value, boolean defaultValue,
            @Cached BooleanCastNode booleanCastNode) {
        return booleanCastNode.execute(node, value);
    }
}
