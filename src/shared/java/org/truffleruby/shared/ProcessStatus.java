/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.shared;

/** Similar to Ruby's Process::Status and waitpid(3)'s wstatus, this helper class represents either an exit code or a
 * terminating signal. We encode it as a single int because this needs to be passed from the context to the launcher. */
public abstract class ProcessStatus {

    private static final int SIGNAL_OFFSET = 1_000_000;

    public static int exitCode(int exitCode) {
        // The max exit code is 255, `ruby -e 'exit -1'; echo $?` => 255.
        // We modulo the exitCode to ensure there is no overlap between encoded exit codes and signal numbers.
        return Integer.remainderUnsigned(exitCode, 256);
    }

    public static int signal(int signo) {
        return SIGNAL_OFFSET + signo;
    }

    public static boolean isSignal(int processStatus) {
        return processStatus > SIGNAL_OFFSET;
    }

    public static int toSignal(int processStatus) {
        return processStatus - SIGNAL_OFFSET;
    }

}
