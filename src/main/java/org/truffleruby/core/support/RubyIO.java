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

import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;

public final class RubyIO extends RubyDynamicObject {

    public static final int CLOSED_FD = -1;

    private int descriptor;

    public RubyIO(RubyClass rubyClass, Shape shape, int descriptor) {
        super(rubyClass, shape);
        this.descriptor = descriptor;
    }

    public int getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(int descriptor) {
        this.descriptor = descriptor;
    }

}
