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
import org.truffleruby.core.numeric.IntegerNodes.BitAndNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.BitAndNodeFactory;
import org.truffleruby.language.NoImplicitCastsToLong;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@TypeSystemReference(NoImplicitCastsToLong.class)
public abstract class InlinedBitAndNode extends BinaryInlinedOperationNode {

    @Child BitAndNode fixnumBitAnd;

    public InlinedBitAndNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.integerBitAndAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intBitAnd(int a, int b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object intLongBitAnd(int a, long b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longIntBitAnd(long a, int b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longBitAnd(long a, long b) {
        return getBitAndNode().executeBitAnd(a, b);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private BitAndNode getBitAndNode() {
        if (fixnumBitAnd == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumBitAnd = insert(BitAndNodeFactory.create(null));
        }
        return fixnumBitAnd;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedBitAndNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }
}
