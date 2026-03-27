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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("value")
public abstract class ToNumberWithCoercionNode extends FormatNode {

    @Child private DispatchNode floatNode;

    @Specialization
    Object alreadyDouble(VirtualFrame frame, double value) {
        return value;
    }

    @Specialization
    Object alreadyLong(VirtualFrame frame, long value) {
        return value;
    }

    @Specialization
    Object alreadyBignum(VirtualFrame frame, RubyBignum value) {
        return value.value;
    }

    @Fallback
    Object toDouble(VirtualFrame frame, Object value) {
        if (floatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            floatNode = insert(DispatchNode.create());
        }

        return floatNode.call(getContext().getCoreLibrary().kernelModule, "Float", value);
    }

}
