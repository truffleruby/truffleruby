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
package org.truffleruby.core.format.write.bytes;

import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("value")
public abstract class WriteByteNode extends FormatNode {

    @Specialization
    Object doWrite(VirtualFrame frame, byte value) {
        writeByte(frame, value);
        return null;
    }

    @Specialization
    Object doWrite(VirtualFrame frame, long value) {
        return doWrite(frame, (byte) value);
    }

}
