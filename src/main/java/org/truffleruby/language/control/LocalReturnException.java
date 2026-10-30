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

import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("serial")
public final class LocalReturnException extends RubyControlFlowException {

    private final Object value;
    /** The node of the return expression, used for the :return TracePoint event */
    private final Node returnNode;

    public LocalReturnException(Object value, Node returnNode) {
        this.value = value;
        this.returnNode = returnNode;
    }

    public Object getValue() {
        return value;
    }

    public Node getReturnNode() {
        return returnNode;
    }

}
