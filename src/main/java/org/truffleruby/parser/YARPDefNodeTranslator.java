/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Split;
import org.truffleruby.debug.MetricsProfiler.MetricKind;
import org.truffleruby.language.RubyMethodRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.CachedLazyCallTargetSupplier;

import org.ruby_lang.prism.Nodes;
import org.truffleruby.shared.options.Profile;

public final class YARPDefNodeTranslator extends YARPTranslator {

    private final boolean shouldLazyTranslate;

    public YARPDefNodeTranslator(RubyLanguage language, TranslatorEnvironment environment) {
        super(environment);

        if (parseEnvironment.parserContext.isEval() || parseEnvironment.isCoverageEnabled()) {
            shouldLazyTranslate = false;
        } else if (parseEnvironment.inCore()) {
            shouldLazyTranslate = language.options.LAZY_TRANSLATION_CORE;
        } else {
            shouldLazyTranslate = language.options.LAZY_TRANSLATION_USER;
        }
    }

    private RubyNode compileMethodBody(Nodes.DefNode node, Nodes.ParametersNode parameters, Arity arity) {
        declareLocalVariables(node.locals);

        final RubyNode loadArguments = new YARPLoadArgumentsTranslator(
                environment,
                parameters,
                arity,
                false,
                true,
                this).translate();

        RubyNode body = translateNodeOrNil(node.body).simplifyAsTailExpression();
        body = sequence(loadArguments, body);

        if (environment.hasFlipFlopStates()) {
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

    private RubyMethodRootNode translateMethodNodeLazily(Nodes.DefNode node, Nodes.ParametersNode parameters,
            Arity arity) {
        /* Multiple methods of the same file might trigger lazy translation at the same time. We need to prevent that
         * because there is shared mutable state in at least YARPTranslator, TranslatorEnvironment and ParseEnvironment.
         * So we synchronize on the common object for all translations in a file, ParseEnvironment. */
        synchronized (parseEnvironment) {
            return translateMethodNode(node, parameters, arity);
        }
    }

    private RubyMethodRootNode translateMethodNodeWithMetrics(RubyContext context, Nodes.DefNode node,
            Nodes.ParametersNode parameters, Arity arity) {
        if (context != null && context.getOptions().METRICS_PROFILE_REQUIRE == Profile.TOTAL) {
            return context.getMetricsProfiler().callWithMetrics(
                    MetricKind.TRANSLATING,
                    parseEnvironment.rubySource.getSourcePath(),
                    () -> translateMethodNodeLazily(node, parameters, arity));
        } else {
            return translateMethodNodeLazily(node, parameters, arity);
        }
    }

    private Nodes.DefNode getNonLazyDefNodeWithMetrics(RubyContext context, Nodes.DefNode node) {
        if (context != null && context.getOptions().METRICS_PROFILE_REQUIRE == Profile.TOTAL) {
            return context.getMetricsProfiler().callWithMetrics(
                    MetricKind.PARSING,
                    parseEnvironment.rubySource.getSourcePath(),
                    () -> node.getNonLazy());
        } else {
            return node.getNonLazy();
        }
    }

    public CachedLazyCallTargetSupplier buildMethodNodeCompiler(Nodes.DefNode node, Nodes.ParametersNode parameters,
            Arity arity) {
        if (shouldLazyTranslate) {
            return new CachedLazyCallTargetSupplier(
                    () -> {
                        var context = RubyLanguage.getCurrentContext();
                        Nodes.DefNode fullDefNode = getNonLazyDefNodeWithMetrics(context, node);
                        return translateMethodNodeWithMetrics(context, fullDefNode, parameters, arity).getCallTarget();
                    });
        } else {
            final RubyMethodRootNode root = translateMethodNode(node.getNonLazy(), parameters, arity);
            return new CachedLazyCallTargetSupplier(() -> root.getCallTarget());
        }
    }

}
