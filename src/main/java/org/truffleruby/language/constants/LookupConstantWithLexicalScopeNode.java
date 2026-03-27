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

import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.module.ConstantLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class LookupConstantWithLexicalScopeNode extends LookupConstantBaseNode
        implements LookupConstantInterface {

    private final LexicalScope lexicalScope;
    private final String name;

    public LookupConstantWithLexicalScopeNode(LexicalScope lexicalScope, String name) {
        this.lexicalScope = lexicalScope;
        this.name = name;
    }

    public RubyModule getModule() {
        return lexicalScope.getLiveModule();
    }

    public abstract RubyConstant executeLookupConstant();

    @SuppressFBWarnings("ES")
    @Override
    public RubyConstant lookupConstant(Node node, LexicalScope lexicalScope, RubyModule module, String name,
            boolean checkName) {
        assert name == this.name;
        return executeLookupConstant();
    }

    @Specialization(assumptions = "constant.getAssumptions()")
    RubyConstant lookupConstant(
            @Cached("doLookup()") ConstantLookupResult constant) {
        if (constant.isDeprecated()) {
            warnDeprecatedConstant(getModule(), name);
        }
        return constant.getConstant();
    }

    @Specialization
    RubyConstant lookupConstantUncached(
            @Cached InlinedConditionProfile isDeprecatedProfile) {
        final ConstantLookupResult constant = doLookup();
        if (isDeprecatedProfile.profile(this, constant.isDeprecated())) {
            warnDeprecatedConstant(getModule(), name);
        }
        return constant.getConstant();
    }

    @NeverDefault
    @TruffleBoundary
    protected ConstantLookupResult doLookup() {
        return ModuleOperations.lookupConstantWithLexicalScope(getContext(), lexicalScope, name);
    }

}
