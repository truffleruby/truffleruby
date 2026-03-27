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
package org.truffleruby.test.internal;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import junit.framework.TestCase;
import org.junit.Test;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.loader.EmbeddedScript;

public class EmbeddedScriptTest {

    @Test
    public void testLineContainsRubyFalse() {
        final byte[] bytes = bytes("foo bar baz");
        TestCase.assertFalse(EmbeddedScript.lineContainsRuby(bytes, 0, bytes.length));
    }

    @Test
    public void testLineContainsRubyStart() {
        final byte[] bytes = bytes("ruby foo bar");
        assertTrue(EmbeddedScript.lineContainsRuby(bytes, 0, bytes.length));
    }

    @Test
    public void testLineContainsRubyMiddle() {
        final byte[] bytes = bytes("foo ruby bar");
        assertTrue(EmbeddedScript.lineContainsRuby(bytes, 0, bytes.length));
    }

    @Test
    public void testLineContainsRubyEnd() {
        final byte[] bytes = bytes("foo bar ruby");
        assertTrue(EmbeddedScript.lineContainsRuby(bytes, 0, bytes.length));
    }

    @Test
    public void testLineContainsRubyInBounds() {
        final byte[] bytes = bytes("foo bar rub");
        assertFalse(EmbeddedScript.lineContainsRuby(bytes, 0, bytes.length));
    }

    @Test
    public void testLineContainsRubyBeforeLine() {
        final byte[] bytes = bytes("ruby foo bar");
        assertFalse(EmbeddedScript.lineContainsRuby(bytes, 5, bytes.length - 5));
    }

    @Test
    public void testLineContainsRubyAfterLine() {
        final byte[] bytes = bytes("foo bar ruby");
        assertFalse(EmbeddedScript.lineContainsRuby(bytes("foo bar ruby"), 0, bytes.length - 5));
    }

    @Test
    public void testLineContainsRubyOffset() {
        final byte[] bytes = bytes("foo bar ruby");
        assertTrue(EmbeddedScript.lineContainsRuby(bytes, 4, bytes.length - 4));
    }

    private byte[] bytes(String string) {
        return StringOperations.encodeAsciiBytes(string);
    }

}
