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

/** If the Thread dies from an exception, rethrow it on join(). This Thread can then be used for assert*(). */
public class TestingThread {

    private final Thread thread;
    private Throwable throwable;

    public TestingThread(Runnable runnable) {
        thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                throwable = t;
                throw t;
            }
        });
    }

    public void start() {
        thread.start();
    }

    public void join() throws Throwable {
        thread.join();

        if (throwable != null) {
            throw throwable;
        }
    }

}
