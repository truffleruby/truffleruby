/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some logic copied from jruby.util.Pack
 *
 *  * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2003-2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Derek Berner <derek.berner@state.nm.us>
 * Copyright (C) 2006 Evan Buswell <ebuswell@gmail.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2009 Joseph LaFata <joe@quibb.org>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.core.format.read.bytes;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadUUStringNode extends FormatNode {

    @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

    @Specialization
    Object encode(VirtualFrame frame, byte[] source) {
        final ByteBuffer encode = wrapByteBuffer(frame, source);

        final byte[] bytes = read(encode);

        setSourcePosition(frame, encode.position());

        return createString(fromByteArrayNode, bytes, Encodings.BINARY);
    }

    @TruffleBoundary
    private byte[] read(ByteBuffer encode) {
        int length = encode.remaining() * 3 / 4;
        byte[] lElem = new byte[length];
        int index = 0;
        int s = 0;
        int total = 0;

        if (length > 0) {
            s = encode.get();
        }

        while (encode.hasRemaining() && s > ' ' && s < 'a') {
            int a, b, c, d;
            byte[] hunk = new byte[3];

            int len = (s - ' ') & 0x3F;
            s = safeGet(encode);
            total += len;
            if (total > length) {
                len -= total - length;
                total = length;
            }

            while (len > 0) {
                int mlen = Math.min(len, 3);

                if (encode.hasRemaining() && s >= ' ') {
                    a = (s - ' ') & 0x3F;
                    s = safeGet(encode);
                } else {
                    a = 0;
                }

                if (encode.hasRemaining() && s >= ' ') {
                    b = (s - ' ') & 0x3F;
                    s = safeGet(encode);
                } else {
                    b = 0;
                }

                if (encode.hasRemaining() && s >= ' ') {
                    c = (s - ' ') & 0x3F;
                    s = safeGet(encode);
                } else {
                    c = 0;
                }

                if (encode.hasRemaining() && s >= ' ') {
                    d = (s - ' ') & 0x3F;
                    s = safeGet(encode);
                } else {
                    d = 0;
                }

                hunk[0] = (byte) ((a << 2 | b >> 4) & 255);
                hunk[1] = (byte) ((b << 4 | c >> 2) & 255);
                hunk[2] = (byte) ((c << 6 | d) & 255);

                for (int i = 0; i < mlen; i++) {
                    lElem[index++] = hunk[i];
                }

                len -= mlen;
            }

            if (s == '\r') {
                s = safeGet(encode);
            } else if (s == '\n') {
                s = safeGet(encode);
            } else if (encode.hasRemaining()) {
                if (safeGet(encode) == '\n') {
                    safeGet(encode);
                } else if (encode.hasRemaining()) {
                    encode.position(encode.position() - 1);
                }
            }
        }

        return Arrays.copyOfRange(lElem, 0, index);
    }

}
