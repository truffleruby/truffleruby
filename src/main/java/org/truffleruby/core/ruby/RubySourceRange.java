/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.ruby;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;

public final class RubySourceRange extends RubyDynamicObject {

    public final SourceSection sourceSection;

    @TruffleBoundary
    public RubySourceRange(RubyClass rubyClass, Shape shape, SourceSection sourceSection) {
        super(rubyClass, shape);
        if (!sourceSection.isAvailable()) {
            throw CompilerDirectives.shouldNotReachHere("not available source section: " + sourceSection);
        }
        this.sourceSection = Objects.requireNonNull(sourceSection);
    }

}
