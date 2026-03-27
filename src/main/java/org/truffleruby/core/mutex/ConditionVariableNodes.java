/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.mutex;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.collections.Memo;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "ConditionVariable", isClass = true)
public abstract class ConditionVariableNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyConditionVariable allocate(RubyClass rubyClass) {
            // condLock is only held for a short number of non-blocking instructions,
            // so there is no need to poll for safepoints while locking it.
            // It is an internal lock and so locking should be done with condLock.lock()
            // to avoid changing the Ruby Thread status and consume Java thread interrupts.
            final ReentrantLock condLock = MutexOperations.newReentrantLock();
            final Condition condition = MutexOperations.newCondition(condLock);
            final Shape shape = getLanguage().conditionVariableShape;
            final RubyConditionVariable instance = new RubyConditionVariable(rubyClass, shape, condLock, condition);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @Primitive(name = "condition_variable_wait")
    public abstract static class WaitNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyConditionVariable noTimeout(RubyConditionVariable condVar, RubyMutex mutex, Nil timeout,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            final RubyThread thread = getLanguage().getCurrentThread();
            final ReentrantLock mutexLock = mutex.lock;

            MutexOperations.checkOwnedMutex(getContext(), mutexLock, this, errorProfile);
            waitInternal(condVar, mutexLock, thread, -1);
            return condVar;
        }

        @Specialization
        RubyConditionVariable withTimeout(RubyConditionVariable condVar, RubyMutex mutex, long timeout,
                @Cached @Shared InlinedBranchProfile errorProfile) {
            final RubyThread thread = getLanguage().getCurrentThread();
            final ReentrantLock mutexLock = mutex.lock;

            MutexOperations.checkOwnedMutex(getContext(), mutexLock, this, errorProfile);
            waitInternal(condVar, mutexLock, thread, timeout);
            return condVar;
        }

        @SuppressFBWarnings({ "UL_UNRELEASED_LOCK", "UL_UNRELEASED_LOCK_EXCEPTION_PATH" })
        @TruffleBoundary
        private void waitInternal(RubyConditionVariable conditionVariable, ReentrantLock mutexLock,
                RubyThread thread, long durationInNanos) {
            final ReentrantLock condLock = conditionVariable.lock;
            final Condition condition = conditionVariable.condition;
            final long endNanoTime;
            if (durationInNanos >= 0) {
                endNanoTime = System.nanoTime() + durationInNanos;
            } else {
                endNanoTime = 0;
            }

            // Clear the wakeUp flag, following Ruby semantics:
            // it should only be considered if we are inside Mutex#sleep when Thread#{run,wakeup} is called.
            thread.wakeUp.set(false);

            // condLock must be locked before unlocking mutexLock, to avoid losing potential signals.
            // We must not change the Ruby Thread status and not consume a Java thread interrupt while locking condLock.
            // If there is an interrupt, it should be consumed by Condition#await() and the Ruby Thread sleep status
            // must imply being ready to be interrupted by Thread#{run,wakeup}.
            condLock.lock();
            int holdCount = 0; // can be > 1 for MonitorMixin
            try {
                while (mutexLock.isHeldByCurrentThread()) {
                    mutexLock.unlock();
                    holdCount++;
                }

                conditionVariable.waiters++;
                try {
                    awaitSignal(conditionVariable, thread, durationInNanos, condLock, condition, endNanoTime);
                } catch (Error | RuntimeException e) {
                    /* Consume a signal if one was waiting. We do this because the error may have occurred while we were
                     * waiting, or at some point after exiting a safepoint that throws an exception and another thread
                     * has attempted to signal us. It is valid for us to consume this signal because we are still marked
                     * as waiting for it. */
                    consumeSignal(conditionVariable);
                    throw e;
                } finally {
                    conditionVariable.waiters--;
                }
            } finally {
                condLock.unlock();

                MutexOperations.internalLockEvenWithException(getContext(), mutexLock, this);
                if (holdCount > 1) {
                    // We know we already hold the lock, so we can skip the rest of the logic at this point.
                    for (int i = 1; i < holdCount; i++) {
                        mutexLock.lock();
                    }
                }
            }
        }

