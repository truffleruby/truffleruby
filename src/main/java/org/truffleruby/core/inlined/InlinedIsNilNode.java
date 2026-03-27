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

import com.oracle.truffle.api.dsl.Cached;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

public abstract class InlinedIsNilNode extends UnaryInlinedOperationNode {

    protected static final String METHOD = "nil?";

    public InlinedIsNilNode(RubyLanguage language, RubyCallNodeParameters callNodeParameters) {
        super(
                language,
                callNodeParameters,
                language.coreMethodAssumptions.nilClassIsNilAssumption);
    }

    @Specialization(assumptions = "assumptions")
    boolean nil(Nil self) {
        return true;
    }

    @Specialization(
            guards = "lookup.lookupProtected(frame, self, METHOD) == coreMethods().KERNEL_IS_NIL",
            assumptions = "assumptions",
            limit = "1")
    boolean notNil(VirtualFrame frame, Object self,
            @Cached LookupMethodOnSelfNode lookup) {
        return false;
    }

    @Specialization
    Object fallback(VirtualFrame frame, Object self) {
        return rewriteAndCall(frame, self);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InlinedIsNilNodeGen.create(
                getLanguage(),
                this.parameters,
                getSelfNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
