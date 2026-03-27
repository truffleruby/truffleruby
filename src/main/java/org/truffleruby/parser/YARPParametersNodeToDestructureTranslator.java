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

import com.oracle.truffle.api.CompilerDirectives;
import org.ruby_lang.prism.Nodes;
import org.ruby_lang.prism.Nodes.NoBlockParameterNode;
import org.truffleruby.core.array.ArrayIndexNodes;
import org.truffleruby.core.array.ArraySliceNodeGen;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.ReadBlockOptionalArgumentFromArrayNode;
import org.truffleruby.language.arguments.ReadKeywordArgumentNode;
import org.truffleruby.language.arguments.ReadBlockPostArgumentFromArrayNode;
import org.truffleruby.language.arguments.SaveMethodBlockNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;


/** Translates block's Nodes.ParametersNode to destructure a block's single Array argument.
 * 
 * When a block is called with a single argument that is Array (or could be converted to Array), this array is
 * destructured and its elements are assigned to a block parameters (that is similar to multi-assignment):
 * 
 * <pre>
 *   proc { |a, *b| [a, b] }.call([1, 2, 3]) # => [1, [2, 3]]
 * </pre>
 * 
 * Based on org.truffleruby.parser.YARPLoadArgumentsTranslator */
public final class YARPParametersNodeToDestructureTranslator extends YARPBaseTranslator {

    private final YARPTranslator yarpTranslator;

    private enum State {
        PRE,
        OPT,
        POST
    }

    private final Nodes.ParametersNode parameters;
    /** position of actual argument in a frame that is being evaluated/read to match a read node and actual argument */
    private int index;
    /** to distinguish pre and post Nodes.RequiredParameterNode parameters */
    private State state;

    private final RubyNode readArrayNode;

    public YARPParametersNodeToDestructureTranslator(
            TranslatorEnvironment environment,
            Nodes.ParametersNode parameters,
            RubyNode readArrayNode,
            YARPTranslator yarpTranslator) {
        super(environment);
        this.parameters = parameters;
        this.readArrayNode = readArrayNode;
        this.yarpTranslator = yarpTranslator;
    }

    public RubyNode translate() {
        final List<RubyNode> sequence = new ArrayList<>();

        sequence.add(YARPTranslator.loadSelf(language));

        if (parameters.requireds.length > 0) {
            state = State.PRE;
            index = 0;
            for (var node : parameters.requireds) {
                sequence.add(node.accept(this));
                index++;
            }
        }

        if (parameters.optionals.length > 0) {
            index = parameters.requireds.length;
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

    @Override
    public RubyNode visitMultiTargetNode(Nodes.MultiTargetNode node) {
        final RubyNode readNode;

        if (state == State.PRE) {
            readNode = ArrayIndexNodes.ReadConstantIndexNode.create(readArrayNode, index);
        } else if (state == State.POST) {
            readNode = new ReadBlockPostArgumentFromArrayNode(readArrayNode, -index, getRequiredCount(),
                    getOptionalCount(), hasRest());
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final var translator = new YARPMultiTargetNodeTranslator(node, language, yarpTranslator, readNode);
        final RubyNode rubyNode = translator.translate();
        return rubyNode;
    }

    @Override
    public RubyNode visitRequiredKeywordParameterNode(Nodes.RequiredKeywordParameterNode node) {
        // Passing single array argument to a block with required keyword arguments should lead
        // to ArgumentError 'missing keyword' in runtime.
        // So use ReadKeywordArgumentNode to raise this exception.
        final var name = language.getSymbol(node.name);
        return ReadKeywordArgumentNode.create(name, null);
    }

    @Override
    public RubyNode visitRequiredParameterNode(Nodes.RequiredParameterNode node) {
        final RubyNode readNode;

        if (state == State.PRE) {
            readNode = ArrayIndexNodes.ReadConstantIndexNode.create(readArrayNode, index);
        } else if (state == State.POST) {
            readNode = new ReadBlockPostArgumentFromArrayNode(readArrayNode, -index, getRequiredCount(),
                    getOptionalCount(), hasRest());
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final int slot = environment.findFrameSlot(node.name);
        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitOptionalKeywordParameterNode(Nodes.OptionalKeywordParameterNode node) {
        final int slot = environment.findFrameSlot(node.name);
        final var defaultValue = node.value.accept(this);

        // keyword arguments couldn't be passed to a block in case of destructuring single Array argument,
        // so assign default value directly
        return new WriteLocalVariableNode(slot, defaultValue);
    }

    @Override
    public RubyNode visitOptionalParameterNode(Nodes.OptionalParameterNode node) {
        final RubyNode readNode;
        final RubyNode defaultValue = node.value.accept(this);
        final int slot = environment.findFrameSlot(node.name);
        int minimum = index + 1 + parameters.posts.length;

        readNode = new ReadBlockOptionalArgumentFromArrayNode(readArrayNode, index, minimum, defaultValue);

        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitRestParameterNode(Nodes.RestParameterNode node) {
        // NOTE: we actually could do nothing if parameter is anonymous as far as
        // the only reason to store value of anonymous rest in a local variable is
        // to forward in but forwarding doesn't work in blocks
        final RubyNode readNode;

        int from = parameters.requireds.length + parameters.optionals.length;
        int to = -parameters.posts.length;
        readNode = ArraySliceNodeGen.create(from, to, readArrayNode);

        final String name = (node.name != null) ? node.name : TranslatorEnvironment.DEFAULT_REST_NAME;
        final int slot = environment.findFrameSlot(name);
        return new WriteLocalVariableNode(slot, readNode);
    }

    @Override
    public RubyNode visitImplicitRestNode(Nodes.ImplicitRestNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in #translate");
    }

    @Override
    public RubyNode visitKeywordRestParameterNode(Nodes.KeywordRestParameterNode node) {
        // NOTE: we actually could do nothing if parameter is anonymous
        //      as far as this translator handles a block parameters only,
        //      but anonymous keyword rest forwarding doesn't work in blocks
        final int slot;

        if (node.name != null) {
            slot = environment.findFrameSlot(node.name);
        } else {
            final String name = TranslatorEnvironment.DEFAULT_KEYWORD_REST_NAME;
            slot = environment.findFrameSlot(name); // declared by YARPLoadArgumentsTranslator
        }

        // keyword arguments couldn't be passed to a block in case of destructuring single Array argument,
        // so immediately assign `{}` value
        final var valueNode = HashLiteralNode.create(RubyNode.EMPTY_ARRAY, language);
        return new WriteLocalVariableNode(slot, valueNode);
    }

    @Override
    public RubyNode visitBlockParameterNode(Nodes.BlockParameterNode node) {
        final String name;

        if (node.name == null) {
            // def a(&)
            name = TranslatorEnvironment.DEFAULT_BLOCK_NAME;
        } else {
            name = node.name;
        }

        final int slot = environment.findFrameSlot(name);
        return new SaveMethodBlockNode(slot);
    }

    @Override
    public RubyNode visitNoBlockParameterNode(NoBlockParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("&nil is Ruby 4.0+");
    }

    @Override
    protected RubyNode defaultVisit(Nodes.Node node) {
        // translate default values of optional positional and keyword arguments
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

}
