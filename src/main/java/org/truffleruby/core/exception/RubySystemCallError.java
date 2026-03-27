/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
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

import com.oracle.truffle.api.object.Shape;

public final class RubySystemCallError extends RubyException {

    public Object errno;

    public RubySystemCallError(
            RubyClass rubyClass,
            Shape shape,
            Object message,
            Backtrace backtrace,
            Object cause,
            Object errno) {
        super(rubyClass, shape, message, backtrace, cause);
        assert errno != null;
        this.errno = errno;
    }

}
