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
package org.truffleruby.core.inlined;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedGreaterOrEqualNode extends BinaryInlinedOperationNode {

    public InlinedGreaterOrEqualNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.integerGreaterOrEqualAssumption,
                language.coreMethodAssumptions.floatGreaterOrEqualAssumption);
    }

    @Specialization(assumptions = "assumptions")
    boolean doInt(int a, int b) {
        return a >= b;
    }

    @Specialization(assumptions = "assumptions")
    boolean doLong(long a, long b) {
        return a >= b;
    }

    @Specialization(assumptions = "assumptions")
    boolean doDouble(double a, double b) {
        return a >= b;
    }

    @Specialization(assumptions = "assumptions")
    boolean longDouble(long a, double b) {
        return a >= b;
    }

    @Specialization(assumptions = "assumptions")
    boolean doubleLong(double a, long b) {
        return a >= b;
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedGreaterOrEqualNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
