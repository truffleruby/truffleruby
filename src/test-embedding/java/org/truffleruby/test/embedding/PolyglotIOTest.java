/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.embedding;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

public class PolyglotIOTest extends RubyTest {

    @Test
    public void testPolyglotIn() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayInputStream in = new ByteArrayInputStream("abc".getBytes());

        try (Context context = RubyTest.setupContext(Context.newBuilder()).out(out).in(in).build()) {
            context.eval(Source.create("ruby", "puts STDIN.read(3)"));
        }

        assertEquals("abc\n", out.toString());
    }

    @Test
    public void testPolyglotOut() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (Context context = RubyTest.setupContext(Context.newBuilder()).out(out).build()) {
            context.eval(Source.create("ruby", "puts 'abc'"));
        }

        assertEquals("abc\n", out.toString());
    }

    @Test
    public void testPolyglotErr() {
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        try (Context context = RubyTest.setupContext(Context.newBuilder()).err(err).build()) {
            context.eval(Source.create("ruby", "STDERR.puts 'abc'"));
        }

        assertEquals("abc\n", err.toString());
    }

}
