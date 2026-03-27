/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

@SuppressWarnings("serial")
public final class CannotConvertBinaryRubyStringToJavaString extends RuntimeException {

    private final int nonAsciiCharacter;

    public CannotConvertBinaryRubyStringToJavaString(int nonAsciiCharacter) {
        super(
                "Cannot convert a Ruby String with BINARY encoding containing non-US-ASCII character " +
                        nonAsciiCharacter + " to a Java String");
        this.nonAsciiCharacter = nonAsciiCharacter;
    }

    public int getNonAsciiCharacter() {
        return nonAsciiCharacter;
    }

}
