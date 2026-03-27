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
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import java.util.concurrent.ThreadLocalRandom;

@NodeChild(value = "valueNode", type = RubyBaseNodeWithExecute.class)
public abstract class ChaosNode extends RubyContextSourceNode {

    public static ChaosNode create() {
        return ChaosNodeGen.create(null);
    }

    public static ChaosNode create(RubyBaseNodeWithExecute value) {
        return ChaosNodeGen.create(value);
    }

    abstract RubyBaseNodeWithExecute getValueNode();

    @Specialization
    Object chaos(int value) {
        if (randomBoolean()) {
            return value;
        } else {
            return (long) value;
        }
    }

    @Specialization(guards = "fitsInInteger(value)")
    Object chaos(long value) {
        if (randomBoolean()) {
            return value;
        } else {
            return (int) value;
        }
    }

    @Specialization(guards = "!fitsInInteger(value)")
    long passThrough(long value) {
        return value;
    }

    @Fallback
    Object chaos(Object value) {
        return value;
    }

    @TruffleBoundary
    private boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ChaosNodeGen.create(getValueNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}

