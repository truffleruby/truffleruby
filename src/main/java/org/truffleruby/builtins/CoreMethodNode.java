/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.builtins;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateInline;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import org.truffleruby.language.RubyNode;

@GenerateNodeFactory
@GenerateInline(value = false, inherit = true)
public abstract class CoreMethodNode extends RubyContextSourceNode {

    @Override
    public final RubyNode cloneUninitialized() {
        throw CompilerDirectives.shouldNotReachHere(
                getClass() + " should be handled by RubyCoreMethodRootNode#cloneUninitializedRootNode()");
    }

}
