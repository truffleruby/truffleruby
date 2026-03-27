/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public final class RubySystemExit extends RubyException {

    public int exitStatus;

    public RubySystemExit(
            RubyClass rubyClass,
            Shape shape,
            Object message,
            Backtrace backtrace,
            Object cause,
            int exitStatus) {
        super(rubyClass, shape, message, backtrace, cause);
        this.exitStatus = exitStatus;
    }

    // region Exception interop
    @Override
    @ExportMessage
    public ExceptionType getExceptionType() {
        return ExceptionType.EXIT;
    }

    @ExportMessage
    public int getExceptionExitStatus() {
        return exitStatus;
    }
    // endregion

}
