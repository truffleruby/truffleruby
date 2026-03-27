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

import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class MethodLookupResult {

    private final InternalMethod method;
    @CompilationFinal(dimensions = 1) private final Assumption[] assumptions;

    public MethodLookupResult(InternalMethod method, Assumption... assumptions) {
        this.method = method;
        this.assumptions = assumptions;
    }

    public MethodLookupResult withNoMethod() {
        if (method == null) {
            return this;
        } else {
            return new MethodLookupResult(null, assumptions);
        }
    }

    public boolean isDefined() {
        return method != null && !method.isUndefined();
    }

    public InternalMethod getMethod() {
        return method;
    }

    public Assumption[] getAssumptions() {
        return assumptions;
    }

}
