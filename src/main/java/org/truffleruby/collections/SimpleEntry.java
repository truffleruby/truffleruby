/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.collections;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.Map;

/** A final class implementing {@link Map.Entry}, so that it's safe to call {@link #getKey()} and {@link #getValue()} in
 * PE code. */
public final class SimpleEntry<K, V> implements Map.Entry<K, V> {

    private final K key;
    private final V value;

    public SimpleEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @TruffleBoundary
    @Override
    public V setValue(V value) {
        throw new UnsupportedOperationException("SimpleEntry is immutable");
    }
}
