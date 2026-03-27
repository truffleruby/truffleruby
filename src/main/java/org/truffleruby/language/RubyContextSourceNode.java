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
package org.truffleruby.language;

import com.oracle.truffle.api.frame.VirtualFrame;

/** See {@link RubyNode} */
public abstract class RubyContextSourceNode extends RubyContextSourceNodeCustomExecuteVoid {

    /** Final here to avoid the DSL generating extra redundant methods for this. Subclass
     * {@link org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid} for implementing a custom executeVoid().
     * Be careful to either mark the executeVoid() override as final or the class as final, otherwise the DSL will
     * override and just call execute() which would ignore our override. */
    @Override
    public final Nil executeVoid(VirtualFrame frame) {
        execute(frame);
        return nil;
    }

}
