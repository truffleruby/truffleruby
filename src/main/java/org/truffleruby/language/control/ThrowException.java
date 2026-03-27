/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.nodes.ControlFlowException;

/** Kernel#throw */
@SuppressWarnings("serial")
public final class ThrowException extends ControlFlowException implements RubyThrowable {

    private final Object tag;
    private final Object value;

    public ThrowException(Object tag, Object value) {
        this.tag = tag;
        this.value = value;
    }

    public Object getTag() {
        return tag;
    }

    public Object getValue() {
        return value;
    }

}
