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
package org.truffleruby.language.objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceAssignableNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.locals.ReadFrameSlotNode;

public abstract class WriteInstanceVariableNode extends RubyContextSourceAssignableNode {

    private final String name;

    @Child private ReadFrameSlotNode readSelfSlotNode;
    @Child private IsFrozenNode isFrozenNode;
    @Child private RubyNode rhs;
    @Child private WriteObjectFieldNode writeNode;

    @CompilationFinal private boolean frozenProfile;

    @Override
    public abstract Object execute(VirtualFrame frame);

    public WriteInstanceVariableNode(String name, RubyNode rhs) {
        this.name = name;
        this.readSelfSlotNode = SelfNode.createReadSelfFrameSlotNode();
        this.rhs = rhs;
    }

    @Specialization
    Object doWrite(VirtualFrame frame) {
        final Object self = SelfNode.readSelf(frame, readSelfSlotNode);
        final Object value = rhs.execute(frame);
        write(self, value);
        return value;
    }

    @Override
    public void assign(VirtualFrame frame, Object value) {
        final Object self = SelfNode.readSelf(frame, readSelfSlotNode);
        write(self, value);
    }

    private void write(Object object, Object value) {
        if (getIsFrozenNode().execute(object)) {
            if (!frozenProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frozenProfile = true;
            }
            throw new RaiseException(getContext(), coreExceptions().frozenError(object, this));
        }

        if (writeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeNode = insert(WriteObjectFieldNodeGen.create());
        }

        writeNode.execute(this, (RubyDynamicObject) object, name, value);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    private IsFrozenNode getIsFrozenNode() {
        if (isFrozenNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isFrozenNode = insert(IsFrozenNodeGen.create());
        }
        return isFrozenNode;
    }

    @Override
    public AssignableNode toAssignableNode() {
        this.rhs = null;
        return this;
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = WriteInstanceVariableNodeGen.create(name, cloneUninitialized(rhs));
        return copy.copyFlags(this);
    }

}
