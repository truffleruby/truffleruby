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
package org.truffleruby.language.arguments;

import org.truffleruby.language.threadlocal.SpecialVariableStorage;

public final class ReadCallerVariablesIfAvailableNode extends ReadCallerVariablesNode {

    public static ReadCallerVariablesIfAvailableNode create() {
        return new ReadCallerVariablesIfAvailableNode();
    }

    @Override
    protected SpecialVariableStorage getCallerSpecialVariables() {
        return null;
    }
}
