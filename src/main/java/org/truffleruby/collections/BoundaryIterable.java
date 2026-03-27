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

public final class BoundaryIterable<E> implements Iterable<E> {

    public static <E> BoundaryIterable<E> wrap(Iterable<E> iterable) {
        return new BoundaryIterable<>(iterable);
    }

    private final Iterable<E> iterable;

    public BoundaryIterable(Iterable<E> iterable) {
        this.iterable = iterable;
    }

    @Override
    public BoundaryIterator<E> iterator() {
        return new BoundaryIterator<>(getIterator());
    }

    @TruffleBoundary
    private Iterator<E> getIterator() {
        return iterable.iterator();
    }

}
