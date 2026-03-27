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
package org.truffleruby.core.format.exceptions;

import com.oracle.truffle.api.nodes.ControlFlowException;
import org.truffleruby.language.control.RubyThrowable;

@SuppressWarnings("serial")
public class FormatException extends ControlFlowException implements RubyThrowable {

    private final String message;

    public FormatException() {
        message = null;
    }

    public FormatException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
