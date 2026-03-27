/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
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
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.frame.VirtualFrame;

/** Represents a super call with implicit arguments without a surrounding method */
public final class ZSuperOutsideMethodNode extends RubyContextSourceNode {

    final boolean insideDefineMethod;

    @Child private LookupSuperMethodNode lookupSuperMethodNode = LookupSuperMethodNodeGen.create();

    public ZSuperOutsideMethodNode(boolean insideDefineMethod) {
        this.insideDefineMethod = insideDefineMethod;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // This is MRI behavior
        if (insideDefineMethod) { // TODO (eregon, 22 July 2015): This check should be more dynamic.
            throw new RaiseException(getContext(), coreExceptions().runtimeError(
                    "implicit argument passing of super from method defined by define_method() is not supported. Specify all arguments explicitly.",
                    this));
        } else {
            throw new RaiseException(getContext(), coreExceptions().noSuperMethodOutsideMethodError(this));
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final Object self = RubyArguments.getSelf(frame);
        final InternalMethod superMethod = lookupSuperMethodNode.executeLookupSuperMethod(frame, self);

        if (superMethod == null) {
            return nil;
        } else {
            return FrozenStrings.SUPER;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ZSuperOutsideMethodNode(insideDefineMethod);
        return copy.copyFlags(this);
    }

}
