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
package org.truffleruby.core.support;

import org.truffleruby.algorithms.Randomizer;
import org.truffleruby.core.klass.RubyClass;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public final class RubyPRNGRandomizer extends RubyRandomizer {

    private Randomizer randomizer;

    // Each thread keeps its own private RubyRandomizer instance, so we use this setting to skip synchronization
    // for those
    private final boolean threadSafe;

    public RubyPRNGRandomizer(RubyClass rubyClass, Shape shape, Randomizer randomizer, boolean threadSafe) {
        super(rubyClass, shape);
        this.randomizer = randomizer;
        this.threadSafe = threadSafe;
    }

    public void setRandomizer(Randomizer randomizer) {
        this.randomizer = randomizer;
    }

    @TruffleBoundary
    public int genrandInt32() {
        if (threadSafe) {
            synchronized (this) {
                return randomizer.unsynchronizedGenrandInt32();
            }
        } else {
            return randomizer.unsynchronizedGenrandInt32();
        }
    }

    public Object getSeed() {
        return randomizer.getSeed();
    }
}
