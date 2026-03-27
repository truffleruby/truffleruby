/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/** Used by Thread#kill and to terminate threads. This does run code in ensure. */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("serial")
public final class KillException extends AbstractTruffleException implements RubyThrowable {

    @TruffleBoundary
    private static RuntimeException javaStacktrace() {
        return new RuntimeException("stacktrace:");
    }

    public KillException(Node location) {
        super("Thread#kill", javaStacktrace(), UNLIMITED_STACK_TRACE, location);
    }

    @ExportMessage
    protected ExceptionType getExceptionType() {
        return ExceptionType.INTERRUPT;
    }
}
