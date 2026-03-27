/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.embedding;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

public abstract class RubyTest {

    public static void assertThrows(Runnable test, Consumer<PolyglotException> exceptionVerifier) {
        PolyglotException e = assertThrows(test, PolyglotException.class);
        assertTrue(e.isGuestException());
        exceptionVerifier.accept(e);
    }

    public static <E> E assertThrows(Runnable test, Class<E> exceptionClass) {
        try {
            test.run();
        } catch (Throwable e) {
            if (!exceptionClass.isInstance(e)) {
                throw e;
            }
            return exceptionClass.cast(e);
        }

        fail("should have thrown");
        throw new Error("unreachable");
    }

    public static Context.Builder setupContext(Context.Builder builder) {
        return builder
                .allowAllAccess(true)
                .option("ruby.basic-ops-inline", "false");
    }

    public static Context createContext() {
        return setupContext(Context.newBuilder()).build();
    }

}
