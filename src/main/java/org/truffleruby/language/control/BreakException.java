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


@SuppressWarnings("serial")
public final class BreakException extends RubyControlFlowException {

    private final BreakID breakID;
    private final Object result;

    public BreakException(BreakID breakID, Object result) {
        this.breakID = breakID;
        this.result = result;
    }

    public BreakID getBreakID() {
        return breakID;
    }

    public Object getResult() {
        return result;
    }

}
