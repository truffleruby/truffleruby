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
package org.truffleruby.language.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.truffleruby.RubyContext;
import org.truffleruby.core.mutex.MutexOperations;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/** Usage:
 * 
 * <pre>
 * <code>
 *  ReentrantLockFreeingMap<String> fileLocks = new ReentrantLockFreeingMap<String>();
 *  while (true) {
 *      final ReentrantLock lock = fileLocks.getLock(key);
 *
 *      if (!fileLocks.lock(callNode, context, lock)) {
 *          continue;
 *      }
 *
 *      try {
 *          doStuff
 *          return true;
 *      } finally {
 *          fileLocks.unlock(key, lock);
 *      }
 *  }
 * </code>
 * </pre>
 */
public final class ReentrantLockFreeingMap<K> {

    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLockFreeingMap() {
    }

    @TruffleBoundary
    public ReentrantLock get(K key) {
        final ReentrantLock currentLock = locks.get(key);
        final ReentrantLock lock;

        if (currentLock == null) {
            ReentrantLock newLock = new ReentrantLock();
            final ReentrantLock wasLock = locks.putIfAbsent(key, newLock);
            lock = (wasLock == null) ? newLock : wasLock;
        } else {
            lock = currentLock;
        }
        return lock;
    }

    @TruffleBoundary
    public boolean isCurrentThreadHoldingLock(K key) {
        final ReentrantLock lock = locks.get(key);
        return lock != null && lock.isHeldByCurrentThread();
    }

    @TruffleBoundary
    public boolean lock(RubyContext context, K key, ReentrantLock lock, Node currentNode) {
        // Also sets status to sleep in MRI
        MutexOperations.lockInternal(context, lock, currentNode);
        // ensure that we are not holding removed lock
        if (lock == locks.get(key)) {
            return true;
        } else {
            lock.unlock();
            return false;
        }
    }

    @TruffleBoundary
    public void unlock(K key, ReentrantLock lock) {
        if (!lock.hasQueuedThreads()) {
            // may remove lock after a thread starts waiting, has to be mitigated by checking
            // correctLock after lock is acquired, if not it has to start over
            locks.remove(key);
        }
        lock.unlock();
    }

}
