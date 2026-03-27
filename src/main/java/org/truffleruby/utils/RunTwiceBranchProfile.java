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
package org.truffleruby.utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.NodeCloneable;

public final class RunTwiceBranchProfile extends NodeCloneable {

    private enum ExecuteCounter {
        NEVER,
        ONCE,
        MANY;

        public ExecuteCounter next() {
            if (this == NEVER) {
                return ONCE;
            } else {
                return MANY;
            }
        }
    }

    @NeverDefault
    public static RunTwiceBranchProfile create() {
        return new RunTwiceBranchProfile();
    }

    @CompilationFinal private ExecuteCounter executeCounter = ExecuteCounter.NEVER;

    public void enter() {
        if (executeCounter != ExecuteCounter.MANY) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeCounter = executeCounter.next();
        }
    }
}
