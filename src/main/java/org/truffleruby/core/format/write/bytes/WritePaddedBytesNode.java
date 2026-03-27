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
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.library.RubyStringLibrary;

/** Simply write bytes. */
@NodeChild("width")
@NodeChild("precision")
@NodeChild("value")
public abstract class WritePaddedBytesNode extends FormatNode {

    private final ConditionProfile leftJustifiedProfile = ConditionProfile.create();
    private final ConditionProfile paddingProfile = ConditionProfile.create();
    private final ConditionProfile precisionProfile = ConditionProfile.create();
    private final boolean leftJustified;

    public WritePaddedBytesNode(boolean leftJustified) {
        this.leftJustified = leftJustified;
    }

    @Specialization
    Object write(VirtualFrame frame, int padding, int precision, Object string,
            @Bind Node node,
            @Cached RubyStringLibrary libString,
            @Cached TruffleString.CodePointLengthNode codePointLengthNode,
            @Cached TruffleString.CodePointIndexToByteIndexNode codePointIndexToByteIndexNode,
            @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
        if (padding == PrintfSimpleTreeBuilder.DEFAULT) {
            padding = 0;
        }

        var tstring = libString.getTString(node, string);
        var encoding = libString.getEncoding(node, string);
        if (leftJustifiedProfile.profile(leftJustified || padding < 0)) {
            writeStringBytes(frame, precision, tstring, encoding, codePointIndexToByteIndexNode, byteArrayNode);
            writePaddingBytes(frame, Math.abs(padding), precision, tstring, encoding, codePointLengthNode);
        } else {
            writePaddingBytes(frame, padding, precision, tstring, encoding, codePointLengthNode);
            writeStringBytes(frame, precision, tstring, encoding, codePointIndexToByteIndexNode, byteArrayNode);
        }

        return null;
    }

    private void writeStringBytes(VirtualFrame frame, int precision,
            AbstractTruffleString tstring, RubyEncoding encoding,
            TruffleString.CodePointIndexToByteIndexNode codePointIndexToByteIndexNode,
            TruffleString.GetInternalByteArrayNode byteArrayNode) {
        var byteArray = byteArrayNode.execute(tstring, encoding.tencoding);
        int length;

        if (precisionProfile.profile(precision >= 0 && byteArray.getLength() > precision)) {
            int index = codePointIndexToByteIndexNode.execute(tstring, 0, precision, encoding.tencoding);
            if (index >= 0) {
                length = index;
            } else {
                length = byteArray.getLength();
            }
        } else {
            length = byteArray.getLength();
        }

        writeBytes(frame, byteArray.getArray(), byteArray.getOffset(), length);
    }

    private void writePaddingBytes(VirtualFrame frame, int padding, int precision, AbstractTruffleString tstring,
            RubyEncoding encoding,
            TruffleString.CodePointLengthNode codePointLengthNode) {
        if (paddingProfile.profile(padding > 0)) {
            int codePointLength = codePointLengthNode.execute(tstring, encoding.tencoding);
            int padBytes;
            if (precision > 0 && codePointLength > precision) {
                padBytes = padding - precision;
            } else if (padding > 0 && padding > codePointLength) {
                padBytes = padding - codePointLength;
            } else {
                padBytes = 0;
            }

            for (int n = 0; n < padBytes; n++) {
                writeByte(frame, (byte) ' ');
            }
        }
    }
}
