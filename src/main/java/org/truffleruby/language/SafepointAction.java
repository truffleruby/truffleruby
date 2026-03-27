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
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.thread.RubyThread;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A action to run in a guest-language safepoint. Actions with side-effects should usually be asynchronous. */
public abstract class SafepointAction extends ThreadLocalAction {

    private static final SafepointPredicate CURRENT_FIBER_OF_THREAD = //
            (context, thread, action) -> thread == action.getTargetThread() &&
                    context.getLanguageSlow().getCurrentFiber() == action.getTargetThread().getCurrentFiber();

    private final boolean publicSynchronous;
    private final String reason;
    private final SafepointPredicate filter;
    private final RubyThread targetThread;
    private final AtomicBoolean executed;

    public SafepointAction(String reason, RubyThread targetThread, boolean hasSideEffects, boolean synchronous) {
        this(reason, CURRENT_FIBER_OF_THREAD, hasSideEffects, synchronous, targetThread);
    }

    public SafepointAction(String reason, SafepointPredicate filter, boolean hasSideEffects, boolean synchronous) {
        this(reason, filter, hasSideEffects, synchronous, null);
    }

    private SafepointAction(
            String reason,
            SafepointPredicate filter,
            boolean hasSideEffects,
            boolean synchronous,
            RubyThread targetThread) {
        super(hasSideEffects, synchronous);
        this.publicSynchronous = synchronous;
        this.reason = reason;
        this.filter = filter;
        this.targetThread = targetThread;
        this.executed = targetThread != null ? new AtomicBoolean(false) : null;
    }

    public abstract void run(RubyThread rubyThread, Node currentNode);

    @Override
    protected final void perform(Access access) {
        if (Thread.currentThread() != access.getThread()) {
            throw CompilerDirectives.shouldNotReachHere(
                    "safepoint action for " + access.getThread() + " executed on other thread: " +
                            Thread.currentThread());
        }

        final RubyLanguage language = RubyLanguage.getCurrentLanguage();
        final RubyContext context = RubyLanguage.getCurrentContext();

        final RubyThread rubyThread = language.getCurrentThread();
        final Node node = access.getLocation();
        if (filter.test(context, rubyThread, this)) {
            // actions which need to run on the current Fiber of a Ruby Thread might execute multiple times, because
            // they can be scheduled at different times and the current fiber of that thread might change in between.
            // So ensure we only execute the action once.
            if (executed == null || executed.compareAndSet(false, true)) {
                run(rubyThread, node);
            }

            if (filter == SafepointPredicate.ALL_THREADS_AND_FIBERS) {
                final RubyFiber currentFiber = rubyThread.getCurrentFiber();
                for (RubyFiber fiber : rubyThread.runningFibers) {
                    if (fiber != currentFiber) {
                        context.fiberManager.safepoint(currentFiber, fiber, this, node);
                    }
                }
            }
        }

    }

    public boolean isSynchronous() {
        return publicSynchronous;
    }

    public RubyThread getTargetThread() {
        return Objects.requireNonNull(targetThread);
    }

    @Override
    public String toString() {
        return reason + " " + Integer.toHexString(System.identityHashCode(this));
    }

}

