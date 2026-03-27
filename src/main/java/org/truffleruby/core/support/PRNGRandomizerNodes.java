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

import java.math.BigInteger;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.algorithms.Randomizer;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.annotations.Visibility;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "Truffle::PRNGRandomizer", isClass = true)
public abstract class PRNGRandomizerNodes {

    public static RubyPRNGRandomizer newRandomizer(RubyContext context, RubyLanguage language, boolean threadSafe) {
        final RubyBignum seed = RandomizerGenSeedNode.randomSeedBignum(context);
        final Randomizer randomizer = RandomizerSetSeedNode.randomFromBignum(seed);
        return new RubyPRNGRandomizer(
                context.getCoreLibrary().prngRandomizerClass,
                language.prngRandomizerShape,
                randomizer,
                threadSafe);
    }

    public static void resetSeed(RubyContext context, RubyPRNGRandomizer random) {
        final RubyBignum seed = RandomizerGenSeedNode.randomSeedBignum(context);
        final Randomizer randomizer = RandomizerSetSeedNode.randomFromBignum(seed);
        random.setRandomizer(randomizer);
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPRNGRandomizer randomizerAllocate(RubyClass randomizerClass) {
            // Since this is a manually-created Truffle::Randomizer instance that can be shared by multiple threads,
            // we enable thread-safe mode in the Randomizer.
            final boolean threadSafe = true;

            return new RubyPRNGRandomizer(
                    coreLibrary().prngRandomizerClass,
                    getLanguage().prngRandomizerShape,
                    new Randomizer(),
                    threadSafe);
        }

    }

    @CoreMethod(names = "seed")
    public abstract static class RandomizerSeedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object seed(RubyPRNGRandomizer randomizer) {
            return randomizer.getSeed();
        }

    }

    @CoreMethod(names = "seed=", required = 1)
    public abstract static class RandomizerSetSeedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyPRNGRandomizer setSeed(RubyPRNGRandomizer randomizer, long seed) {
            randomizer.setRandomizer(randomFromLong(seed));
            return randomizer;
        }

        @Specialization
        RubyPRNGRandomizer setSeed(RubyPRNGRandomizer randomizer, RubyBignum seed) {
            randomizer.setRandomizer(randomFromBignum(seed));
            return randomizer;
        }

        @TruffleBoundary
        static Randomizer randomFromLong(long seed) {
            long v = Math.abs(seed);
            if (v == (v & 0xffffffffL)) {
                return new Randomizer(seed, (int) v);
            } else {
                int[] ints = new int[2];
                ints[0] = (int) v;
                ints[1] = (int) (v >> 32);
                return new Randomizer(seed, ints);
            }
        }

        private static final int N = 624;

        @TruffleBoundary
        static Randomizer randomFromBignum(RubyBignum seed) {
            BigInteger big = seed.value;
            if (big.signum() < 0) {
                big = big.abs();
            }
            byte[] buf = big.toByteArray();
            int buflen = buf.length;
            if (buf[0] == 0) {
                buflen -= 1;
            }
            int len = Math.min((buflen + 3) / 4, N);
            int[] ints = bigEndianToInts(buf, len);
            if (len <= 1) {
                return new Randomizer(seed, ints[0]);
            } else {
                return new Randomizer(seed, ints);
            }
        }

        private static int[] bigEndianToInts(byte[] buf, int initKeyLen) {
            int[] initKey = new int[initKeyLen];
            for (int idx = 0; idx < initKey.length; ++idx) {
                initKey[idx] = getIntBigIntegerBuffer(buf, idx);
            }
            return initKey;
        }

        static int getIntBigIntegerBuffer(byte[] src, int loc) {
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

    }

    @CoreMethod(names = "generate_seed", needsSelf = false)
    public abstract static class RandomizerGenSeedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyBignum generateSeed() {
            return randomSeedBignum(getContext());
        }

        private static final int DEFAULT_SEED_CNT = 4;

        @TruffleBoundary
        public static RubyBignum randomSeedBignum(RubyContext context) {
            byte[] seed = context.getRandomSeedBytes(DEFAULT_SEED_CNT * 4);
            final BigInteger bigInteger = new BigInteger(seed).abs();
            return BignumOperations.createBignum(bigInteger);
        }

    }

    @Primitive(name = "randomizer_bytes", lowerFixnum = 1)
    public abstract static class RandomizerBytesPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyString genRandBytes(RubyPRNGRandomizer randomizer, int length,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            final byte[] bytes = new byte[length];
            int idx = 0;
            for (; length >= 4; length -= 4) {
                int r = randomizer.genrandInt32();
                for (int i = 0; i < 4; ++i) {
                    bytes[idx++] = (byte) (r & 0xff);
                    r >>>= 8;
                }
            }
            if (length > 0) {
                int r = randomizer.genrandInt32();
                for (int i = 0; i < length; ++i) {
                    bytes[idx++] = (byte) (r & 0xff);
                    r >>>= 8;
                }
            }

            return createString(fromByteArrayNode, bytes, Encodings.BINARY);
        }
    }

}
