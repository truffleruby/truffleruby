/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.mutex;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;

public final class RubyConditionVariable extends RubyDynamicObject {

    final ReentrantLock lock;
    final Condition condition;
    int waiters = 0;
    int signals = 0;

    public RubyConditionVariable(RubyClass rubyClass, Shape shape, ReentrantLock lock, Condition condition) {
        super(rubyClass, shape);
        this.lock = lock;
        this.condition = condition;
    }

}
