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

import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.ModuleBodyDefinition;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

/** Defines a new class or module (or gets an existing one) and executes its body. */
public final class RunModuleDefinitionNode extends RubyContextSourceNode {

    @Child private RubyNode definingModule;
    private final ModuleBodyDefinition moduleBodyDefinition;
    @Child private IndirectCallNode callModuleDefinitionNode = Truffle.getRuntime().createIndirectCallNode();

    public RunModuleDefinitionNode(ModuleBodyDefinition definition, RubyNode definingModule) {
        this.definingModule = definingModule;
        this.moduleBodyDefinition = definition;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyModule module = (RubyModule) definingModule.execute(frame);
        final InternalMethod definition = moduleBodyDefinition.createMethod(frame, module, this);

        return callModuleDefinitionNode.call(definition.getCallTarget(), RubyArguments.pack(
                null,
                null,
                definition,
                null,
                module,
                nil,
                NoKeywordArgumentsDescriptor.INSTANCE,
                EMPTY_ARGUMENTS));
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new RunModuleDefinitionNode(
                moduleBodyDefinition,
                definingModule.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
