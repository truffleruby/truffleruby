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
package org.truffleruby.language.constants;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.frame.VirtualFrame;

/** Read a constant using the current lexical scope: CONST */
public final class ReadConstantWithLexicalScopeNode extends RubyContextSourceNode {

    private final LexicalScope lexicalScope;
    private final String name;

    @Child private LookupConstantWithLexicalScopeNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode = GetConstantNode.create();

    public ReadConstantWithLexicalScopeNode(LexicalScope lexicalScope, String name) {
        this.lexicalScope = lexicalScope;
        this.name = name;
        this.lookupConstantNode = LookupConstantWithLexicalScopeNodeGen.create(lexicalScope, name);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyModule module = lexicalScope.getLiveModule();
        return getConstantNode.lookupAndResolveConstant(lexicalScope, module, name, lookupConstantNode, true);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        final RubyConstant constant;
        try {
            constant = lookupConstantNode.executeLookupConstant();
        } catch (RaiseException e) {
            if (e.getException().getLogicalClass() == coreLibrary().nameErrorClass) {
                // private constant
                return nil;
            }
            throw e;
        }

        if (ModuleOperations.isConstantDefined(constant)) {
            return FrozenStrings.CONSTANT;
        } else {
            return nil;
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadConstantWithLexicalScopeNode(lexicalScope, name);
        return copy.copyFlags(this);
    }

}
