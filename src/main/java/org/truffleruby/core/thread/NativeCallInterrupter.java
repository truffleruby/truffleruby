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
package org.truffleruby.core.thread;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import org.truffleruby.signal.LibRubySignal;

import java.util.Timer;
import java.util.TimerTask;

final class NativeCallInterrupter implements TruffleSafepoint.Interrupter {

    private final Timer timer;
    private final long threadID;
    private Task currentTask = null;

    NativeCallInterrupter(Timer timer, long threadID) {
        this.timer = timer;
        this.threadID = threadID;
    }

    @Override
    public void interrupt(Thread thread) {
        synchronized (this) {
            final Task previousTask = this.currentTask;
            if (previousTask != null) {
                previousTask.cancel();
            }

            final Task task = new Task(threadID);
            this.currentTask = task;
            timer.schedule(task, 0, Task.PERIOD);
        }
    }

    @Override
    public void resetInterrupted() {
        synchronized (this) {
            final Task task = this.currentTask;
            if (task != null) {
                task.cancel();
            }
        }
    }

    // Try every 100ms for 50 times maximum (5 seconds)
    static final class Task extends TimerTask {
        private static final int PERIOD = 100; // milliseconds
        private static final int MAX_TIME = 5000; // milliseconds
        private static final int MAX_EXECUTIONS = MAX_TIME / PERIOD;

        private final long threadID;
        private int executed = 0;
        private boolean cancelled = false;

        Task(long threadID) {
            this.threadID = threadID;
        }

        @Override
        public boolean cancel() {
            // Ensure this task does not run later when this method returns.
            // If it was running at the time of cancel(), wait for that.
            synchronized (this) {
                cancelled = true;
                return super.cancel();
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }

                if (executed < MAX_EXECUTIONS) {
                    executed++;
                    int result = LibRubySignal.sendSIGVTALRMToThread(threadID);
                    if (result != 0) {
                        throw CompilerDirectives.shouldNotReachHere(
                                String.format("pthread_kill(%x, SIGVTALRM) failed with result=%d", threadID, result));
                    }
                } else {
                    cancel();
                }
            }
        }
    }

}
