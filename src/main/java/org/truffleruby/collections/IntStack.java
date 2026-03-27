/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.collections;

import org.truffleruby.core.array.ArrayUtils;

public final class IntStack {

    int[] storage;
    int index = -1;

    public IntStack() {
        this(16);
    }

    public IntStack(int length) {
        this.storage = new int[length];
    }

    public boolean isEmpty() {
        return index == -1;
    }

    public void push(int value) {
        if (++index == storage.length) {
            storage = ArrayUtils.grow(storage, storage.length * 2);
        }
        storage[index] = value;
    }

    public int peek() {
        return storage[index];
    }

    public int pop() {
        return storage[index--];
    }
}
