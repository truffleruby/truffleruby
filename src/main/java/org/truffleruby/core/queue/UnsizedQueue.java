/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.collections.ConcurrentOperations;

public final class UnsizedQueue {

    private final ReentrantLock lock;
    private final Condition canTake;

    private Item addEnd;
    private Item takeEnd;
    private int size;
    private boolean closed;

    public static final Object CLOSED = new Object();

    @TruffleBoundary
    public UnsizedQueue() {
        this.lock = new ReentrantLock();
        this.canTake = lock.newCondition();
    }

    @TruffleBoundary
    public boolean add(Object item) {
        lock.lock();

        try {
            if (closed) {
                return false;
            }

            doAdd(item);

            return true;
        } finally {
            lock.unlock();
        }
    }

    private void doAdd(Object item) {
        final Item newItem = new Item(item);
        if (addEnd != null) {
            addEnd.setNextToTake(newItem);
        }
        addEnd = newItem;
        if (takeEnd == null) {
            takeEnd = addEnd;
        }
        size++;
        canTake.signal();
    }

    @TruffleBoundary
    public boolean addAll(Object[] items) {
        lock.lock();

        try {
            if (closed) {
                return false;
            }

            for (Object i : items) {
                doAdd(i);
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public Object take() throws InterruptedException {
        lock.lock();

        try {
            while (takeEnd == null) {
                if (closed) {
                    return CLOSED;
                }

                ConcurrentOperations.awaitAndCheckInterrupt(canTake);
            }

            return doTake();
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public Object poll(long timeoutMilliseconds) throws InterruptedException {
        lock.lock();

        try {
            final long deadline = System.currentTimeMillis() + timeoutMilliseconds;

            while (takeEnd == null) {
                final long currentTimeout = deadline - System.currentTimeMillis();
                final boolean signalled = ConcurrentOperations.awaitAndCheckInterrupt(canTake, currentTimeout,
                        TimeUnit.MILLISECONDS);

                if (!signalled) { // Timed out.
                    return null;
                }
            }

            if (closed) {
                return CLOSED;
            }

            return doTake();
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public Object poll() {
        lock.lock();

        try {
            if (takeEnd == null) {
                return null;
            } else {
                return doTake();
            }
        } finally {
            lock.unlock();
        }
    }

    private Object doTake() {
        final Object item = takeEnd.getItem();
        final Item nextToTake = takeEnd.getNextToTake();
        takeEnd.clearNextReference();
        takeEnd = nextToTake;
        if (takeEnd == null) {
            addEnd = null;
        }
        size--;
        return item;
    }

    @TruffleBoundary
    public int size() {
        lock.lock();

        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @TruffleBoundary
    public void clear() {
        lock.lock();

        try {
            while (takeEnd != null) {
                final Item next = takeEnd.getNextToTake();
                takeEnd.clearNextReference();
                takeEnd = next;
            }
            addEnd = null;
            takeEnd = null;
            size = 0;
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public int getNumberWaitingToTake() {
        lock.lock();

        try {
            return lock.getWaitQueueLength(canTake);
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public Collection<Object> getContents() {
        final Collection<Object> objects = new ArrayList<>();

        lock.lock();
        try {
            Item iterator = takeEnd;

            while (iterator != null) {
                objects.add(iterator.getItem());
                iterator = iterator.getNextToTake();
            }
        } finally {
            lock.unlock();
        }

        return objects;
    }

    @TruffleBoundary
    public void close() {
        lock.lock();

        try {
            closed = true;
            canTake.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @TruffleBoundary
    public boolean isClosed() {
        lock.lock();

        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    private static final class Item {

        private final Object item;
        private Item nextToTake;

        public Item(Object item) {
            this.item = Objects.requireNonNull(item);
        }

        public Object getItem() {
            return item;
        }

        public void setNextToTake(Item nextToTake) {
            this.nextToTake = nextToTake;
        }

        public Item getNextToTake() {
            return nextToTake;
        }

        public void clearNextReference() {
            nextToTake = null;
        }

    }

}
