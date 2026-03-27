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
import org.truffleruby.core.numeric.IntegerNodes.MulNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.MulNodeFactory;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedMulNode extends BinaryInlinedOperationNode {

    @Child MulNode fixnumMul;

    public InlinedMulNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.integerMulAssumption,
                language.coreMethodAssumptions.floatMulAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intMul(int a, int b) {
        return getMulNode().executeMul(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longMul(long a, long b) {
        return getMulNode().executeMul(a, b);
    }

    @Specialization(assumptions = "assumptions")
    double floatMul(double a, double b) {
        return a * b;
    }

    @Specialization(assumptions = "assumptions")
    double longDouble(long a, double b) {
        return a * b;
    }

    @Specialization(assumptions = "assumptions")
    double doubleLong(double a, long b) {
        return a * b;
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private MulNode getMulNode() {
        if (fixnumMul == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumMul = insert(MulNodeFactory.create(null));
        }
        return fixnumMul;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedMulNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
