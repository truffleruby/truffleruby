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

import java.math.BigInteger;

import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.CantCompressNegativeException;
import org.truffleruby.core.numeric.RubyBignum;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild("value")
public abstract class WriteBERNode extends FormatNode {

    private final ConditionProfile cantCompressProfile = ConditionProfile.create();

    private final BigInteger BIG_128 = BigInteger.valueOf(128);

    @Specialization
    Object doWrite(VirtualFrame frame, int value) {
        if (cantCompressProfile.profile(value < 0)) {
            throw new CantCompressNegativeException();
        }

        writeBytes(frame, encode(value));
        return null;
    }

    @Specialization
    Object doWrite(VirtualFrame frame, long value) {
        if (cantCompressProfile.profile(value < 0)) {
            throw new CantCompressNegativeException();
        }

        writeBytes(frame, encode(value));
        return null;
    }

    @Specialization
    Object doWrite(VirtualFrame frame, RubyBignum value) {
        if (cantCompressProfile.profile(value.value.signum() < 0)) {
            throw new CantCompressNegativeException();
        }

        writeBytes(frame, encode(value));
        return null;
    }

    @TruffleBoundary
    private byte[] encode(Object from) {
        // TODO CS 30-Mar-15 should write our own optimizable version of BER

        final ByteArrayBuilder buf = new ByteArrayBuilder();

        long l;

        if (from instanceof RubyBignum) {
            from = ((RubyBignum) from).value;
            while (true) {
                BigInteger bignum = (BigInteger) from;
                BigInteger[] ary = bignum.divideAndRemainder(BIG_128);
                buf.append((byte) (ary[1].longValue() | 0x80) & 0xff);

                if (ary[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                    l = ary[0].longValue();
                    break;
                }
                from = ary[0];
            }
        } else if (from instanceof Integer) {
            l = (int) from;
        } else if (from instanceof Long) {
            l = (long) from;
        } else {
            throw new UnsupportedOperationException();
        }

        while (l != 0) {
            buf.append((byte) (((l & 0x7f) | 0x80) & 0xff));
            l >>= 7;
        }

        int left = 0;
        int right = buf.getLength() - 1;

        if (right >= 0) {
            buf.getUnsafeBytes()[0] &= 0x7F;
        } else {
            buf.append(0);
        }

        while (left < right) {
            final byte tmp = buf.getUnsafeBytes()[left];
            buf.getUnsafeBytes()[left] = buf.getUnsafeBytes()[right];
            buf.getUnsafeBytes()[right] = tmp;

            left++;
            right--;
        }

        return buf.getBytes();
    }

}
