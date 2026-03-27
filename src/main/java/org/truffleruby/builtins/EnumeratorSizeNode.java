/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.builtins;

import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class EnumeratorSizeNode extends RubyContextSourceNode {

    @Child private RubyNode method;
    @Child private DispatchNode toEnumWithSize;

    private final ConditionProfile noBlockProfile = ConditionProfile.create();

    private final RubySymbol methodName;
    private final RubySymbol sizeMethodName;

    public EnumeratorSizeNode(RubySymbol sizeMethodName, RubySymbol methodName, RubyNode method) {
        this.method = method;
        this.methodName = methodName;
        this.sizeMethodName = sizeMethodName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object block = RubyArguments.getBlock(frame);

        if (noBlockProfile.profile(block == nil)) {
            if (toEnumWithSize == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toEnumWithSize = insert(DispatchNode.create());
            }

            final Object self = RubyArguments.getSelf(frame);
            return toEnumWithSize.call(
                    coreLibrary().truffleKernelOperationsModule,
                    "to_enum_with_size",
                    self,
                    methodName,
                    sizeMethodName);
        } else {
            return method.execute(frame);
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new EnumeratorSizeNode(
                sizeMethodName,
                methodName,
                method.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
