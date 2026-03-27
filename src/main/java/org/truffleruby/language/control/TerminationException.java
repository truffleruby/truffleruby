/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("serial")
public abstract class TerminationException extends RuntimeException implements RubyThrowable {

    // To help debugging
    public final Node location;

    public TerminationException(String message, Node location) {
        super(message);
        this.location = location;
    }

}
