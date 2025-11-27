/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import org.truffleruby.utils.Utils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

public final class ConcurrentEntry {

    private static final VarHandle NEXT_IN_LOOKUP_HANDLE = Utils.getVarHandle(MethodHandles.lookup(),
            ConcurrentEntry.class, "nextInLookup", ConcurrentEntry.class);
    private static final VarHandle PREV_IN_SEQUENCE_HANDLE = Utils.getVarHandle(MethodHandles.lookup(),
            ConcurrentEntry.class, "previousInSequence", ConcurrentEntry.class);
    private static final VarHandle NEXT_IN_SEQUENCE_HANDLE = Utils.getVarHandle(MethodHandles.lookup(),
            ConcurrentEntry.class, "nextInSequence", ConcurrentEntry.class);

    private final int hashed;
    private final Object key;
    private volatile Object value;

    private volatile ConcurrentEntry nextInLookup;

    private volatile ConcurrentEntry previousInSequence;
    private volatile ConcurrentEntry nextInSequence;

    private final boolean removed;
    private final boolean lock;

    private volatile boolean published;

    public ConcurrentEntry(int hashed, Object key, Object value, boolean published) {
        this.hashed = hashed;
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
        this.removed = false;
        this.lock = false;
        this.published = published;
    }

    public ConcurrentEntry(
            boolean removed,
            boolean lock,
            ConcurrentEntry prevInSequence,
            ConcurrentEntry nextInSequence,
            ConcurrentEntry nextInLookup) {
        this.hashed = 0;
        this.key = null;
        this.value = null;
        this.previousInSequence = prevInSequence;
        this.nextInSequence = nextInSequence;
        this.nextInLookup = nextInLookup;
        this.removed = removed;
        this.lock = lock;
        this.published = false;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isLock() {
        return lock;
    }

    public boolean isSentinel() {
        return key == null && !lock && !removed;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public boolean isPublished() {
        return published;
    }

    public int getHashed() {
        return hashed;
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ConcurrentEntry getNextInLookup() {
        return nextInLookup;
    }

    public void setNextInLookup(ConcurrentEntry nextInLookup) {
        this.nextInLookup = nextInLookup;
    }

    public boolean compareAndSetNextInLookup(ConcurrentEntry old, ConcurrentEntry nextInLookup) {
        return NEXT_IN_LOOKUP_HANDLE.compareAndSet(this, old, nextInLookup);
    }

    public ConcurrentEntry getPreviousInSequence() {
        return previousInSequence;
    }

    public void setPreviousInSequence(ConcurrentEntry previousInSequence) {
        assert !removed;
        this.previousInSequence = previousInSequence;
    }

    public boolean compareAndSetPreviousInSequence(ConcurrentEntry old, ConcurrentEntry previousInSequence) {
        assert !removed;
        return PREV_IN_SEQUENCE_HANDLE.compareAndSet(this, old, previousInSequence);
    }

    public ConcurrentEntry getNextInSequence() {
        return nextInSequence;
    }

    public void setNextInSequence(ConcurrentEntry nextInSequence) {
        assert !removed;
        this.nextInSequence = nextInSequence;
    }

    public boolean compareAndSetNextInSequence(ConcurrentEntry old, ConcurrentEntry nextInSequence) {
        assert !removed;
        return NEXT_IN_SEQUENCE_HANDLE.compareAndSet(this, old, nextInSequence);
    }

    @Override
    public String toString() {
        if (key == null) {
            if (lock) {
                return "lock";
            } else if (removed) {
                return "removed";
            } else if (previousInSequence == null) {
                return "HEAD";
            } else {
                return "TAIL";
            }
        } else {
            return key.toString();
        }
    }

}
