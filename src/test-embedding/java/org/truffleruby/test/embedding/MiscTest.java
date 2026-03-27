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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class MiscTest {

    @Test
    public void testMembersAndStringUnboxing() {
        try (Context context = RubyTest.createContext()) {
            Value result = context.eval("ruby", "Struct.new(:id, :text, :arr).new(42, '42', [1, 42, 3])");
            assertTrue(result.hasMembers());

            int id = result.invokeMember("id").asInt();
            assertEquals(42, id);

            String text = result.invokeMember("text").asString();
            assertEquals("42", text);

            Value array = result.invokeMember("arr");
            assertTrue(array.hasArrayElements());
            assertEquals(3, array.getArraySize());
            assertEquals(42, array.getArrayElement(1).asInt());
        }
    }

    @Test
    public void timeoutExecution() throws Throwable {
        Context context = RubyTest.createContext();

        // schedule a timeout in 1s
        TestingThread thread = new TestingThread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
            context.close(true);
        });

        context.eval("ruby", "init = 1");
        thread.start();
        try {
            String maliciousCode = "while true; end";
            RubyTest.assertThrows(() -> context.eval("ruby", maliciousCode),
                    e -> assertTrue(e.isCancelled()));
        } finally {
            thread.join();
        }
    }

    @Test
    public void testCancellationWithFibers() throws Throwable {
        Context context = RubyTest.createContext();

        // schedule a timeout in 100ms
        TestingThread thread = new TestingThread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
            context.close(true);
        });

        context.eval("ruby", "init = 1");
        thread.start();
        try {
            String code = "unstarted = Fiber.new {}; resumed = Fiber.new { Fiber.yield }.tap(&:resume); sleep 1";
            RubyTest.assertThrows(() -> context.eval("ruby", code),
                    e -> assertTrue(e.isCancelled()));
        } finally {
            thread.join();
        }
    }

    @Test
    public void testEvalFromEmbedderThreadSingleThreaded() throws Throwable {
        final String codeDependingOnCurrentThread = "Thread.current.object_id";

        try (Context context = RubyTest.createContext()) {
            long thread1 = context.eval("ruby", codeDependingOnCurrentThread).asLong();

            TestingThread thread = new TestingThread(() -> {
                long thread2 = context.eval("ruby", codeDependingOnCurrentThread).asLong();
                assertNotEquals(thread1, thread2);
            });
            thread.start();
            thread.join();
        }
    }

    @Test
    public void testFiberFromEmbedderThread() throws Throwable {
        try (Context context = RubyTest.createContext()) {
            context.eval("ruby", ":init");

            TestingThread thread = new TestingThread(() -> {
                int value = context.eval("ruby", "Fiber.new { 6 * 7 }.resume").asInt();
                assertEquals(42, value);
            });
            thread.start();
            thread.join();
        }
    }

    @Test
    public void testEmbedderThreadRubyThreadInitialization() throws Throwable {
        try (Context context = Context.newBuilder("ruby").allowCreateThread(true).build()) {
            context.initialize("ruby");
            TestingThread thread = new TestingThread(() -> {
                // Run code that requires the Ruby Thread object to be initialized
                Value recursiveArray = context.eval("ruby", "a = [0]; a[0] = a; a.inspect"); // Access @recursive_objects
                assertEquals("[[...]]", recursiveArray.asString());
                assertTrue(context.eval("ruby", "Thread.current.thread_variable_get('foo')").isNull());
                assertTrue(context.eval("ruby", "rand").fitsInDouble());
            });
            thread.start();
            thread.join();
        }
    }

    @Test
    public void testForeignThread() throws Throwable {
        // Tests thread initialization and finalization for a foreign thread and an embedder thread
        try (Context context = Context.newBuilder("ruby").allowCreateThread(true).build()) {
            context.eval("ruby", "$queue = Queue.new");
            assertEquals(context.eval("ruby", "Thread.main"), context.eval("ruby", "Thread.current"));
            assertTrue(context.eval("ruby", "Thread.current").toString().contains("main"));
            assertFalse(context.eval("ruby", "Thread.current").toString().contains("<foreign thread>"));

            Runnable polyglotThreadBody = () -> {
                context.eval("ruby", "$queue.pop");
                assertTrue(context.eval("ruby", "Thread.current").toString().contains("<foreign thread>"));
                assertTrue(context.eval("ruby", "Thread.current.equal?(Thread.current)").asBoolean());
            };
            Thread polyglotThread = context
                    .eval("ruby", "Truffle::Debug")
                    .invokeMember("create_polyglot_thread", polyglotThreadBody)
                    .asHostObject();

            TestingThread embedderThread = new TestingThread(() -> {
                context.eval("ruby", "$queue.pop");
                assertTrue(context.eval("ruby", "Thread.current").toString().contains("<foreign thread>"));
            });

            polyglotThread.start();
            embedderThread.start();

            context.eval("ruby", "$queue << 1");
            context.eval("ruby", "$queue << 2");

            polyglotThread.join(); // causes disposeThread(polyglotThread) to run on polyglotThread
            embedderThread.join();
        }
    }

    @Test
    public void testEmbedderThreadContextClosedOnOtherThread() throws Throwable {
        try (Context context = Context.newBuilder("ruby").allowCreateThread(true).build()) {
            TestingThread thread = new TestingThread(() -> {
                assertEquals(context.eval("ruby", "Thread.current"), context.eval("ruby", "Thread.main"));
                assertEquals(42, context.eval("ruby", "6 * 7").asInt());
            });
            thread.start();
            thread.join();
        }
    }

}
