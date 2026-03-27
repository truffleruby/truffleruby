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
package org.truffleruby.core.mutex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class MutexOperations {

    @TruffleBoundary
    public static void lock(RubyContext context, ReentrantLock lock, RubyThread thread, RubyNode currentNode) {
        lockInternal(context, lock, currentNode);
        thread.ownedLocks.add(lock);
    }

    @TruffleBoundary
    public static boolean tryLock(ReentrantLock lock, RubyThread thread) {
        if (lock.tryLock()) {
            thread.ownedLocks.add(lock);
            return true;
        } else {
            return false;
        }
    }

    @TruffleBoundary
    public static void lockInternal(RubyContext context, ReentrantLock lock, Node currentNode) {
        if (lock.tryLock()) {
            return;
        }

        context.getThreadManager().runUntilResult(currentNode, () -> {
            lock.lockInterruptibly();
            return ThreadManager.BlockingAction.SUCCESS;
        });

        if (!lock.isHeldByCurrentThread()) {
            throw CompilerDirectives.shouldNotReachHere("lockInternal() did not acquire lock as expected");
        }
    }

    @TruffleBoundary
    protected static void lockEvenWithExceptions(
            RubyContext context, ReentrantLock lock, RubyThread thread, Node currentNode) {
        // We need to re-lock this lock after a Mutex#sleep, no matter what, even if another thread throw us an exception.
        // Yet, we also need to allow safepoints to happen otherwise the thread that could unlock could be blocked.
        internalLockEvenWithException(context, lock, currentNode);
        thread.ownedLocks.add(lock);
    }

    @TruffleBoundary
    public static void internalLockEvenWithException(RubyContext context, ReentrantLock lock, Node currentNode) {
        if (lock.isHeldByCurrentThread()) {
            throw new RaiseException(context, context.getCoreExceptions().threadErrorRecursiveLocking(currentNode));
        }

        if (lock.tryLock()) {
            return;
        }

        Throwable throwable = null;
        try {
            while (true) {
                try {
                    context.getThreadManager().runUntilResult(currentNode, () -> {
                        lock.lockInterruptibly();
                        return ThreadManager.BlockingAction.SUCCESS;
                    });
                    break;
                } catch (Throwable t) {
                    throwable = t;
                }
            }
        } finally {
            if (!lock.isHeldByCurrentThread()) {
                throw CompilerDirectives.shouldNotReachHere("the lock could not be reacquired", throwable);
            }
        }

        if (throwable != null) {
            ExceptionOperations.rethrow(throwable);
        }
    }

    /** Should be preceded with MutexOperations.checkOwnedMutex call to throw a proper exception */
    @TruffleBoundary
    public static void unlock(ReentrantLock lock, RubyThread thread) {
        unlockInternal(lock);
        thread.ownedLocks.remove(lock);
    }

    @TruffleBoundary
    public static void unlockInternal(ReentrantLock lock) {
        if (!lock.isHeldByCurrentThread()) {
            throw CompilerDirectives.shouldNotReachHere("the lock was not held when calling unlockInternal()");
        }
        lock.unlock();
    }

    @TruffleBoundary
    public static ReentrantLock newReentrantLock() {
        return new ReentrantLock();
    }

    @TruffleBoundary
    public static Condition newCondition(ReentrantLock lock) {
        return lock.newCondition();
    }

    public static void checkOwnedMutex(RubyContext context, ReentrantLock lock, RubyNode currentNode,
            InlinedBranchProfile errorProfile) {
        if (!lock.isHeldByCurrentThread()) {
            errorProfile.enter(currentNode);
            if (!lock.isLocked()) {
                throw new RaiseException(context, context.getCoreExceptions().threadErrorUnlockNotLocked(currentNode));
            } else {
                throw new RaiseException(context, context.getCoreExceptions().threadErrorAlreadyLocked(currentNode));
            }
        }
    }

}
