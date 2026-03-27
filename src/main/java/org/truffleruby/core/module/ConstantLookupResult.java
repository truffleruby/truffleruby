/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.module;

import org.truffleruby.RubyContext;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class ConstantLookupResult {

    private final RubyConstant constant;
    @CompilationFinal(dimensions = 1) private final Assumption[] assumptions;

    public ConstantLookupResult(RubyConstant constant, Assumption... assumptions) {
        assert constant == null || !(constant.isAutoload() && constant.getAutoloadConstant().isAutoloadingThread());
        this.constant = constant;
        this.assumptions = assumptions;
    }

    public boolean isFound() {
        return constant != null && !constant.isUndefined();
    }

    public boolean isDeprecated() {
        return constant != null && constant.isDeprecated();
    }

    public boolean isAutoload() {
        return constant != null && constant.isAutoload();
    }

    public boolean isVisibleTo(RubyContext context, LexicalScope lexicalScope, RubyModule module) {
        return constant == null || constant.isVisibleTo(context, lexicalScope, module);
    }

    public RubyConstant getConstant() {
        return constant;
    }

    public Assumption[] getAssumptions() {
        return assumptions;
    }

}
