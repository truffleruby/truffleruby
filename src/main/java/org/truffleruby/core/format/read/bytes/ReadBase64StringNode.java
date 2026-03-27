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
import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.core.format.write.bytes.EncodeUM;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadBase64StringNode extends FormatNode {

    @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

    private final int count;

    public ReadBase64StringNode(int count) {
        this.count = count;
    }

    @Specialization
    Object read(VirtualFrame frame, byte[] source) {
        final ByteBuffer encode = wrapByteBuffer(frame, source);

        final byte[] result = read(encode);

        setSourcePosition(frame, encode.position());

        return createString(fromByteArrayNode, result, Encodings.BINARY);
    }

    @TruffleBoundary
    private byte[] read(ByteBuffer encode) {
        int length = encode.remaining() * 3 / 4;
        byte[] lElem = new byte[length];
        int a = -1, b = -1, c = 0, d;
        int index = 0;
        int s = -1;

        if (count == 0) {
            if (encode.remaining() % 4 != 0) {
                throw new InvalidFormatException("invalid base64");
            }
            while (encode.hasRemaining()) {
                // obtain a
                s = safeGet(encode);
                a = EncodeUM.b64_xtable[s];
                if (a == -1) {
                    throw new InvalidFormatException("invalid base64");
                }

                // obtain b
                s = safeGet(encode);
                b = EncodeUM.b64_xtable[s];
                if (b == -1) {
                    throw new InvalidFormatException("invalid base64");
                }

                // obtain c
                s = safeGet(encode);
                c = EncodeUM.b64_xtable[s];
                if (s == '=') {
                    if (safeGet(encode) != '=') {
                        throw new InvalidFormatException("invalid base64");
                    }
                    break;
                }
                if (c == -1) {
                    throw new InvalidFormatException("invalid base64");
                }

                // obtain d
                s = safeGet(encode);
                d = EncodeUM.b64_xtable[s];
                if (s == '=') {
                    break;
                }
                if (d == -1) {
                    throw new InvalidFormatException("invalid base64");
                }

                // calculate based on a, b, c and d
                lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                lElem[index++] = (byte) ((b << 4 | c >> 2) & 255);
                lElem[index++] = (byte) ((c << 6 | d) & 255);
            }

            if (encode.hasRemaining()) {
                throw new InvalidFormatException("invalid base64");
            }

            if (a != -1) {
                if (c == -1 && s == '=') {
                    if ((b & 15) != 0) {
                        throw new InvalidFormatException("invalid base64");
                    }
                    lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                } else if (c != -1 && s == '=') {
                    if ((c & 3) != 0) {
                        throw new InvalidFormatException("invalid base64");
                    }
                    lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                    lElem[index++] = (byte) ((b << 4 | c >> 2) & 255);
                }
            }
        } else {

            while (encode.hasRemaining()) {
                b = c = -1;

                // obtain a
                s = safeGet(encode);
                while (((a = EncodeUM.b64_xtable[s]) == -1) && encode.hasRemaining()) {
                    s = safeGet(encode);
                }
                if (a == -1) {
                    break;
                }

                // obtain b
                s = safeGet(encode);
                while (((b = EncodeUM.b64_xtable[s]) == -1) && encode.hasRemaining()) {
                    s = safeGet(encode);
                }
                if (b == -1) {
                    break;
                }

                // obtain c
                s = safeGet(encode);
                while (((c = EncodeUM.b64_xtable[s]) == -1) && encode.hasRemaining()) {
                    if (s == '=') {
                        break;
                    }
                    s = safeGet(encode);
                }
                if ((s == '=') || c == -1) {
                    if (s == '=') {
                        encode.position(encode.position() - 1);
                    }
                    break;
                }

                // obtain d
                s = safeGet(encode);
                while (((d = EncodeUM.b64_xtable[s]) == -1) && encode.hasRemaining()) {
                    if (s == '=') {
                        break;
                    }
                    s = safeGet(encode);
                }
                if ((s == '=') || d == -1) {
                    if (s == '=') {
                        encode.position(encode.position() - 1);
                    }
                    break;
                }

                // calculate based on a, b, c and d
                lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                lElem[index++] = (byte) ((b << 4 | c >> 2) & 255);
                lElem[index++] = (byte) ((c << 6 | d) & 255);
                a = -1;
            }

            if (a != -1 && b != -1) {
                if (c == -1) {
                    lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                } else {
                    lElem[index++] = (byte) ((a << 2 | b >> 4) & 255);
                    lElem[index++] = (byte) ((b << 4 | c >> 2) & 255);
                }
            }
        }

        return Arrays.copyOfRange(lElem, 0, index);
    }

}
