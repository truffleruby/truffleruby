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
package org.truffleruby.collections;

import java.util.Iterator;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BoundaryIterator<E> implements Iterator<E> {

    private final Iterator<E> iterator;

    public BoundaryIterator(Iterator<E> iterator) {
        this.iterator = iterator;
    }

    @TruffleBoundary
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @TruffleBoundary
    @Override
    public E next() {
        return iterator.next();
    }

    @TruffleBoundary
    @Override
    public void remove() {
        iterator.remove();
    }

}
