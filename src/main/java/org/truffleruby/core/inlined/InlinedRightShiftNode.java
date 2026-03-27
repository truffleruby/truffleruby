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
import org.truffleruby.core.numeric.IntegerNodes.RightShiftNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.RightShiftNodeFactory;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedRightShiftNode extends BinaryInlinedOperationNode {

    @Child RightShiftNode fixnumRightShift;

    public InlinedRightShiftNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.integerRightShiftAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intRightShift(int a, int b) {
        return getRightShiftNode().executeRightShift(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longRightShift(long a, int b) {
        return getRightShiftNode().executeRightShift(a, b);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private RightShiftNode getRightShiftNode() {
        if (fixnumRightShift == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumRightShift = insert(RightShiftNodeFactory.create(null));
        }
        return fixnumRightShift;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedRightShiftNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
