/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/** Convert a Ruby String or Symbol to a Java string, or return a default string if a value was not provided. */
@GenerateInline
@GenerateCached(false)
public abstract class ToJavaStringWithDefaultNode extends RubyBaseNode {

    public abstract String execute(Node node, Object value, String defaultValue);

    @Specialization
    static String doDefault(NotProvided value, String defaultValue) {
        return defaultValue;
    }

    @Specialization(guards = "wasProvided(value)")
    static String doProvided(Node node, Object value, String defaultValue,
            @Cached ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.execute(node, value);
    }

}
