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

import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.oracle.truffle.api.dsl.Cached;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.core.numeric.FixnumOrBignumNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "source", type = SourceNode.class)
public abstract class ReadBERNode extends FormatNode {


    private static final long UL_MASK = 0xFE000000;
    private static final BigInteger BIG_128 = BigInteger.valueOf(128);

    private final ConditionProfile simpleProfile = ConditionProfile.create();

    @Specialization
    Object encode(VirtualFrame frame, byte[] source,
            @Cached FixnumOrBignumNode fixnumOrBignumNode) {
        final ByteBuffer encode = wrapByteBuffer(frame, source);
        int position = encode.position();

        final long ul = encode.get(position) & 0x7f;

        if (simpleProfile.profile((encode.get(position) & 0x80) == 0)) {
            position++;
            setSourcePosition(frame, position);
            return ul;
        } else {
            position++;
            assert (ul & UL_MASK) == 0;
            final BigIntegerAndPos bigIntegerAndPos = runLoop(encode, ul, position);

            setSourcePosition(frame, bigIntegerAndPos.getPos());
            return fixnumOrBignumNode.execute(this, bigIntegerAndPos.getBigInteger());
        }
    }

    @TruffleBoundary
    private BigIntegerAndPos runLoop(ByteBuffer encode, long ul, int pos) {
        BigInteger big = BigInteger.valueOf(ul);

        do {
            assert pos < encode.limit();
            big = big.multiply(BIG_128);
            big = big.add(BigInteger.valueOf(encode.get(pos) & 0x7f));
        } while ((encode.get(pos++) & 0x80) != 0);

        return new BigIntegerAndPos(big, pos);
    }

    private static final class BigIntegerAndPos {

        private final BigInteger bigInteger;
        private final int pos;

        public BigIntegerAndPos(BigInteger bigInteger, int pos) {
            this.bigInteger = bigInteger;
            this.pos = pos;
        }

        public BigInteger getBigInteger() {
            return bigInteger;
        }

        public int getPos() {
            return pos;
        }

    }

}
