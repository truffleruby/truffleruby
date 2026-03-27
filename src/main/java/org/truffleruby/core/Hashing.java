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
package org.truffleruby.core;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class Hashing {

    public static final Hashing NO_SEED = new Hashing(0);

    @CompilationFinal private long seed;

    public Hashing(long seed) {
        this.seed = seed;
    }

    public void patchSeed(long seed) {
        this.seed = seed;
    }

    public int hash(int value) {
        return value + (int) seed;
    }

    public long hash(long salt, long value) {
        return end(update(start(salt), value));
    }

    public long start(long salt) {
        return salt + seed;
    }

    public static long update(long hash, long value) {
        hash += value;
        return murmur(murmur(hash) + (hash >>> 32));
    }

    public static long end(long hash) {
        return murmurStep(murmurStep(hash, 10), 17);
    }

    /* stringHash is not resistant to known Ruby vulnerabilities and so should be used in combination with the context's
     * Hashing instance. */
    public static int stringHash(byte[] bytes, int startingHashCode, int offset, int length) {
        assert offset + length <= bytes.length;

        int hashCode = startingHashCode;
        final int endIndex = offset + length;
        for (int i = offset; i < endIndex; i++) {
            hashCode = 31 * hashCode + bytes[i];
        }

        return hashCode;
    }

    private static long murmur(long h) {
        return murmurStep(h, 16);
    }

    private static long murmurStep(long h, long k) {
        h += k;
        h *= 0x5bd1e995; // The MurmurHash2 magic number
        h ^= h >> 16;
        return h;
    }

}
