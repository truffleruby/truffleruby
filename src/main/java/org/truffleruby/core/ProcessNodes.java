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
package org.truffleruby.core;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.symbol.RubySymbol;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;

import sun.misc.Signal;

import java.time.Instant;

@CoreModule(value = "Process")
public abstract class ProcessNodes {

    @CoreMethod(names = "argv0", onSingleton = true)
    public abstract static class Argv0Node extends CoreMethodArrayArgumentsNode {

        @Specialization
        ImmutableRubyString argv0() {
            return getContext().getMainScriptName();
        }

    }

    @Primitive(name = "process_time_nanotime")
    public abstract static class ProcessTimeNanoTimeNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long nanoTime() {
            return System.nanoTime();
        }

    }

    @Primitive(name = "process_time_instant")
    public abstract static class ProcessTimeInstantNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long instant() {
            final Instant now = Instant.now();
            return (now.getEpochSecond() * 1_000_000_000L) + now.getNano();
        }

    }

    @Primitive(name = "process_kill_raise")
    public abstract static class ProcessKillRaiseNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        int raise(RubySymbol signalName) {
            final Signal signal = new Signal(signalName.getString());
            try {
                Signal.raise(signal);
            } catch (IllegalArgumentException e) {
                // Java does not know the handler, fallback to using kill()
                return -1;
            }
            return 1;
        }

    }

}
