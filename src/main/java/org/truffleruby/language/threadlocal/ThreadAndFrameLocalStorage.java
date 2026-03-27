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
package org.truffleruby.language.threadlocal;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.language.Nil;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ThreadAndFrameLocalStorage {

    // We store a thread id rather than the thread itself. Although
    // this id can theoretically be reused the implementation simply
    // increments a long and has 48 bits that it can use.
    private final long originalThreadId;
    private Object originalThreadValue;
    private volatile ThreadLocal<Object> otherThreadValues = null;

    public ThreadAndFrameLocalStorage(RubyContext context) {
        // Cannot store a Thread id while pre-initializing
        originalThreadId = context.isPreInitializing() ? 0 : Thread.currentThread().threadId();
        originalThreadValue = Nil.INSTANCE;
    }

    public Object get(Node node, InlinedConditionProfile sameThreadProfile) {
        if (sameThreadProfile.profile(node, Thread.currentThread().threadId() == originalThreadId)) {
            return originalThreadValue;
        } else {
            return fallbackGet();
        }
    }

    @TruffleBoundary
    private ThreadLocal<Object> getOtherThreadValues() {
        if (otherThreadValues != null) {
            return otherThreadValues;
        } else {
            synchronized (this) {
                if (otherThreadValues != null) {
                    return otherThreadValues;
                } else {
                    otherThreadValues = ThreadLocal.withInitial(() -> Nil.INSTANCE);
                    return otherThreadValues;
                }
            }
        }
    }

    @TruffleBoundary
    private Object fallbackGet() {
        return getOtherThreadValues().get();
    }

    public void set(Node node, Object value, InlinedConditionProfile sameThreadProfile) {
        if (sameThreadProfile.profile(node, Thread.currentThread().threadId() == originalThreadId)) {
            originalThreadValue = value;
        } else {
            fallbackSet(value);
        }
    }

    @TruffleBoundary
    private void fallbackSet(Object value) {
        getOtherThreadValues().set(value);
    }

}
