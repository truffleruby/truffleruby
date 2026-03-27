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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import org.ruby_lang.prism.Nodes.NoBlockParameterNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.MissingArgumentBehavior;
import org.truffleruby.language.arguments.ReadKeywordArgumentNode;
import org.truffleruby.language.arguments.ReadKeywordRestArgumentNode;
import org.truffleruby.language.arguments.ReadOptionalArgumentNode;
import org.truffleruby.language.arguments.ReadPostArgumentNode;
import org.truffleruby.language.arguments.ReadPreArgumentNode;
import org.truffleruby.language.arguments.ReadRestArgumentNode;
import org.truffleruby.language.arguments.SaveMethodBlockNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;

import org.truffleruby.language.methods.Arity;
import org.ruby_lang.prism.Nodes;

/** Translate method or block parameters and assign local variables.
 *
 * Parameters should be iterated in the same order {@link org.truffleruby.parser.YARPReloadArgumentsTranslator} iterates
 * to handle multiple "_" parameters (and parameters with "_" prefix) correctly. */
public final class YARPLoadArgumentsTranslator extends YARPBaseTranslator {

    private final Arity arity;
    /** block or lambda/method */
    private final boolean isProc;
    /** method or proc */
    private final boolean isMethod;
    private final YARPTranslator yarpTranslator;

    private enum State {
        PRE,
        OPT,
        POST
    }

    private final Nodes.ParametersNode parameters;
    private final boolean hasKeywordArguments;
    /** position of actual argument in a frame that is being evaluated/read to match a read node and actual argument */
    private int index = 0;
    /** to distinguish pre and post Nodes.RequiredParameterNode parameters */
    private State state;
    /** number of duplicated parameters with "_" prefix */
    private int repeatedParameterCounter = 2;

    public YARPLoadArgumentsTranslator(
            TranslatorEnvironment environment,
            Nodes.ParametersNode parameters,
            Arity arity,
            boolean isProc,
            boolean isMethod,
            YARPTranslator yarpTranslator) {
        super(environment);
        this.arity = arity;
        this.isProc = isProc;
        this.isMethod = isMethod;
        this.yarpTranslator = yarpTranslator;
        this.parameters = Objects.requireNonNull(parameters);
        this.hasKeywordArguments = hasKeywordArguments(parameters);
    }

    public RubyNode translate() {
        final List<RubyNode> sequence = new ArrayList<>();

        sequence.add(YARPTranslator.loadSelf(language));

        if (parameters.requireds.length > 0) {
            state = State.PRE;
            for (var node : parameters.requireds) {
                sequence.add(node.accept(this));
                index++;
            }
        }

        // Do this before handling optional arguments as one might get
        // its default value via a `yield`.
        if (isMethod) {
            sequence.add(saveMethodBlockArg());
        }

        // Early return for the common case of zero parameters
        if (parameters == ZERO_PARAMETERS_NODE) {
            return sequence(sequence.toArray(RubyNode.EMPTY_ARRAY));
        }

        if (parameters.optionals.length > 0) {
            for (var node : parameters.optionals) {
                sequence.add(node.accept(this));
                index++;
            }
        }

        if (parameters.rest != null) {
            if (parameters.rest instanceof Nodes.ImplicitRestNode) {
                // do nothing

                // The only reason to save anonymous rest parameter in a local variable is to be able to forward it.
                // Implicit rest is allowed only in blocks but anonymous rest forwarding works only in methods/lambdas.
            } else {
                sequence.add(parameters.rest.accept(this));
            }
        }

        if (parameters.posts.length > 0) {
            state = State.POST;
            index = -1;

            for (int i = parameters.posts.length - 1; i >= 0; i--) {
                sequence.add(parameters.posts[i].accept(this));
                index--;
            }
        }

        for (var node : parameters.keywords) {
            sequence.add(node.accept(this));
        }

        if (hasKeywordsRest(parameters)) {
            sequence.add(parameters.keyword_rest.accept(this));
        }

        if (parameters.block != null) {
            sequence.add(parameters.block.accept(this));
        }

        return sequence(sequence.toArray(RubyNode.EMPTY_ARRAY));
    }

    public RubyNode saveMethodBlockArg() {
        final int slot = environment.declareVar(TranslatorEnvironment.METHOD_BLOCK_NAME);
        return new SaveMethodBlockNode(slot);
    }

