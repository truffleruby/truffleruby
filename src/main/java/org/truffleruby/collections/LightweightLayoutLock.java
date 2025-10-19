/*
 * Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.collections;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/** From the paper "B. Daloze, A. Tal, S. Marr, H. Mossenbock, E. Petrank. Parallelization of Dynamic Languages:
 * Synchronizing Built-in Collections. In Proceedings of the 2018 Conference on Object-Oriented Programming, Systems,
 * Languages, and Applications (OOPSLA), Boston, United States, November 7-9, 2018".
 * <a href="https://dl.acm.org/doi/10.1145/3276478">Freely available here</a>. */
public final class LightweightLayoutLock {

    private static final int WRITE = 1;
    private static final int LAYOUT_CHANGE = 2;

    private final StampedLock baseLock = new StampedLock();
    private boolean rescan = false; // Protected by the baseLock
    private final List<AtomicInteger> threadStates = new ArrayList<>();

    public LightweightLayoutLock() {
    }

    // Read operation

    void startRead() {
        // Nothing, just there for clarity
    }

    public boolean finishRead(AtomicInteger threadState, InlinedConditionProfile fastPath, Node node) {
        // Prevent data reads to float below
        VarHandle.loadLoadFence();

        if (fastPath.profile(node, threadState.get() == 0)) {
            return true; // no Layout Change so data is valid
        }

        // set to idle after Layout Change done
        slowSet(threadState, 0);
        // signal to retry the read
        return false;
    }

    // Write operation

    public void startWrite(AtomicInteger threadState, InlinedConditionProfile fastPath, Node node) {
        if (!fastPath.profile(node, threadState.compareAndSet(0, WRITE))) {
            // Layout Change happened or pending, wait for Layout Change to complete
            slowSet(threadState, WRITE);
        }
    }

    public void finishWrite(AtomicInteger threadState) {
        // subtract since Layout Change might be pending
        threadState.addAndGet(-WRITE);
    }

    // Layout Change operation

    @TruffleBoundary
    public long startLayoutChange() {
        // wait for slowSet or other Layout Change
        long stamp = baseLock.writeLock();

        if (rescan) {
            for (AtomicInteger threadState : this.threadStates) {
                if (threadState.get() != LAYOUT_CHANGE) {
                    // if not already Layout Change
                    threadState.addAndGet(LAYOUT_CHANGE);
                    // wait for current write to complete
                    while (threadState.get() != LAYOUT_CHANGE) {
                        Thread.yield();
                    }
                }
            }

            // rescan only after slowSet
            rescan = false;
        }
        return stamp;
    }

    @TruffleBoundary
    public void finishLayoutChange(long stamp) {
        // Do not reset threadStates to optimize consecutive Layout Changes
        baseLock.unlockWrite(stamp);
    }

    public boolean inLayoutChange() {
        // Does not tell us if this thread is holding the lock in write mode,
        // but StampedLock does not seem to provide that.
        return baseLock.isWriteLocked();
    }

    // Thread registration/unregistration

    @TruffleBoundary
    public AtomicInteger registerThread() {
        AtomicInteger threadState = new AtomicInteger(LAYOUT_CHANGE); // Initial value
        long stamp = baseLock.writeLock();
        try {
            threadStates.add(threadState);
        } finally {
            baseLock.unlockWrite(stamp);
        }
        return threadState;
    }

    @TruffleBoundary
    public void unregisterThread(AtomicInteger threadState) {
        long stamp = baseLock.writeLock();
        try {
            threadStates.remove(threadState);
        } finally {
            baseLock.unlockWrite(stamp);
        }
    }

    // Helpers

    /* Common slow path for read and write operations */
    @TruffleBoundary
    private void slowSet(AtomicInteger threadState, int state) {
        // wait for Layout Change to complete
        long stamp = baseLock.readLock();
        try {
            // subsequent Layout Change must scan thread states
            threadState.set(state);
            rescan = true;
        } finally {
            baseLock.unlockRead(stamp);
        }
    }


}
