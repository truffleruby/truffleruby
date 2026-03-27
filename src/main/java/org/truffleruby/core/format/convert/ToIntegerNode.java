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
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("value")
public abstract class ToIntegerNode extends FormatNode {

    public abstract Object executeToInteger(VirtualFrame frame, Object object);

    @Specialization
    int toInteger(int value) {
        return value;
    }

    @Specialization
    long toInteger(long value) {
        return value;
    }

    @Specialization
    RubyBignum toInteger(RubyBignum value) {
        return value;
    }

    @Specialization
    long toInteger(double value) {
        return (long) value;
    }

    @Specialization(guards = "!isRubyNumber(value)")
    Object toInteger(VirtualFrame frame, Object value,
            @Cached DispatchNode integerNode) {
        return integerNode.call(getContext().getCoreLibrary().kernelModule, "Integer", value);
    }

}
