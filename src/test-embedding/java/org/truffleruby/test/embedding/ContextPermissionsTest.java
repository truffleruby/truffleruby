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
package org.truffleruby.test.embedding;

import java.lang.foreign.Arena;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContextPermissionsTest {

    @Test
    public void testNoPermissions() throws Throwable {
        try (Context context = Context.newBuilder("ruby").build()) {
            Assert.assertEquals(3, context.eval("ruby", "1 + 2").asInt());

            String option = "Truffle::Boot.get_option('platform-native')";
            Assert.assertEquals(false, context.eval("ruby", option).asBoolean());
            option = "Truffle::Boot.get_option('single-threaded')";
            Assert.assertEquals(true, context.eval("ruby", option).asBoolean());
        }
    }

    @Test
    public void testPointerNoNative() throws Throwable {
        try (Context context = Context.newBuilder("ruby").build()) {
            Assert.assertEquals(3, context.eval("ruby", "1 + 2").asInt());

            Assert.assertTrue(context.eval("ruby", "defined?(Truffle::FFI::Pointer::NULL).nil?").asBoolean());
            assertNativeAccessDenied(() -> context.eval("ruby", "Truffle::FFI::Pointer.allocate"));
            assertNativeAccessDenied(() -> context.eval("ruby", "Truffle::FFI::Pointer.new(4)"));
            assertNativeAccessDenied(() -> context.eval("ruby", "Truffle::FFI::MemoryPointer.new(4)"));
        }
    }

    private static void assertNativeAccessDenied(Runnable action) {
        RubyTest.assertThrows(action, e -> {
            assertEquals("SecurityError", e.getGuestObject().getMetaObject().getMetaQualifiedName());
            assertEquals("native access is not allowed", e.getMessage());
        });
    }

    @Test
    public void testCachedNativeStringRespectsRequestingContext() {
        // Built-in encoding names are immutable Ruby strings owned by the RubyLanguage. Contexts only share a
        // RubyLanguage, and so these strings and their cached native copies, when they share an Engine with engine
        // caching enabled.
        String convert = "Truffle::CExt.string_to_ffi_pointer_inplace(Encoding::UTF_8.name)";
        try (Engine engine = Engine.newBuilder("ruby").allowExperimentalOptions(true)
                .option("ruby.experimental-engine-caching", "true").build();
                Context allowed = Context.newBuilder("ruby").engine(engine).allowNativeAccess(true).build();
                Context denied = Context.newBuilder("ruby").engine(engine).build()) {
            assertTrue(allowed.eval("ruby", convert + ".is_a?(Truffle::FFI::Pointer)").asBoolean());
            assertNativeAccessDenied(() -> denied.eval("ruby", convert));
        }
    }

    @Test
    public void testNativeStringReadersRespectPlatformOption() {
        try (Arena arena = Arena.ofConfined();
                Context context = Context.newBuilder("ruby").allowNativeAccess(true).allowExperimentalOptions(true)
                        .option("ruby.platform-native", "false").build()) {
            long address = arena.allocateFrom("ruby").address();
            assertNativeAccessDenied(() -> context.eval("ruby", "-> p { Truffle::CExt.rb_enc_str_new_native(p, 4, 0) }")
                    .execute(address));
            assertNativeAccessDenied(() -> context.eval("ruby", "-> p { Truffle::CExt.rb_tr_enc_str_new_cstr(p, 0) }")
                    .execute(address));
        }
    }

    @Test
    public void testNativeNoThreads() throws Throwable {
        try (Context context = Context.newBuilder("ruby").allowNativeAccess(true).build()) {
            Assert.assertEquals(3, context.eval("ruby", "1 + 2").asInt());

            Assert.assertEquals(true, context.eval("ruby", "File.directory?('.')").asBoolean());
        }
    }

    @Test
    public void testRequireGem() {
        try (Context context = Context.newBuilder("ruby").allowIO(IOAccess.ALL).allowNativeAccess(true).build()) {
            // NOTE: rake is a bundled gem, so it needs RubyGems to be required
            Assert.assertEquals("Rake", context.eval("ruby", "require 'rake'; Rake.to_s").asString());
        }
    }

    @Test
    public void testRequireCExtension() {
        try (Context context = Context.newBuilder("ruby").allowIO(IOAccess.ALL).allowNativeAccess(true).build()) {
            Assert.assertEquals("Etc", context.eval("ruby", "require 'etc'; Etc.to_s").asString());
        }
    }

    @Test
    public void testRequireCExtensionInMultipleContexts() {
        // Only a single Ruby context at a time in a process can load C extension support
        try (Context context = Context.newBuilder("ruby").allowIO(IOAccess.ALL).allowNativeAccess(true).build()) {
            Assert.assertEquals("Etc", context.eval("ruby", "require 'etc'; Etc.to_s").asString());
            // Date._parse caches Regexp VALUE handles in C static variables of date_core.so (date_parse.c)
            Assert.assertEquals(2003, context.eval("ruby", "require 'date'; Date._parse('2003-01-02')[:year]").asInt());

            try (Context concurrentContext = Context.newBuilder("ruby").allowIO(IOAccess.ALL)
                    .allowNativeAccess(true).build()) {
                RubyTest.assertThrows(
                        () -> concurrentContext.eval("ruby", "require 'etc'"),
                        e -> {
                            assertTrue(e.getMessage(), e.getMessage().contains(
                                    "C extension support can only be loaded in a single Ruby context at a time"));
                            assertEquals("RuntimeError",
                                    e.getGuestObject().getMetaObject().getMetaQualifiedName());
                        });
            }
        }

        // Once the context which loaded C extension support is disposed, its native libraries are dlclose()'d,
        // so a new context can load C extensions again with freshly-initialized static variables
        try (Context context = Context.newBuilder("ruby").allowIO(IOAccess.ALL).allowNativeAccess(true).build()) {
            Assert.assertEquals("Etc", context.eval("ruby", "require 'etc'; Etc.to_s").asString());
            // This would fail with dead VALUE handles of the disposed context if date_core.so was not dlclose()'d
            Assert.assertEquals(2003, context.eval("ruby", "require 'date'; Date._parse('2003-01-02')[:year]").asInt());
        }
    }

    @Test
    public void testThreadsNoNative() throws Throwable {
        try (Context context = Context
                .newBuilder("ruby")
                .allowCreateThread(true)
                .build()) {
            String option = "Truffle::Boot.get_option('single-threaded')";
            Assert.assertEquals(false, context.eval("ruby", option).asBoolean());

            Assert.assertEquals(3, context.eval("ruby", "1 + 2").asInt());

            Assert.assertEquals(7, context.eval("ruby", "Thread.new { 3 + 4 }.value").asInt());
            assertTrue(context.eval("ruby", "Truffle::Debug.multithreaded?").asBoolean());

            RubyTest.assertThrows(
                    () -> context.eval("ruby", "File.stat('.')"),
                    e -> {
                        assertEquals("native access is not allowed", e.getMessage());
                        assertEquals("SecurityError", e.getGuestObject().getMetaObject().getMetaQualifiedName());
                    });
        }
    }

    @Test
    public void testNoThreadsEnforcesSingleThreadedOption() throws Throwable {
        try (Context context = Context
                .newBuilder("ruby")
                .build()) {
            String option = "Truffle::Boot.get_option('single-threaded')";
            Assert.assertEquals(true, context.eval("ruby", option).asBoolean());

            RubyTest.assertThrows(
                    () -> context.eval("ruby", "Thread.new {}.join"),
                    e -> {
                        assertEquals("threads not allowed in single-threaded mode", e.getMessage());
                        assertEquals("SecurityError", e.getGuestObject().getMetaObject().getMetaQualifiedName());
                    });
        }
    }

    @Test
    public void testNoThreads() {
        try (Context context = Context.newBuilder("ruby").allowCreateThread(false).build()) {
            RubyTest.assertThrows(
                    () -> context.eval("ruby", "Thread.new {}.join"),
                    e -> {
                        assertEquals("threads not allowed in single-threaded mode", e.getMessage());
                        assertEquals("SecurityError", e.getGuestObject().getMetaObject().getMetaQualifiedName());
                    });

            RubyTest.assertThrows(
                    () -> context.eval("ruby", "Fiber.new {}.resume"),
                    e -> {
                        assertEquals("fibers not allowed with allowCreateThread(false)", e.getMessage());
                        assertEquals("SecurityError", e.getGuestObject().getMetaObject().getMetaQualifiedName());
                    });

            assertFalse(context.eval("ruby", "Truffle::Debug.multithreaded?").asBoolean());
        }
    }

    @Test
    public void testFiberDoesNotTriggerMultiThreading() {
        try (Context context = Context.newBuilder("ruby").allowCreateThread(true).build()) {
            final Value array = context.eval(
                    "ruby",
                    "a = [1]; f = Fiber.new { a << 3; Fiber.yield; a << 5 }; a << 2; f.resume; a << 4; f.resume");
            assertTrue(array.hasArrayElements());
            assertEquals(5, array.getArraySize());
            for (int i = 0; i < 5; i++) {
                assertEquals(i + 1, array.getArrayElement(i).asInt());
            }

            assertFalse(context.eval("ruby", "Truffle::Debug.multithreaded?").asBoolean());
        }
    }

    @Test
    public void testNestedFiberAndTerminateFiber() {
        try (Context context = Context.newBuilder("ruby").allowCreateThread(true).build()) {
            final Value array = context.eval(
                    "ruby",
                    "a = []; Fiber.new { a << 1; Fiber.new { a << 2; Fiber.yield; unreachable }.resume; a << 3 }.resume");
            assertTrue(array.hasArrayElements());
            assertEquals(3, array.getArraySize());
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, array.getArrayElement(i).asInt());
            }

            assertFalse(context.eval("ruby", "Truffle::Debug.multithreaded?").asBoolean());
        }
    }

}
