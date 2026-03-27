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

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class ReadClassVariableNode extends RubyContextSourceNode {

    private final String name;
    private final BranchProfile missingProfile = BranchProfile.create();
    private final BranchProfile topLevelProfile = BranchProfile.create();

    @Child private RubyNode lexicalScopeNode;
    @Child private ResolveTargetModuleForClassVariablesNode resolveTargetModuleNode = ResolveTargetModuleForClassVariablesNode
            .create();
    @Child private LookupClassVariableNode lookupClassVariableNode = LookupClassVariableNode.create();

    public ReadClassVariableNode(RubyNode lexicalScopeNode, String name) {
        this.lexicalScopeNode = lexicalScopeNode;
        this.name = name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final LexicalScope lexicalScope = (LexicalScope) lexicalScopeNode.execute(frame);
        if (lexicalScope.getParent() == null) {
            topLevelProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().runtimeErrorClassVariableTopLevel(this));
        }

        final RubyModule module = resolveTargetModuleNode.execute(lexicalScope);
        final Object value = lookupClassVariableNode.execute(module, name);

        if (value == null) {
            missingProfile.enter();
            throw new RaiseException(
                    getContext(),
                    coreExceptions().nameErrorUninitializedClassVariable(module, name, this));
        }

        return value;
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final LexicalScope lexicalScope = (LexicalScope) lexicalScopeNode.execute(frame);
        final RubyModule module = resolveTargetModuleNode.execute(lexicalScope);
        final Object value = lookupClassVariableNode.execute(module, name);

        if (value == null) {
            return nil;
        } else {
            return FrozenStrings.CLASS_VARIABLE;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadClassVariableNode(
                lexicalScopeNode.cloneUninitialized(),
                name);
        return copy.copyFlags(this);
    }

}
