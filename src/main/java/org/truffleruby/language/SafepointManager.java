/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.oracle.truffle.api.TruffleSafepoint;
import org.truffleruby.RubyContext;
import org.truffleruby.core.thread.RubyThread;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

public final class SafepointManager {

    private final RubyContext context;

    public SafepointManager(RubyContext context) {
        this.context = context;
    }

    /** Variant for a single thread */
    @TruffleBoundary
    public void pauseRubyThreadAndExecute(Node currentNode, SafepointAction action) {
        final RubyThread rubyThread = action.getTargetThread();

        if (context.getEnv().getContext().isEntered() && context.getLanguageSlow().getCurrentThread() == rubyThread) {
            if (context.getLanguageSlow().getCurrentFiber() != rubyThread.getCurrentFiber()) {
                throw CompilerDirectives.shouldNotReachHere(
                        "The currently executing Java thread does not correspond to the currently active fiber for the current Ruby thread");
            }
            // fast path if we are already the right thread
            action.run(rubyThread, currentNode);
        } else {
            pauseAllThreadsAndExecute(currentNode, action);
        }
    }

    @TruffleBoundary
    public void pauseAllThreadsAndExecute(Node currentNode, SafepointAction action) {
        // TODO: Potentially can narrow threads for some cases like pauseRubyThreadAndExecute(), but not clear how to find current Fiber of another Ruby Thread
        // TODO: For pauseRubyThreadAndExecute(), we would need to retry for new threads if it was executed on no threads to handle new Fibers of that Thread
        final Future<Void> future = context.getEnv().submitThreadLocal(null, action);

        if (action.isSynchronous()) {
            TruffleSafepoint.setBlockedThreadInterruptible(currentNode, f -> {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }, future);
        }
    }

}
