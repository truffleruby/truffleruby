/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.convert;

import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.Nil;

@NodeChild("value")
public abstract class ReinterpretByteAsIntegerNode extends FormatNode {

    private final boolean signed;

    public ReinterpretByteAsIntegerNode(boolean signed) {
        this.signed = signed;
    }

    @Specialization
    Object decode(Nil nil) {
        return nil;
    }

    @Specialization
    int decode(byte value) {
        if (signed) {
            return value;
        } else {
            return Byte.toUnsignedInt(value);
        }
    }

}
