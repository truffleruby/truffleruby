/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some logic copied from pack.c
 *
 * Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */
package org.truffleruby.core.format.read.bytes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadMIMEStringNode extends FormatNode {

    @Specialization
    Object read(VirtualFrame frame, byte[] source,
            @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
        final int position = getSourcePosition(frame);
        final int end = getSourceEnd(frame);

        final byte[] store = new byte[end - position];

        final int storeIndex = parseSource(source, position, end, store);

        setSourcePosition(frame, end);

        var tstring = fromByteArrayNode.execute(store, 0, storeIndex, Encodings.BINARY.tencoding, true);
        return createString(tstring, Encodings.BINARY);
    }

    // Logic from MRI pack.c pack_unpack_internal
    // https://github.com/ruby/ruby/blob/37c2cd3fa47c709570e22ec4dac723ca211f423a/pack.c#L1639
    @TruffleBoundary
    private int parseSource(byte[] source, int position, int end, byte[] store) {
        int sourceLength = end - position;
        int storeIndex = 0;
        int loopIndex = 0;
        if (sourceLength > 0) {
            int i = position;
            int c = source[i] & 0xff;
            while (i < end) {

                if (c == '=') {
                    if (++i == end) {
                        break;
                    }
                    c = source[i] & 0xff;

                    if (i + 1 < end && c == '\r' && (source[i + 1] & 0xff) == '\n') {
                        i++;
                        c = source[i] & 0xff;
                    }

                    if (c != '\n') {
                        final int c1 = Character.digit(c, 16);
                        if (c1 == -1) {
                            break;
                        }

                        if (++i == end) {
                            break;
                        }
                        c = source[i] & 0xff;

                        final int c2 = Character.digit(c, 16);
                        if (c2 == -1) {
                            break;
                        }

                        final byte value = (byte) (c1 << 4 | c2);
                        store[storeIndex] = value;
                        storeIndex++;
                    }

                } else {
                    store[storeIndex] = (byte) c;
                    storeIndex++;
                }
                i++;
                if (i < end) {
                    c = source[i] & 0xff;
                }
                loopIndex = i - position;
            }
        }

        final int storeLength = store.length;
        if (loopIndex < storeLength) {
            final int left = storeLength - loopIndex;
            System.arraycopy(source, position + loopIndex, store, storeIndex, left);
            storeIndex += left;
        }
        return storeIndex;
    }

}
