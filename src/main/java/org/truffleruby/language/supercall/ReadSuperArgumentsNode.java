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

import org.truffleruby.core.array.ArrayToObjectArrayNode;
import org.truffleruby.core.array.ArrayToObjectArrayNodeGen;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/** Get the arguments of a super call with explicit arguments. */
public final class ReadSuperArgumentsNode extends RubyContextSourceNode {

    @Children private final RubyNode[] arguments;
    @Child private ArrayToObjectArrayNode unsplatNode;

    private final boolean isSplatted;

    public ReadSuperArgumentsNode(RubyNode[] arguments, boolean isSplatted) {
        assert !isSplatted || arguments.length == 1;
        this.arguments = arguments;
        this.isSplatted = isSplatted;
    }

    @ExplodeLoop
    @Override
    public final Object execute(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(arguments.length);

        // Execute the arguments
        final Object[] argumentsObjects = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            argumentsObjects[i] = arguments[i].execute(frame);
        }

        if (isSplatted) {
            return unsplat(argumentsObjects);
        } else {
            return argumentsObjects;
        }
    }

    private Object[] unsplat(Object[] argumentsObjects) {
        if (unsplatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            unsplatNode = insert(ArrayToObjectArrayNodeGen.create());
        }
        return unsplatNode.unsplat(argumentsObjects);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadSuperArgumentsNode(
                cloneUninitialized(arguments),
                isSplatted);
        return copy.copyFlags(this);
    }

}