        // @SuppressFBWarnings(value = { "UL", "RV" })
        private void awaitSignal(RubyConditionVariable self, RubyThread thread, long durationInNanos,
                ReentrantLock condLock, Condition condition, long endNanoTime) {
            final Memo<Boolean> done = new Memo<>(false);

            getContext().getThreadManager().runUntilResult(this, () -> {
                if (done.get()) {
                    return BlockingAction.SUCCESS;
                }

                while (true) {
                    /* We must not consumeSignal() here, as we should only consume a signal after being awaken by
                     * condition.signal() or condition.signalAll(). Otherwise, ConditionVariable#signal might
                     * condition.signal() a waiting thread, and then if the current thread calls ConditionVariable#wait
                     * before the waiting thread awakes, we might steal that waiting thread's signal with
                     * consumeSignal(). So, we must await() first. spec/ruby/library/conditionvariable/signal_spec.rb is
                     * a good spec for this (run with repeats = 10000). */
                    if (durationInNanos >= 0) {
                        final long currentTime = System.nanoTime();
                        if (currentTime >= endNanoTime) {
                            return BlockingAction.SUCCESS;
                        }

                        /** Condition#await() can only exit (return or throw) after the associated ReentrantLock is
                         * re-acquired. Even if it's interrupted, the InterruptedException is "stuck inside" until that
                         * ReentrantLock is re-acquired. So the ReentrantLock we use here must be a ReentrantLock used
                         * only for that Condition/RubyConditionVariable and nothing else. Specifically it must not be a
                         * ReentrantLock exposed to Ruby, e.g., via a Ruby Mutex, as that could be held forever by
                         * another Ruby thread (which did {code mutex.lock} after the #wait), and we would never be able
                         * to interrupt both threads at the same time for a synchronous ThreadLocalAction). */
                        ConcurrentOperations.awaitAndCheckInterrupt(condition, endNanoTime - currentTime,
                                TimeUnit.NANOSECONDS);
                    } else {
                        ConcurrentOperations.awaitAndCheckInterrupt(condition);
                    }
                    if (consumeSignal(self)) {
                        return BlockingAction.SUCCESS;
                    }
                }
            }, condLock::unlock, (t) -> {
                /* Working with ConditionVariables is tricky because of safepoints. To call await or signal on a
                 * condition variable we must hold the lock, and that lock is released when we start waiting. However if
                 * the wait is interrupted then the lock will be reacquired before control returns to us. If we are
                 * interrupted for a safepoint then we must release the lock so that all threads can enter the
                 * safepoint, and acquire it again before resuming waiting. */
                condLock.lock();

                // Thread#{wakeup,run} might have woken us. In that a case, no signal is consumed.
                if (thread.wakeUp.getAndSet(false)) {
                    done.set(true);
                    return;
                }

                // Check if a signal are available now, since another thread might have used
                // ConditionVariable#signal while we released condLock to check for safepoints.
                if (consumeSignal(self)) {
                    done.set(true);
                }
            });
        }

        private boolean consumeSignal(RubyConditionVariable self) {
            if (self.signals > 0) {
                self.signals--;
                return true;
            }
            return false;
        }

    }

    @CoreMethod(names = "signal")
    public abstract static class SignalNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyConditionVariable signal(RubyConditionVariable self) {
            final ReentrantLock condLock = self.lock;
            final Condition condition = self.condition;

            condLock.lock();
            try {
                if (self.waiters > 0) {
                    self.signals++;
                    condition.signal();
                }
            } finally {
                condLock.unlock();
            }

            return self;
        }
    }

    @CoreMethod(names = "broadcast")
    public abstract static class BroadCastNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyConditionVariable broadcast(RubyConditionVariable self) {
            final ReentrantLock condLock = self.lock;
            final Condition condition = self.condition;

            condLock.lock();
            try {
                if (self.waiters > 0) {
                    self.signals += self.waiters;
                    condition.signalAll();
                }
            } finally {
                condLock.unlock();
            }

            return self;
        }
    }
}
