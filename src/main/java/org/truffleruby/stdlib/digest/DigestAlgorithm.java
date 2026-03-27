/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.stdlib.digest;

enum DigestAlgorithm {
    MD5("MD5", 16, 64),
    SHA1("SHA1", 20, 64),
    SHA256("SHA-256", 32, 64),
    SHA384("SHA-384", 48, 128),
    SHA512("SHA-512", 64, 128);

    private final String name;
    private final int length;
    private final int blockLength;

    DigestAlgorithm(String name, int length, int blockLength) {
        this.name = name;
        this.length = length;
        this.blockLength = blockLength;
    }

    public String getName() {
        return name;
    }

    public int getLength() {
        return length;
    }

    public int getBlockLength() {
        return blockLength;
    }
}
