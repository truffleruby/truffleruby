/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.RubyContext;
import org.truffleruby.core.exception.RubyException;

@SuppressWarnings("serial")
public final class DeferredRaiseException extends Exception {

    public final ExceptionGetter exceptionGetter;

    public DeferredRaiseException(ExceptionGetter exceptionGetter) {
        this.exceptionGetter = exceptionGetter;
    }

    @TruffleBoundary
    public RaiseException getException(RubyContext context) {
        return new RaiseException(context, exceptionGetter.getException(context));
    }

    public interface ExceptionGetter {
        RubyException getException(RubyContext context);
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}