    @Override
    public RubyNode visitMultiTargetNode(Nodes.MultiTargetNode node) {
        final RubyNode readNode;

        if (state == YARPLoadArgumentsTranslator.State.PRE) {
            readNode = YARPTranslator.profileArgument(
                    language,
                    new ReadPreArgumentNode(
                            index,
                            hasKeywordArguments,
                            isProc ? MissingArgumentBehavior.NIL : MissingArgumentBehavior.RUNTIME_ERROR));
        } else if (state == YARPLoadArgumentsTranslator.State.POST) {
            readNode = new ReadPostArgumentNode(-index, getRequiredCount(), getOptionalCount(), hasRest(),
                    hasKeywordArguments);
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final var translator = new YARPMultiTargetNodeTranslator(node, language, yarpTranslator, readNode);
        final RubyNode rubyNode = translator.translate();

        // Prolog is used now only for caching of method receiver (e.g. a.b or a[b]) and constant module (e.g. a::B).
        // So for method or block parameters prolog is supposed to be empty.
        assert translator.prolog.isEmpty();

        return rubyNode;
    }

    @Override
    public RubyNode visitRequiredKeywordParameterNode(Nodes.RequiredKeywordParameterNode node) {
        final int slot = environment.findFrameSlot(node.name);
        final var name = language.getSymbol(node.name);
        final var readNode = ReadKeywordArgumentNode.create(name, null);

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitRequiredParameterNode(Nodes.RequiredParameterNode node) {
        final RubyNode readNode;

        if (state == YARPLoadArgumentsTranslator.State.PRE) {
            readNode = YARPTranslator.profileArgument(
                    language,
                    new ReadPreArgumentNode(
                            index,
                            hasKeywordArguments,
                            isProc ? MissingArgumentBehavior.NIL : MissingArgumentBehavior.RUNTIME_ERROR));

        } else if (state == YARPLoadArgumentsTranslator.State.POST) {
            readNode = new ReadPostArgumentNode(-index, getRequiredCount(), getOptionalCount(), hasRest(),
                    hasKeywordArguments);
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final int slot;
        if (node.isRepeatedParameter()) {
            // when there are multiple "_" parameters
            String name = createNameForRepeatedParameter(node.name);
            slot = environment.declareVar(name);
        } else {
            slot = environment.findFrameSlot(node.name);
        }

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitOptionalKeywordParameterNode(Nodes.OptionalKeywordParameterNode node) {
        final int slot = environment.findFrameSlot(node.name);
        final var name = language.getSymbol(node.name);
        final var value = node.value.accept(this);
        final var readNode = ReadKeywordArgumentNode.create(name, value);

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitOptionalParameterNode(Nodes.OptionalParameterNode node) {
        final RubyNode readNode;
        final RubyNode defaultValue = node.value.accept(this);
        int minimum = index + 1 + parameters.posts.length;

        readNode = new ReadOptionalArgumentNode(
                index,
                minimum,
                hasKeywordArguments,
                defaultValue);

        final int slot;
        if (node.isRepeatedParameter()) {
            // when there are multiple "_" parameters
            String name = createNameForRepeatedParameter(node.name);
            slot = environment.declareVar(name);
        } else {
            slot = environment.findFrameSlot(node.name);
        }

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitRestParameterNode(Nodes.RestParameterNode node) {
        final RubyNode readNode;

        int from = parameters.requireds.length + parameters.optionals.length;
        int to = -parameters.posts.length;
        readNode = new ReadRestArgumentNode(from, -to, hasKeywordArguments);

        final int slot;
        if (node.name != null) {
            if (node.isRepeatedParameter()) {
                // when there are multiple "_" parameters
                String name = createNameForRepeatedParameter(node.name);
                slot = environment.declareVar(name);
            } else {
                slot = environment.findFrameSlot(node.name);
            }
        } else { // def a(*)
            slot = environment.declareVar(TranslatorEnvironment.DEFAULT_REST_NAME);
        }

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitImplicitRestNode(Nodes.ImplicitRestNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in #translateWithParameters");
    }

    @Override
    public RubyNode visitKeywordRestParameterNode(Nodes.KeywordRestParameterNode node) {
        final RubyNode readNode = new ReadKeywordRestArgumentNode(language, arity);

        final int slot;
        if (node.name != null) {
            slot = environment.findFrameSlot(node.name);
        } else { // def a(**)
            slot = environment.declareVar(TranslatorEnvironment.DEFAULT_KEYWORD_REST_NAME);
        }

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitBlockParameterNode(Nodes.BlockParameterNode node) {
        final int slot;
        if (node.name != null) {
            slot = environment.findFrameSlot(node.name);
        } else { // def a(&)
            slot = environment.declareVar(TranslatorEnvironment.DEFAULT_BLOCK_NAME);
        }

        return new SaveMethodBlockNode(slot);
    }

    @Override
    public RubyNode visitNoBlockParameterNode(NoBlockParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("&nil is Ruby 4.0+");
    }

    @Override
    public RubyNode visitForwardingParameterNode(Nodes.ForwardingParameterNode node) {
        // is allowed in method parameters only

        // desugar ... to *, **, and & parameters
        environment.declareVar(TranslatorEnvironment.FORWARDED_REST_NAME);
        environment.declareVar(TranslatorEnvironment.FORWARDED_KEYWORD_REST_NAME);
        environment.declareVar(TranslatorEnvironment.FORWARDED_BLOCK_NAME);

        final var rest = new Nodes.RestParameterNode(0, 0, NO_FLAGS, TranslatorEnvironment.FORWARDED_REST_NAME);
        final var keyrest = new Nodes.KeywordRestParameterNode(0, 0, NO_FLAGS,
                TranslatorEnvironment.FORWARDED_KEYWORD_REST_NAME);
        final var block = new Nodes.BlockParameterNode(0, 0, NO_FLAGS, TranslatorEnvironment.FORWARDED_BLOCK_NAME);

        return sequence(
                rest.accept(this),
                keyrest.accept(this),
                block.accept(this));
    }

    @Override
    protected RubyNode defaultVisit(Nodes.Node node) {
        // For normal expressions in the default value for optional arguments, use the normal body translator
        return node.accept(yarpTranslator);
    }

    private int getRequiredCount() {
        return parameters.requireds.length + parameters.posts.length;
    }

    private int getOptionalCount() {
        return parameters.optionals.length;
    }

    private boolean hasRest() {
        return parameters.rest != null;
    }

    /** Generate a name for subsequent local variable, that look like %_1, %_2, etc.
     *
     * Parameters that start with "_" (e.g. _, _options, etc.) can be used repeatedly in a method parameters list, e.g.
     *
     * <pre>
     *     def foo(_, _)
     *     end
     * </pre>
     *
     * They should be forwarded properly but there are no local variables declared in Prism for such duplicated
     * parameters. That's why such local variables should be declared now. */
    private String createNameForRepeatedParameter(String name) {
        int count = repeatedParameterCounter++;
        return "%" + name + count;
    }

}
