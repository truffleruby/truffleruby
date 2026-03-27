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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.Nil;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class WriteBinaryStringNode extends FormatNode {

    private final boolean pad;
    private final boolean padOnNil;
    private final int width;
    private final byte padding;
    private final boolean takeAll;
    private final boolean appendNull;

    public WriteBinaryStringNode(
            boolean pad,
            boolean padOnNil,
            int width,
            byte padding,
            boolean takeAll,
            boolean appendNull) {
        this.pad = pad;
        this.padOnNil = padOnNil;
        this.width = width;
        this.padding = padding;
        this.takeAll = takeAll;
        this.appendNull = appendNull;
    }

    @Specialization
    Object write(VirtualFrame frame, Nil nil) {
        if (padOnNil) {
            for (int n = 0; n < width; n++) {
                writeByte(frame, padding);
            }
        } else if (appendNull) {
            writeByte(frame, (byte) 0);
        }

        return null;
    }

    // We use @Fallback here instead of:
    // @Specialization(guards = "libString.isRubyString(node, string)", limit = "1")
    // because otherwise Truffle wants this to be static and that would be very messy with the many instance fields.
    @Fallback
    Object write(VirtualFrame frame, Object string,
            @Cached RubyStringLibrary libString,
            @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
        var tstring = libString.getTString(this, string);
        var byteArray = getInternalByteArrayNode.execute(tstring, libString.getTEncoding(this, string));
        write(frame, byteArray.getArray(), byteArray.getOffset(), byteArray.getLength());
        return null;
    }

    private void write(VirtualFrame frame, byte[] bytes, int offset, int length) {
        final int lengthFromBytes;
        if (takeAll) {
            lengthFromBytes = length;
        } else {
            lengthFromBytes = Math.min(width, length);
        }

        if (pad) {
            final int lengthFromPadding = width - lengthFromBytes;

            writeBytes(frame, bytes, offset, lengthFromBytes);

            for (int n = 0; n < lengthFromPadding; n++) {
                writeByte(frame, padding);
            }
        } else {
            writeBytes(frame, bytes, offset, lengthFromBytes);
        }

        if (appendNull) {
            writeByte(frame, (byte) 0);
        }
    }

}
