/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.RubyMethodRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.CachedLazyCallTargetSupplier;

import org.ruby_lang.prism.Nodes;

public final class YARPDefNodeTranslator extends YARPTranslator {

    private final boolean shouldLazyTranslate;

    public YARPDefNodeTranslator(
            RubyLanguage language,
            TranslatorEnvironment environment,
            RubyDeferredWarnings rubyWarnings) {
        super(environment, rubyWarnings);

        if (parseEnvironment.parserContext.isEval() || parseEnvironment.isCoverageEnabled()) {
            shouldLazyTranslate = false;
        } else if (language.isCoreSource(source)) {
            shouldLazyTranslate = language.options.LAZY_TRANSLATION_CORE;
        } else {
            shouldLazyTranslate = language.options.LAZY_TRANSLATION_USER;
        }
    }

    private RubyNode compileMethodBody(Nodes.DefNode node, Nodes.ParametersNode parameters, Arity arity) {
        declareLocalVariables(node);

        final RubyNode loadArguments = new YARPLoadArgumentsTranslator(
                environment,
                parameters,
                arity,
                false,
                true,
                this).translate();

        RubyNode body = translateNodeOrNil(node.body).simplifyAsTailExpression();
        body = sequence(loadArguments, body);

        if (environment.getFlipFlopStates().size() > 0) {
            body = sequence(initFlipFlopStates(environment), body);
        }

        return body;
    }

    private RubyMethodRootNode translateMethodNode(Nodes.DefNode node, Nodes.ParametersNode parameters, Arity arity) {
        RubyNode body = compileMethodBody(node, parameters, arity);

        return new RubyMethodRootNode(
                language,
                getSourceSection(node),
                environment.computeFrameDescriptor(),
                environment.getSharedMethodInfo(),
                body,
                Split.HEURISTIC,
                environment.getReturnID(),
                arity);
    }

    public CachedLazyCallTargetSupplier buildMethodNodeCompiler(Nodes.DefNode node, Nodes.ParametersNode parameters,
            Arity arity) {
        if (shouldLazyTranslate) {
            return new CachedLazyCallTargetSupplier(
                    () -> translateMethodNode(node, parameters, arity).getCallTarget());
        } else {
            final RubyMethodRootNode root = translateMethodNode(node, parameters, arity);
            return new CachedLazyCallTargetSupplier(() -> root.getCallTarget());
        }
    }

    private void declareLocalVariables(Nodes.DefNode node) {
        for (String name : node.locals) {
            assert !(name.equals("*") || name.equals("**") || name.equals("&") || name.equals("...")) : name;
            environment.declareVar(name);
        }
    }

}
