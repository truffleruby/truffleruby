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
package org.truffleruby.core.format.write.bytes;

import java.nio.ByteOrder;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class WriteHexStringNode extends FormatNode {

    private final ByteOrder byteOrder;
    private final int length;

    public WriteHexStringNode(ByteOrder byteOrder, int length) {
        this.byteOrder = byteOrder;
        this.length = length;
    }

    @Specialization
    Object write(VirtualFrame frame, Object string,
            @Bind Node node,
            @Cached RubyStringLibrary libString,
            @Cached TruffleString.GetInternalByteArrayNode byteArrayNode) {
        var tstring = libString.getTString(node, string);
        var encoding = libString.getTEncoding(node, string);

        return write(frame, byteArrayNode.execute(tstring, encoding));
    }

    protected Object write(VirtualFrame frame, InternalByteArray byteArray) {
        int currentByte = 0;

        final int lengthToUse;

        if (length == -1) {
            lengthToUse = byteArray.getLength();
        } else {
            lengthToUse = length;
        }

        for (int n = 0; n < lengthToUse; n++) {
            byte currentChar;

            if (n < byteArray.getLength()) {
                currentChar = byteArray.get(n);
            } else {
                currentChar = 0;
            }

            if (Character.isJavaIdentifierStart(currentChar)) {
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    currentByte |= (((currentChar & 15) + 9) & 15) << 4;
                } else {
                    currentByte |= ((currentChar & 15) + 9) & 15;
                }
            } else {
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    currentByte |= (currentChar & 15) << 4;
                } else {
                    currentByte |= currentChar & 15;
                }
            }

            if (((n - 1) & 1) != 0) {
                if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    currentByte >>= 4;
                } else {
                    currentByte <<= 4;
                }
            } else {
                writeByte(frame, (byte) currentByte);
                currentByte = 0;
            }
        }

        if ((lengthToUse & 1) != 0) {
            writeByte(frame, (byte) (currentByte & 0xff));
        }

        return null;
    }

}
