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

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.RangeException;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("value")
public abstract class WriteUTF8CharacterNode extends FormatNode {

    @Specialization(guards = { "value >= 0", "value <= 0x7f" })
    Object writeSingleByte(VirtualFrame frame, long value) {
        writeByte(frame, (byte) value);
        return null;
    }

    @Specialization(guards = { "value > 0x7f", "value <= 0x7ff" })
    Object writeTwoBytes(VirtualFrame frame, long value) {
        final byte[] bytes = {
                (byte) (((value >>> 6) & 0xff) | 0xc0),
                (byte) ((value & 0x3f) | 0x80)
        };

        writeBytes(frame, bytes);
        return null;
    }

    @Specialization(guards = { "value > 0x7ff", "value <= 0xffff" })
    Object writeThreeBytes(VirtualFrame frame, long value) {
        final byte[] bytes = {
                (byte) (((value >>> 12) & 0xff) | 0xe0),
                (byte) (((value >>> 6) & 0x3f) | 0x80),
                (byte) ((value & 0x3f) | 0x80)
        };

        writeBytes(frame, bytes);
        return null;
    }

    @Specialization(guards = { "value > 0xffff", "value <= 0x1fffff" })
    Object writeFourBytes(VirtualFrame frame, long value) {
        final byte[] bytes = {
                (byte) (((value >>> 18) & 0xff) | 0xf0),
                (byte) (((value >>> 12) & 0x3f) | 0x80),
                (byte) (((value >>> 6) & 0x3f) | 0x80),
                (byte) ((value & 0x3f) | 0x80)
        };

        writeBytes(frame, bytes);
        return null;
    }

    @Specialization(guards = { "value > 0x1fffff", "value <= 0x3ffffff" })
    Object writeFiveBytes(VirtualFrame frame, long value) {
        final byte[] bytes = {
                (byte) (((value >>> 24) & 0xff) | 0xf8),
                (byte) (((value >>> 18) & 0x3f) | 0x80),
                (byte) (((value >>> 12) & 0x3f) | 0x80),
                (byte) (((value >>> 6) & 0x3f) | 0x80),
                (byte) ((value & 0x3f) | 0x80)
        };

        writeBytes(frame, bytes);
        return null;
    }

    @Specialization(guards = { "value > 0x3ffffff", "value <= 0x7fffffff" })
    Object writeSixBytes(VirtualFrame frame, long value) {
        final byte[] bytes = {
                (byte) (((value >>> 30) & 0xff) | 0xfc),
                (byte) (((value >>> 24) & 0x3f) | 0x80),
                (byte) (((value >>> 18) & 0x3f) | 0x80),
                (byte) (((value >>> 12) & 0x3f) | 0x80),
                (byte) (((value >>> 6) & 0x3f) | 0x80),
                (byte) ((value & 0x3f) | 0x80)
        };

        writeBytes(frame, bytes);
        return null;
    }

    @Specialization(guards = "value < 0")
    Object writeNegative(long value) {
        throw new RangeException("pack(U): value out of range");
    }

    @Specialization(guards = "value > 0x7fffffff")
    Object writeOutOfRange(long value) {
        throw new RangeException("pack(U): value out of range");
    }

}
