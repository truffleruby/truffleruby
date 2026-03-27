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
package org.truffleruby.language;

import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.DenyReplace;

/** Warns only if $VERBOSE is true. Corresponds to Kernel#warn(message, uplevel: 1) if $VERBOSE, but in Java with a
 * given SourceSection. */
public final class WarningNode extends WarnNode {

    @NeverDefault
    public static WarningNode create() {
        return new WarningNode();
    }

    @Override
    public boolean shouldWarn() {
        final Object verbose = readVerboseNode.execute();
        return verbose == Boolean.TRUE;
    }

    @DenyReplace
    public static final class UncachedWarningNode extends AbstractUncachedWarnNode {

        public static final UncachedWarningNode INSTANCE = new UncachedWarningNode();

        private UncachedWarningNode() {
        }

        @Override
        public boolean shouldWarn() {
            return coreLibrary().isVerbose();
        }
    }

}
