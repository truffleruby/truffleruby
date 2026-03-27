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
package org.truffleruby.language.control;

import com.oracle.truffle.api.nodes.Node;

/** Exception sent by the hard Kernel#exit! */
@SuppressWarnings("serial")
public final class ExitException extends TerminationException {

    private final int code;

    public ExitException(int code, Node location) {
        super("Kernel#exit!", location);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
