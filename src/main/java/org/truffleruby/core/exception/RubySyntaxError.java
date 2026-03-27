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
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.library.RubyStringLibrary;

@ExportLibrary(InteropLibrary.class)
public final class RubySyntaxError extends RubyException {

    private final SourceSection sourceLocation; // this is where the syntax error happened in the file being parsed

    public RubySyntaxError(
            RubyClass rubyClass,
            Shape shape,
            Object message,
            Backtrace backtrace,
            Object cause,
            SourceSection sourceLocation) {
        super(rubyClass, shape, message, backtrace, cause);
        this.sourceLocation = sourceLocation;
    }

    // region Exception interop
    @Override
    @ExportMessage
    public ExceptionType getExceptionType() {
        return ExceptionType.PARSE_ERROR;
    }

    @TruffleBoundary
    @ExportMessage
    public boolean isExceptionIncompleteSource() {
        if (RubyStringLibrary.isRubyStringUncached(message)) {
            String messageString = StringOperations.getJavaString(message);
            return messageString.endsWith(" unexpected end-of-file") ||
                    messageString.endsWith(" meets end of file");
        } else {
            return false;
        }
    }

    @ExportMessage
    public boolean hasSourceLocation() {
        return sourceLocation != null;
    }

    @TruffleBoundary
    @ExportMessage
    public SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (sourceLocation != null) {
            return sourceLocation;
        } else {
            throw UnsupportedMessageException.create();
        }
    }
    // endregion

}
