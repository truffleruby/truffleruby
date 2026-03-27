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
package org.truffleruby.language.constants;

import com.oracle.truffle.api.Assumption;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.methods.SharedMethodInfo;

public final class ConstantEntry {

    private final Assumption assumption;
    private final RubyConstant constant;

    public ConstantEntry(RubyConstant constant) {
        this.assumption = Assumption.create("constant is not overridden:");
        this.constant = constant;
    }

    public ConstantEntry() {
        this.assumption = Assumption.create("constant is not defined:");
        this.constant = null;
    }

    public ConstantEntry withNewAssumption() {
        if (constant != null) {
            return new ConstantEntry(constant);
        } else {
            return new ConstantEntry();
        }
    }

    public Assumption getAssumption() {
        return assumption;
    }

    public RubyConstant getConstant() {
        return constant;
    }

    public void invalidate(String reason, RubyModule module, String constantName) {
        assumption.invalidate(reason + ": " + SharedMethodInfo.moduleAndConstantName(module, constantName));
    }

}
