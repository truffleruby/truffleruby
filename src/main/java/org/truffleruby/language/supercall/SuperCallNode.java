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
package org.truffleruby.language.supercall;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.LiteralCallNode;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class SuperCallNode extends LiteralCallNode {

    @Child private RubyNode arguments;
    @Child private RubyNode block;
    @Child private LookupSuperMethodNode lookupSuperMethodNode;
    @Child private CallSuperMethodNode callSuperMethodNode;

    public SuperCallNode(boolean isSplatted, RubyNode arguments, RubyNode block, ArgumentsDescriptor descriptor) {
        super(isSplatted, descriptor);
        this.arguments = arguments;
        this.block = block;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object self = RubyArguments.getSelf(frame);

        // Execute the arguments
        Object[] superArguments = (Object[]) arguments.execute(frame);

        ArgumentsDescriptor descriptor = this.descriptor;
        if (isSplatted) {
            // superArguments already splatted
            descriptor = getArgumentsDescriptorAndCheckRuby2KeywordsHash(superArguments, superArguments.length);
        }

        // Remove empty kwargs in the caller, so the callee does not need to care about this special case
        if (descriptor instanceof KeywordArgumentsDescriptor && emptyKeywordArguments(superArguments)) {
            superArguments = removeEmptyKeywordArguments(superArguments);
            descriptor = NoKeywordArgumentsDescriptor.INSTANCE;
        }

        // Execute the block
        final Object blockObject = block.execute(frame);

        final InternalMethod superMethod = executeLookupSuperMethod(frame, self);

        if (callSuperMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callSuperMethodNode = insert(CallSuperMethodNode.create());
        }

        return callSuperMethodNode.execute(frame, self, superMethod, descriptor, superArguments, blockObject);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final Object self = RubyArguments.getSelf(frame);
        final InternalMethod superMethod = executeLookupSuperMethod(frame, self);

        if (superMethod == null) {
            return nil;
        } else {
            return FrozenStrings.SUPER;
        }
    }

    private InternalMethod executeLookupSuperMethod(VirtualFrame frame, Object self) {
        if (lookupSuperMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupSuperMethodNode = insert(LookupSuperMethodNodeGen.create());
        }
        return lookupSuperMethodNode.executeLookupSuperMethod(frame, self);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new SuperCallNode(
                isSplatted,
                arguments.cloneUninitialized(),
                block.cloneUninitialized(),
                descriptor);
        return copy.copyFlags(this);
    }

}
