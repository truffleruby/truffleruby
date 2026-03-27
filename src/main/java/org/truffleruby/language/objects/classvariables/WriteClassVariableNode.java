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
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyContextSourceAssignableNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.control.RaiseException;

public final class WriteClassVariableNode extends RubyContextSourceAssignableNode {

    private final String name;
    private final BranchProfile topLevelProfile = BranchProfile.create();

    @Child private RubyNode rhs;
    @Child private RubyNode lexicalScopeNode;
    @Child private ResolveTargetModuleForClassVariablesNode resolveTargetModuleNode = ResolveTargetModuleForClassVariablesNode
            .create();
    @Child private SetClassVariableNode setClassVariableNode = SetClassVariableNode.create();

    public WriteClassVariableNode(RubyNode lexicalScopeNode, String name, RubyNode rhs) {
        this.lexicalScopeNode = lexicalScopeNode;
        this.name = name;
        this.rhs = rhs;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object value = rhs.execute(frame);
        assign(frame, value);
        return value;
    }

    @Override
    public void assign(VirtualFrame frame, Object value) {
        final LexicalScope lexicalScope = (LexicalScope) lexicalScopeNode.execute(frame);
        if (lexicalScope.getParent() == null) {
            topLevelProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().runtimeErrorClassVariableTopLevel(this));
        }

        final RubyModule module = resolveTargetModuleNode.execute(lexicalScope);
        setClassVariableNode.execute(module, name, value);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
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
        var copy = new WriteClassVariableNode(
                lexicalScopeNode.cloneUninitialized(),
                name,
                cloneUninitialized(rhs));
        return copy.copyFlags(this);
    }

}
