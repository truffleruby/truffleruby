/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
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

import com.oracle.truffle.api.nodes.Node;
import org.ruby_lang.prism.AbstractNodeVisitor;
import org.ruby_lang.prism.Nodes;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.SequenceNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.literal.BooleanLiteralNode;
import org.truffleruby.language.literal.NilLiteralNode;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class YARPBaseTranslator extends AbstractNodeVisitor<RubyNode> {

    public static final Nodes.Node[] EMPTY_NODE_ARRAY = Nodes.Node.EMPTY_ARRAY;
    public static final Nodes.OptionalParameterNode[] EMPTY_OPTIONAL_PARAMETER_NODE_ARRAY = {};
    public static final Nodes.BlockLocalVariableNode[] EMPTY_BLOCK_LOCAL_VARIABLE_NODE_ARRAY = {};

    public static final Nodes.ParametersNode ZERO_PARAMETERS_NODE = new Nodes.ParametersNode(0, 0, EMPTY_NODE_ARRAY,
            EMPTY_OPTIONAL_PARAMETER_NODE_ARRAY, null, EMPTY_NODE_ARRAY, EMPTY_NODE_ARRAY, null, null);

    public static final short NO_FLAGS = 0;
    public static final int NO_FRAME_ON_STACK_MARKER = -1;

    protected final TranslatorEnvironment environment;

    // For convenient/concise access, actually redundant with environment.getParseEnvironment()
    protected final ParseEnvironment parseEnvironment;
    protected final RubyLanguage language;
    protected final RubySource rubySource;
    protected final Source source;
    protected final RubyEncoding sourceEncoding;
    protected final Node currentNode;

    public YARPBaseTranslator(TranslatorEnvironment environment) {
        this.environment = Objects.requireNonNull(environment);
        this.parseEnvironment = environment.getParseEnvironment();
        this.language = parseEnvironment.language;
        this.rubySource = parseEnvironment.rubySource;
        this.source = rubySource.getSource();
        this.sourceEncoding = rubySource.getEncoding();
        this.currentNode = parseEnvironment.currentNode;
    }

    public final TranslatorEnvironment getEnvironment() {
        return environment;
    }

    protected RuntimeException fail(Nodes.Node node) {
        var context = RubyLanguage.getCurrentContext();
        String code = toString(node);
        var message = this.getClass().getSimpleName() + " does not know how to translate " +
                node.getClass().getSimpleName() + " at " + language.fileLine(getSourceSection(node)) +
                "\nCode snippet:\n" + code + "\nPrism AST:\n" + node;
        throw new RaiseException(context,
                context.getCoreExceptions().syntaxError(message, null, getSourceSection(node)));
        // throw CompilerDirectives.shouldNotReachHere(message); // Useful for debugging
    }

    @Override
    protected RubyNode defaultVisit(Nodes.Node node) {
        throw fail(node);
    }

    protected static RubyNode[] createArray(int size) {
        return size == 0 ? RubyNode.EMPTY_ARRAY : new RubyNode[size];
    }

    protected final RubyNode translateNodeOrNil(Nodes.Node node) {
        final RubyNode rubyNode;
        if (node == null) {
            rubyNode = new NilLiteralNode();
        } else {
            rubyNode = node.accept(this);
        }
        return rubyNode;
    }

    protected final RubyNode translateNodeOrTrue(Nodes.Node node) {
        final RubyNode rubyNode;
        if (node == null) {
            rubyNode = new BooleanLiteralNode(true);
        } else {
            rubyNode = node.accept(this);
        }
        return rubyNode;
    }

    protected final RubyContextSourceNode createCallNode(RubyNode receiver, String method, RubyNode... arguments) {
        return createCallNode(true, receiver, method, arguments);
    }

    protected final RubyContextSourceNode createCallNode(boolean ignoreVisibility, RubyNode receiver, String method,
            RubyNode... arguments) {
        return createCallNodeWithBlock(ignoreVisibility, receiver, method, null, arguments);
    }

    protected final RubyContextSourceNode createCallNodeWithBlock(boolean ignoreVisibility, RubyNode receiver,
            String method, RubyNode block, RubyNode... arguments) {
        var parameters = new RubyCallNodeParameters(
                receiver,
                method,
                block,
                NoKeywordArgumentsDescriptor.INSTANCE,
                arguments,
                ignoreVisibility);
        return language.coreMethodAssumptions.createCallNode(parameters);
    }

    protected static Nodes.CallNode callNode(Nodes.Node location, Nodes.Node receiver, String methodName,
            Nodes.Node... arguments) {
        return callNode(location, NO_FLAGS, receiver, methodName, arguments);
    }

    protected static Nodes.CallNode callNode(Nodes.Node location, short flags, Nodes.Node receiver, String methodName,
            Nodes.Node... arguments) {
        return new Nodes.CallNode(location.startOffset, location.length, flags, receiver, methodName,
                new Nodes.ArgumentsNode(location.startOffset, location.length, NO_FLAGS, arguments), null);
    }

    protected final TruffleString toTString(Nodes.Node node) {
        return TruffleString.fromByteArrayUncached(rubySource.getBytes(), node.startOffset, node.length,
                sourceEncoding.tencoding, false);
    }

    protected final TruffleString toTString(String string) {
        return TStringUtils.fromJavaString(string, sourceEncoding);
    }

    protected final String toString(Nodes.Node node) {
        return TStringUtils.toJavaStringOrThrow(toTString(node), sourceEncoding);
    }

    protected final TruffleString toTString(byte[] bytes) {
        return TruffleString.fromByteArrayUncached(bytes, sourceEncoding.tencoding, false);
    }

    protected final String toString(byte[] bytes) {
        return TStringUtils.toJavaStringOrThrow(toTString(bytes), sourceEncoding);
    }

    // Also useful for debugging to show the file and line of a node
    protected final SourceSection getSourceSection(Nodes.Node yarpNode) {
        if (yarpNode.length == 0 && yarpNode.startOffset == 0) {
            return source.createUnavailableSection();
        } else {
            return source.createSection(yarpNode.startOffset, yarpNode.length);
        }
    }

    public final RubyNode assignPositionAndFlags(Nodes.Node yarpNode, RubyNode rubyNode) {
        assignPositionOnly(yarpNode, rubyNode);
        copyNewlineFlag(yarpNode, rubyNode);
        return rubyNode;
    }

    public final RubyNode assignPositionAndFlagsIfMissing(Nodes.Node yarpNode, RubyNode rubyNode) {
        if (rubyNode.hasSource()) {
            return rubyNode;
        }

        assignPositionOnly(yarpNode, rubyNode);
        copyNewlineFlag(yarpNode, rubyNode);
        return rubyNode;
    }

    protected static void assignPositionOnly(Nodes.Node yarpNode, RubyNode rubyNode) {
        if (yarpNode.length == 0 && yarpNode.startOffset == 0) {
            // (0, 0) means unknown location, so do not set the source section and keep the node as !hasSource()
        } else {
            rubyNode.unsafeSetSourceSection(yarpNode.startOffset, yarpNode.length);
        }
    }

    // assign position based on a list of nodes (arguments list, exception classes list in a rescue section, etc)
    protected final void assignPositionOnly(Nodes.Node[] nodes, RubyNode rubyNode) {
        final Nodes.Node first = nodes[0];
        final Nodes.Node last = ArrayUtils.getLast(nodes);

        final int length = last.endOffset() - first.startOffset;
        assert length > 0 : length;
        rubyNode.unsafeSetSourceSection(first.startOffset, length);
    }

    protected final void copyNewlineFlag(Nodes.Node yarpNode, RubyNode rubyNode) {
        if (yarpNode.hasNewLineFlag()) {
            TruffleSafepoint.poll(currentNode);

            if (parseEnvironment.isCoverageEnabled()) {
                rubyNode.unsafeSetIsCoverageLine();
                int startLine = parseEnvironment.yarpSource.line(yarpNode.startOffset);
                language.coverageManager.setLineHasCode(source, startLine);
            }

            rubyNode.unsafeSetIsNewLine();
        }
    }

    protected void copyNewLineFlag(Nodes.Node source, Nodes.Node target) {
        target.setNewLineFlag(source.hasNewLineFlag());
    }

    protected static RubyNode sequence(Nodes.Node yarpNode, RubyNode... sequence) {
        assert !yarpNode.hasNewLineFlag() : "Expected node passed to sequence() to not have a newline flag";

        RubyNode sequenceNode = sequence(sequence);

        if (!sequenceNode.hasSource()) {
            assignPositionOnly(yarpNode, sequenceNode);
        }

        return sequenceNode;
    }

    protected static RubyNode sequence(RubyNode... sequence) {
        final List<RubyNode> flattened = flatten(sequence);

        if (flattened.isEmpty()) {
            return new NilLiteralNode();
        } else if (flattened.size() == 1) {
            return flattened.get(0);
        } else {
            final RubyNode[] flatSequence = flattened.toArray(RubyNode.EMPTY_ARRAY);
            return new SequenceNode(flatSequence);
        }
    }

    private static List<RubyNode> flatten(RubyNode[] sequence) {
        return flattenFromN(sequence, 0);
    }

    private static List<RubyNode> flattenFromN(RubyNode[] sequence, int n) {
        final List<RubyNode> flattened = new ArrayList<>();

        for (; n < sequence.length; n++) {
            final boolean lastNode = n == sequence.length - 1;
            final RubyNode node = sequence[n];

            if (node == null) {
                continue;
            }

            if (node instanceof SequenceNode) {
                flattened.addAll(flatten(((SequenceNode) node).getSequence()));
            } else if (node.canSubsumeFollowing() && !lastNode) {
                List<RubyNode> rest = flattenFromN(sequence, n + 1);
                if (rest.size() == 1) {
                    flattened.add(node.subsumeFollowing(rest.get(0)));
                } else {
                    flattened.add(node.subsumeFollowing(new SequenceNode(rest.toArray(RubyNode.EMPTY_ARRAY))));
                }
                return flattened;
            } else {
                flattened.add(node);
            }
        }

        return flattened;
    }

    protected final RubyNode[] translate(Nodes.Node[] nodes) {
        if (nodes.length == 0) {
            return RubyNode.EMPTY_ARRAY;
        }

        RubyNode[] rubyNodes = new RubyNode[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            rubyNodes[i] = nodes[i].accept(this);
        }

        return rubyNodes;
    }

    public static boolean hasKeywordArguments(Nodes.ParametersNode node) {
        return node.keywords.length != 0 || hasKeywordsRest(node);
    }

    // This is more error-prone than one might expect due to NoKeywordsParameterNode,
    // so we provide a helper and reviewed all usages of ParametersNode#keyword_rest.
    public static boolean hasKeywordsRest(Nodes.ParametersNode node) {
        return node.keyword_rest != null && !(node.keyword_rest instanceof Nodes.NoKeywordsParameterNode);
    }

    protected final void declareLocalVariables(String[] locals) {
        // YARP doesn't add hidden locals for rest/keyrest/block anonymous parameters or ...
        for (String name : locals) {
            assert !(name.equals("*") || name.equals("**") || name.equals("&") || name.equals("...")) : name;
            environment.declareVar(name);
        }
    }

}
