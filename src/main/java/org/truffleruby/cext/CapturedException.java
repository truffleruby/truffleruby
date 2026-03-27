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
package org.truffleruby.cext;

import com.oracle.truffle.api.interop.TruffleObject;

public final class CapturedException implements TruffleObject {

    private final Throwable exception;

    public CapturedException(Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }

}
