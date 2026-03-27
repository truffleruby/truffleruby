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
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class ForeignToRubyNode extends RubyBaseNode {

    public final Object executeCached(Object value) {
        return execute(this, value);
    }

    public abstract Object execute(Node node, Object value);

    @Specialization
    static int convertByte(byte value) {
        return value;
    }

    @Specialization
    static int convertShort(short value) {
        return value;
    }

    @Specialization
    static double convertFloat(float value) {
        return value;
    }

    @Fallback
    static Object convert(Object value) {
        return value;
    }

}
