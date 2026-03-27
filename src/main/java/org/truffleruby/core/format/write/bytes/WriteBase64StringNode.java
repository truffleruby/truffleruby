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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class WriteBase64StringNode extends FormatNode {

    private final int length;
    private final boolean ignoreStar;

    public WriteBase64StringNode(int length, boolean ignoreStar) {
        this.length = length;
        this.ignoreStar = ignoreStar;
    }

    @Specialization
    Object write(VirtualFrame frame, Object string,
            @Bind Node node,
            @Cached RubyStringLibrary libString,
            @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
        var tstring = libString.getTString(node, string);
        var encoding = libString.getTEncoding(node, string);

        writeBytes(frame, encode(byteArrayNode.execute(tstring, encoding)));

        return null;
    }

    @TruffleBoundary
    private byte[] encode(InternalByteArray byteArray) {
        // TODO CS 30-Mar-15 should write our own optimisable version of Base64

        final ByteArrayBuilder output = new ByteArrayBuilder();
        EncodeUM.encodeUM(byteArray, length, ignoreStar, 'm', output);
        return output.getBytes();
    }

}
