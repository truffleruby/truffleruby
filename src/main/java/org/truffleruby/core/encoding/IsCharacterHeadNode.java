/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.encoding;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.language.RubyBaseNode;

/** Whether the position at byteOffset is the start of a character and not in the middle of a character */
public abstract class IsCharacterHeadNode extends RubyBaseNode {

    public abstract boolean execute(RubyEncoding enc, AbstractTruffleString string, int byteOffset);

    @Specialization(guards = "enc.isSingleByte")
    boolean singleByte(RubyEncoding enc, AbstractTruffleString string, int byteOffset) {
        // return offset directly (org.jcodings.SingleByteEncoding#leftAdjustCharHead)
        return true;
    }

    @Specialization(guards = { "!enc.isSingleByte", "enc.jcoding.isUTF8()" })
    boolean utf8(RubyEncoding enc, AbstractTruffleString string, int byteOffset,
            @Cached TruffleString.ReadByteNode readByteNode) {
        // based on org.jcodings.specific.BaseUTF8Encoding#leftAdjustCharHead
        return utf8IsLead(readByteNode.execute(string, byteOffset, enc.tencoding));

    }

    @TruffleBoundary
    @Specialization(guards = { "!enc.isSingleByte", "!enc.jcoding.isUTF8()" })
    boolean other(RubyEncoding enc, AbstractTruffleString string, int byteOffset,
            @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
        var byteArray = getInternalByteArrayNode.execute(string, enc.tencoding);
        int addedOffsets = byteArray.getOffset() + byteOffset;
        return enc.jcoding.leftAdjustCharHead(byteArray.getArray(), byteArray.getOffset(), addedOffsets,
                byteArray.getEnd()) == addedOffsets;
    }

    /** Copied from org.jcodings.specific.BaseUTF8Encoding */
    private static boolean utf8IsLead(int c) {
        return ((c & 0xc0) & 0xff) != 0x80;
    }

}
