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
package org.truffleruby.extra;

import com.oracle.truffle.api.object.Shape;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import java.util.concurrent.atomic.AtomicReference;

public final class RubyAtomicReference extends RubyDynamicObject {

    final AtomicReference<Object> value;

    public RubyAtomicReference(RubyClass rubyClass, Shape shape, AtomicReference<Object> value) {
        super(rubyClass, shape);
        this.value = value;
    }

}
