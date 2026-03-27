/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Copyright the JRuby team.
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
package org.truffleruby.core.support;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.FixnumOrBignumNode;
import org.truffleruby.core.numeric.RubyBignum;

import java.math.BigInteger;

@CoreModule(value = "Truffle::Randomizer", isClass = true)
public abstract class RandomizerNodes {

    // Generate a random Float, in the range 0...1.0
    @CoreMethod(names = "random_float")
    public abstract static class RandomFloatNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        double randomFloat(RubyRandomizer randomizer,
                @Cached GetRandomIntNode getRandomIntNode) {
            // Logic copied from org.jruby.util.Random
            final int a = getRandomIntNode.execute(randomizer) >>> 5;
            final int b = getRandomIntNode.execute(randomizer) >>> 6;
            return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
        }

    }

    // Generate a random Integer, in the range 0...limit
    @CoreMethod(names = "random_integer", required = 1)
    public abstract static class RandomIntNode extends CoreMethodArrayArgumentsNode {

        @Child GetRandomIntNode getRandomIntNode = GetRandomIntNode.create();

        @Specialization
        int randomizerRandInt(RubyRandomizer randomizer, int limit) {
            return (int) randLimitedFixnumInner(randomizer, limit);
        }

        @Specialization
        long randomizerRandInt(RubyRandomizer randomizer, long limit) {
            return randLimitedFixnumInner(randomizer, limit);
        }

        @Specialization
        Object randomizerRandInt(RubyRandomizer randomizer, RubyBignum limit,
                @Cached FixnumOrBignumNode fixnumOrBignum) {
            return fixnumOrBignum.execute(this, randLimitedBignum(randomizer, limit.value));
        }

        @TruffleBoundary
        public long randLimitedFixnumInner(RubyRandomizer randomizer, long limit) {
            long val;
            if (limit == 0) {
                val = 0;
            } else {
                long mask = makeMask(limit);
                // take care before code cleanup; it might break random sequence compatibility
                retry: while (true) {
                    val = 0;
                    for (int i = 1; 0 <= i; --i) {
                        if (((mask >>> (i * 32)) & 0xffffffffL) != 0) {
                            val |= (getRandomIntNode.execute(randomizer) & 0xffffffffL) << (i * 32);
                            val &= mask;
                        }
                        if (limit < val) {
                            continue retry;
                        }
                    }
                    break;
                }
            }
            return val;
        }

        @TruffleBoundary
        private BigInteger randLimitedBignum(RubyRandomizer randomizer, BigInteger limit) {
            byte[] buf = BigIntegerOps.toByteArray(limit);
            byte[] bytes = new byte[buf.length];
            int len = (buf.length + 3) / 4;
            // take care before code cleanup; it might break random sequence compatibility
            retry: while (true) {
                long mask = 0;
                boolean boundary = true;
                for (int idx = len - 1; 0 <= idx; --idx) {
                    long lim = getIntBigIntegerBuffer(buf, idx) & 0xffffffffL;
                    mask = (mask != 0) ? 0xffffffffL : makeMask(lim);
                    long rnd;
                    if (mask != 0) {
                        rnd = (getRandomIntNode.execute(randomizer) & 0xffffffffL) & mask;
                        if (boundary) {
                            if (lim < rnd) {
                                continue retry;
                            }
                            if (rnd < lim) {
                                boundary = false;
                            }
                        }
                    } else {
                        rnd = 0;
                    }
                    setIntBigIntegerBuffer(bytes, idx, (int) rnd);
                }
                break;
            }
            return BigIntegerOps.create(bytes);
        }

        private static int getIntBigIntegerBuffer(byte[] src, int loc) {
            int v = 0;
            int idx = src.length - loc * 4 - 1;
            if (idx >= 0) {
                v |= (src[idx--] & 0xff);
                if (idx >= 0) {
                    v |= (src[idx--] & 0xff) << 8;
                    if (idx >= 0) {
                        v |= (src[idx--] & 0xff) << 16;
                        if (idx >= 0) {
                            v |= (src[idx--] & 0xff) << 24;
                        }
                    }
                }
            }
            return v;
        }

        private static void setIntBigIntegerBuffer(byte[] dest, int loc, int value) {
            int idx = dest.length - loc * 4 - 1;
            if (idx >= 0) {
                dest[idx--] = (byte) (value & 0xff);
                if (idx >= 0) {
                    dest[idx--] = (byte) ((value >> 8) & 0xff);
                    if (idx >= 0) {
                        dest[idx--] = (byte) ((value >> 16) & 0xff);
                        if (idx >= 0) {
                            dest[idx--] = (byte) ((value >> 24) & 0xff);
                        }
                    }
                }
            }
        }

        private static long makeMask(long x) {
            x = x | x >>> 1;
            x = x | x >>> 2;
            x = x | x >>> 4;
            x = x | x >>> 8;
            x = x | x >>> 16;
            x = x | x >>> 32;
            return x;
        }

    }


}
