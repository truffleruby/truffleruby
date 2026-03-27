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
package org.truffleruby.collections;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.annotations.SuppressFBWarnings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public abstract class ConcurrentOperations {

    /** Replaces {@link ConcurrentHashMap#computeIfAbsent(Object, Function)} as it does not scale. The JDK method takes
     * a monitor for every access if the key is present. See https://bugs.openjdk.java.net/browse/JDK-8161372 which only
     * fixes it in Java 9 if there are no collisions in the bucket.
     * <p>
     * #computeIfAbsent() is used if the key cannot be found with {@link Map#get(Object)}, and therefore this method has
     * the same semantics as the passed map's #computeIfAbsent(). Notably, for ConcurrentHashMap, the lambda is
     * guaranteed to be only executed once per missing key. */
    @TruffleBoundary
    public static <K, V> V getOrCompute(Map<K, V> map, K key, Function<? super K, ? extends V> compute) {
        V value = map.get(key);
        if (value != null) {
            return value;
        } else {
            // Checkstyle: stop
            return map.computeIfAbsent(key, compute);
            // Checkstyle: resume
        }
    }

    /** Similar to {@link Map#replace(Object, Object, Object)} except that the old value can also be null (missing).
     * Returns true if the replace succeeded, or false if it should be retried because <code>map.get(key)</code> is no
     * longer associated to <code>oldValue</code>. */
    public static <K, V> boolean replace(Map<K, V> map, K key, V oldValue, V newValue) {
        return oldValue == null ? map.putIfAbsent(key, newValue) == null : map.replace(key, oldValue, newValue);
    }

    /** {@link Condition#await()} might return and not throw InterruptedException if there was both a signal and an
     * interrupt before the lock could be re-acquired by await(). We always want the InterruptedException if both a
     * signal and an interrupt happen.
     * <hr>
     * {@link Condition#await()} when it sees both a signal and a {@link Thread#interrupt()} has to decide whether it
     * prefers the signal or the interrupt. For a {@link ReentrantLock#newCondition()} condition, the implementation in
     * {@link AbstractQueuedSynchronizer.ConditionObject#await()} sometimes throws InterruptedException and sometimes
     * returns, depending on which happens first. Because await() needs to reacquire the lock, even if the
     * InterruptedException happens before it might still become a situation of "both signal and interrupt". If it
     * prefers the signal and just returns, it does {@code Thread.currentThread().interrupt()} to let us know there was
     * an interrupt too. In any case, if there was any interrupt we want to throw InterruptedException, regardless of
     * what the implementation prefers. */
    @SuppressFBWarnings("WA_AWAIT_NOT_IN_LOOP")
    public static void awaitAndCheckInterrupt(Condition condition) throws InterruptedException {
        // Checkstyle: stop
        condition.await();
        // Checkstyle: resume

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    /** See {@link ConcurrentOperations#awaitAndCheckInterrupt(java.util.concurrent.locks.Condition)} */
    public static boolean awaitAndCheckInterrupt(Condition condition, long time, TimeUnit unit)
            throws InterruptedException {
        // Checkstyle: stop
        boolean value = condition.await(time, unit);
        // Checkstyle: resume

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        return value;
    }

}
