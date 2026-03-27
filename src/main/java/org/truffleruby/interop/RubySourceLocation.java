/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;

import java.util.Objects;

public final class RubySourceLocation extends RubyDynamicObject {

    public final SourceSection sourceSection;

    public RubySourceLocation(RubyClass rubyClass, Shape shape, SourceSection sourceSection) {
        super(rubyClass, shape);
        this.sourceSection = Objects.requireNonNull(sourceSection);
    }

}
