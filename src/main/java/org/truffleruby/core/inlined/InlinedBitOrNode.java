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
import org.truffleruby.core.numeric.IntegerNodes.BitOrNode;
import org.truffleruby.core.numeric.IntegerNodesFactory.BitOrNodeFactory;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class InlinedBitOrNode extends BinaryInlinedOperationNode {

    @Child BitOrNode fixnumBitOr;

    public InlinedBitOrNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.integerBitOrAssumption);
    }

    @Specialization(assumptions = "assumptions")
    Object intBitOr(int a, int b) {
        return getBitOrNode().executeBitOr(a, b);
    }

    @Specialization(assumptions = "assumptions")
    Object longBitOr(long a, long b) {
        return getBitOrNode().executeBitOr(a, b);
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object a, Object b) {
        return rewriteAndCall(frame, a, b);
    }

    private BitOrNode getBitOrNode() {
        if (fixnumBitOr == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumBitOr = insert(BitOrNodeFactory.create(null));
        }
        return fixnumBitOr;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedBitOrNodeGen.create(
                getLanguage(),
                this.parameters,
                getLeftNode().cloneUninitialized(),
                getRightNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
