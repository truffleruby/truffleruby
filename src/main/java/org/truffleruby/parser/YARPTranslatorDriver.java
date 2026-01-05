/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.prism.ParsingOptions;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Split;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.SetBindingFrameForEvalNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.DataNode;
import org.truffleruby.language.EmitWarningsNode;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyEvalRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.RubyTopLevelRootNode;
import org.truffleruby.language.SetTopLevelBindingNode;
import org.truffleruby.language.arguments.MissingArgumentBehavior;
import org.truffleruby.language.arguments.ReadPreArgumentNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.locals.FrameDescriptorNamesIterator;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.options.Options;
import org.truffleruby.shared.Metrics;
import org.prism.Nodes;
import org.prism.ParseResult;
import org.prism.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public final class YARPTranslatorDriver {

    private static final byte[][] EMPTY_BYTE_ARRAY_ARRAY = new byte[0][];
    private static final ParsingOptions.Forwarding[] EMPTY_FORWARDING_ARRAY = new ParsingOptions.Forwarding[0];
    private static final ParsingOptions.Scope[] EMPTY_SCOPE_ARRAY = new ParsingOptions.Scope[0];
    private static final ParsingOptions.Scope EMPTY_SCOPE = new ParsingOptions.Scope(EMPTY_BYTE_ARRAY_ARRAY,
            EMPTY_FORWARDING_ARRAY);

    private final RubyContext context;
    private final RubyLanguage language;
    private ParseEnvironment parseEnvironment;

    public YARPTranslatorDriver(RubyContext context) {
        this.context = context;
        this.language = context.getLanguageSlow();
    }

    public RootCallTarget parse(RubySource rubySource, ParserContext parserContext, String[] argumentNames,
            MaterializedFrame parentFrame, LexicalScope staticLexicalScope, Node currentNode) {
        return parse(rubySource, parserContext, argumentNames, parentFrame, staticLexicalScope, currentNode, null);
    }

    public RootCallTarget parse(RubySource rubySource, ParserContext parserContext, String[] argumentNames,
            MaterializedFrame parentFrame, LexicalScope staticLexicalScope, Node currentNode, ParseResult parseResult) {
        byte[] sourceBytes = rubySource.getBytes();
        this.parseEnvironment = new ParseEnvironment(language, rubySource, parserContext, currentNode);

        assert rubySource.isEval() == parserContext.isEval();

        if (parserContext.isTopLevel() != (parentFrame == null)) {
            throw CompilerDirectives.shouldNotReachHere(
                    "A frame should be given iff the context is not toplevel: " + parserContext + " " + parentFrame);
        }

        if (!rubySource.getEncoding().isAsciiCompatible) {
            throw new RaiseException(context, context.getCoreExceptions()
                    .argumentError(rubySource.getEncoding() + " is not ASCII compatible", currentNode));
        }

        final Source source = rubySource.getSource();
        final TranslatorEnvironment parentEnvironment;

        // local variables in each lexical scope
        final List<List<String>> localsInScopes = new ArrayList<>();

        // prepare locals in scopes
        int blockDepth = 0;
        if (parentFrame != null) {
            MaterializedFrame frame = parentFrame;

            while (frame != null) {
                ArrayList<String> names = new ArrayList<>();

                for (Object identifier : FrameDescriptorNamesIterator.iterate(frame.getFrameDescriptor())) {
                    if (identifier instanceof String name) {
                        names.add(name.intern()); // intern() for footprint
                    }
                }

                localsInScopes.add(names);
                frame = RubyArguments.getDeclarationFrame(frame);
                blockDepth++;
            }

            parentEnvironment = environmentForFrame(context, parentFrame, blockDepth - 1);
        } else {
            parentEnvironment = null;
        }

        // there could be an outer lexical scope that may have its own local variables -
        // so they should be passed to parser as well
        if (argumentNames != null) {
            localsInScopes.add(Arrays.asList(argumentNames));
        }

        // Parse to the YARP AST
        final RubyDeferredWarnings rubyWarnings = new RubyDeferredWarnings();
        final String sourcePath = rubySource.getSourcePath(language).intern();
        final Options options;

        if (parserContext == ParserContext.TOP_LEVEL_FIRST) {
            options = context.getOptions();
        } else {
            options = null;
        }

        if (parseResult == null) {
            printParseTranslateExecuteMetric("before-parsing", context, source);
            parseResult = context.getMetricsProfiler().callWithMetrics(
                    "parsing",
                    source.getName(),
                    () -> parseToYARPAST(rubySource, sourcePath, sourceBytes, localsInScopes,
                            language.options.FROZEN_STRING_LITERALS, options, parserContext));
            printParseTranslateExecuteMetric("after-parsing", context, source);
        }

        parseEnvironment.yarpSource = parseResult.source;

        handleWarningsErrorsPrimitives(language, context, parseResult, rubySource, sourcePath, parseEnvironment,
                rubyWarnings);

        var node = parseResult.value;

        final SourceSection sourceSection = sourceBytes.length == 0
                ? source.createUnavailableSection()
                : source.createSection(0, sourceBytes.length);

        final String modulePath = staticLexicalScope == null || staticLexicalScope == context.getRootLexicalScope()
                ? null
                : staticLexicalScope.getLiveModule().getName();
        final String methodName = getMethodName(parserContext, parentFrame);
        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                sourceSection,
                language.singleContext ? staticLexicalScope : null,
                parserContext.isTopLevel() ? Arity.MODULE_BODY : Arity.NO_ARGUMENTS,
                methodName,
                0,
                methodName,
                null,
                null);

        final boolean topLevel = parserContext.isTopLevel();
        final boolean isModuleBody = topLevel;
        final TranslatorEnvironment environment = new TranslatorEnvironment(
                parentEnvironment,
                parseEnvironment,
                parseEnvironment.allocateReturnID(),
                true,
                isModuleBody,
                sharedMethodInfo,
                sharedMethodInfo.getMethodNameForNotBlock(),
                blockDepth,
                null,
                null,
                modulePath);

        // Declare arguments as local variables in the top-level environment - we'll put the values there in a prelude
        if (argumentNames != null) {
            for (String name : argumentNames) {
                environment.declareVar(name);
            }
        }

        // Translate to Ruby Truffle nodes

        // use source encoding detected by manually, before source file is fully parsed
        final YARPTranslator translator = new YARPTranslator(environment, rubyWarnings);

        RubyNode truffleNode;
        printParseTranslateExecuteMetric("before-translate", context, source);
        try {
            truffleNode = context.getMetricsProfiler().callWithMetrics(
                    "translating",
                    source.getName(),
                    () -> node.accept(translator));
        } finally {
            printParseTranslateExecuteMetric("after-translate", context, source);
        }

        // Load arguments
        if (argumentNames != null && argumentNames.length > 0) {
            final List<RubyNode> sequence = new ArrayList<>();

            for (int n = 0; n < argumentNames.length; n++) {
                final String name = argumentNames[n];
                final RubyNode readNode = YARPTranslator
                        .profileArgument(
                                language,
                                new ReadPreArgumentNode(n, false, MissingArgumentBehavior.NIL));
                final int slot = environment.findFrameSlot(name);
                sequence.add(new WriteLocalVariableNode(slot, readNode));
            }

            sequence.add(truffleNode);
            truffleNode = YARPTranslator.sequence(sequence.toArray(RubyNode.EMPTY_ARRAY));
        }

        // Load flip-flop states

        if (environment.getFlipFlopStates().size() > 0) {
            truffleNode = YARPTranslator.sequence(YARPTranslator.initFlipFlopStates(environment), truffleNode);
        }

        RubyNode[] beginBlocks = translator.getBeginBlocks();

        // add BEGIN {} blocks at the very beginning of the program
        if (beginBlocks.length > 0) {
            RubyNode[] sequence = Arrays.copyOf(beginBlocks, beginBlocks.length + 1);
            sequence[sequence.length - 1] = truffleNode;
            truffleNode = YARPTranslator.sequence(sequence);
        }

        final RubyNode writeSelfNode = YARPTranslator.loadSelf(language);
        truffleNode = YARPTranslator.sequence(writeSelfNode, truffleNode);

        if (!rubyWarnings.warnings.isEmpty()) {
            truffleNode = YARPTranslator.sequence(new EmitWarningsNode(rubyWarnings), truffleNode);
        }

        // Top-level exception handling

        if (parserContext == ParserContext.TOP_LEVEL_FIRST) {
            truffleNode = YARPTranslator.sequence(new SetTopLevelBindingNode(), truffleNode);

            if (parseResult.dataLocation != null) {
                // startOffset - location of beginning of __END__, not ending
                int offset = parseResult.dataLocation.startOffset + "__END__".length();

                if (offset < source.getLength()) {
                    // There are characters after __END__.
                    // Handle optional "\n" after __END__ - it isn't part of DATA.
                    // Don't handle \r\n as far as Windows isn't supported.
                    offset += 1;
                }

                truffleNode = YARPTranslator.sequence(new DataNode(offset), truffleNode);
            }
        }

        final FrameDescriptor frameDescriptor = environment.computeFrameDescriptor();

        if (parserContext == ParserContext.EVAL &&
                BindingNodes.assignsNewUserVariables(frameDescriptor)) {
            truffleNode = new SetBindingFrameForEvalNode(frameDescriptor, truffleNode);
        }

        final RubyRootNode rootNode;
        if (parserContext.isTopLevel()) {
            rootNode = new RubyTopLevelRootNode(
                    language,
                    sourceSection,
                    frameDescriptor,
                    sharedMethodInfo,
                    truffleNode,
                    Split.HEURISTIC,
                    environment.getReturnID(),
                    Arity.ANY_ARGUMENTS);
        } else {
            rootNode = new RubyEvalRootNode(
                    language,
                    sourceSection,
                    frameDescriptor,
                    sharedMethodInfo,
                    truffleNode,
                    Split.HEURISTIC,
                    environment.getReturnID());
        }

        return rootNode.getCallTarget();
    }

    private String getMethodName(ParserContext parserContext, MaterializedFrame parentFrame) {
        if (parserContext.isTopLevel()) {
            return parserContext.getTopLevelName();
        } else {
            if (parentFrame != null) {
                return RubyArguments.getMethod(parentFrame).getName();
            } else {
                throw new UnsupportedOperationException(
                        "Could not determine the method name for parser context " + parserContext);
            }
        }
    }

    public static ParseResult parseToYARPAST(RubySource rubySource, String sourcePath, byte[] sourceBytes,
            List<List<String>> localsInScopes, boolean frozenStringLiteral, Options cliOptions,
            ParserContext parserContext) {
        TruffleSafepoint.poll(null);

        final byte[] filepath = sourcePath.getBytes(Encodings.FILESYSTEM_CHARSET);
        int line = rubySource.getLineOffset() + 1;
        byte[] encoding = StringOperations.encodeAsciiBytes(rubySource.getEncoding().toString()); // encoding name is supposed to contain only ASCII characters
        var version = ParsingOptions.SyntaxVersion.V3_4;
        // Allow Prism to handle encoding magic comments.
        // Magic comments are already detected and rubySource's encoding takes them into account,
        // but encodingLocked is intended for a niche use-case and is supposed to be false in all other cases.
        final boolean encodingLocked = false;
        final boolean mainScript = parserContext == ParserContext.TOP_LEVEL_FIRST;
        final boolean partialScript = false;

        // Prism handles command line options (-n, -l, -a, -p) on its own
        final EnumSet<ParsingOptions.CommandLine> commandline;

        if (cliOptions != null && cliOptions.GETS_LOOP) {
            List<ParsingOptions.CommandLine> yarpCliOptions = new ArrayList<>();
            yarpCliOptions.add(ParsingOptions.CommandLine.N);

            if (cliOptions.PRINT_LOOP) {
                yarpCliOptions.add(ParsingOptions.CommandLine.P);
            }

            if (cliOptions.SPLIT_LOOP) {
                yarpCliOptions.add(ParsingOptions.CommandLine.A);
            }

            if (cliOptions.CHOMP_LOOP) {
                yarpCliOptions.add(ParsingOptions.CommandLine.L);
            }

            commandline = EnumSet.copyOf(yarpCliOptions);
        } else {
            // EnumSet.copyOf method requires a collection to be non-empty
            commandline = EnumSet.noneOf(ParsingOptions.CommandLine.class);
        }

        ParsingOptions.Scope[] scopes;

        if (!localsInScopes.isEmpty()) {
            int scopesCount = localsInScopes.size();
            // Add one empty extra scope at the end to have local variables treated by Prism
            // as declared in the outer scope.
            // See https://github.com/ruby/prism/issues/2327 and https://github.com/ruby/prism/issues/2192
            scopes = new ParsingOptions.Scope[scopesCount + 1];

            for (int i = 0; i < scopesCount; i++) {
                // Local variables are in order from inner scope to outer one, but Prism expects order from outer to inner.
                // So we need to reverse the order
                var namesList = localsInScopes.get(scopesCount - 1 - i);
                List<String> publicNamesList = new ArrayList<>();
                var forwarding = EnumSet.noneOf(ParsingOptions.Forwarding.class);

                for (var name : namesList) {
                    if (!BindingNodes.isHiddenVariable(name)) {
                        publicNamesList.add(name);
                    } else {
                        switch (name) {
                            case TranslatorEnvironment.DEFAULT_REST_NAME ->
                                forwarding.add(ParsingOptions.Forwarding.POSITIONAL);
                            case TranslatorEnvironment.DEFAULT_KEYWORD_REST_NAME ->
                                forwarding.add(ParsingOptions.Forwarding.KEYWORD);
                            case TranslatorEnvironment.DEFAULT_BLOCK_NAME ->
                                forwarding.add(ParsingOptions.Forwarding.BLOCK);
                            case TranslatorEnvironment.FORWARDED_REST_NAME,
                                    TranslatorEnvironment.FORWARDED_KEYWORD_REST_NAME,
                                    TranslatorEnvironment.FORWARDED_BLOCK_NAME ->
                                forwarding.add(ParsingOptions.Forwarding.ALL);
                        }
                    }
                }

                byte[][] namesBytes = new byte[publicNamesList.size()][];
                int j = 0;
                for (var name : publicNamesList) {
                    namesBytes[j] = TStringUtils.javaStringToBytes(name, rubySource.getEncoding());
                    j++;
                }

                var forwardingArray = forwarding.toArray(EMPTY_FORWARDING_ARRAY);
                scopes[i] = new ParsingOptions.Scope(namesBytes, forwardingArray);
            }

            scopes[scopes.length - 1] = EMPTY_SCOPE;
        } else {
            scopes = EMPTY_SCOPE_ARRAY;
        }

        byte[] parsingOptions = ParsingOptions.serialize(filepath, line, encoding, frozenStringLiteral, commandline,
                version, encodingLocked, mainScript, partialScript, scopes);
        byte[] serializedBytes = Parser.parseAndSerialize(sourceBytes, parsingOptions);

        return YARPLoader.load(serializedBytes, sourceBytes, rubySource);
    }

    public static void handleWarningsErrorsPrimitives(RubyLanguage language, RubyContext context,
            ParseResult parseResult, RubySource rubySource, String sourcePath, ParseEnvironment parseEnvironment,
            RubyDeferredWarnings rubyWarnings) {

        final ParseResult.Error[] errors = parseResult.errors;

        // collect warnings generated by the parser
        for (ParseResult.Warning warning : parseResult.warnings) {
            Nodes.Location location = warning.location;
            SourceSection section = rubySource.getSource().createSection(location.startOffset, location.length);

            int lineNumber = section.getStartLine() + rubySource.getLineOffset();

            switch (warning.level) {
                case WARNING_DEFAULT -> rubyWarnings.warn(sourcePath, lineNumber, warning.message);
                case WARNING_VERBOSE -> rubyWarnings.warning(sourcePath, lineNumber, warning.message);
            }
        }

        if (errors.length != 0) {
            // print warnings immediately
            // if there is no syntax error they will be printed in runtime
            if (!rubyWarnings.warnings.isEmpty()) {
                EmitWarningsNode.printWarnings(context, rubyWarnings);
            }

            // Handle only the first reported syntax error.
            // The order of errors should be deterministic,
            // but it isn't guarantied that the first error is the most specific/relevant to user
            ParseResult.Error error = errors[0];

            Nodes.Location location = error.location;
            SourceSection section = rubySource.getSource().createSection(location.startOffset, location.length);

            String message = language.fileLine(section) + ": " + error.message;
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxErrorAlreadyWithFileLine(message, null, section));
        }

        boolean allowTruffleRubyPrimitives = false;
        for (var magicComment : parseResult.magicComments) {
            String name = rubySource.getTStringWithEncoding()
                    .substring(magicComment.keyLocation.startOffset, magicComment.keyLocation.length).toJavaString();
            String value = rubySource.getTStringWithEncoding()
                    .substring(magicComment.valueLocation.startOffset, magicComment.valueLocation.length)
                    .toJavaString();
            // encoding magic comment is handled manually and available as RubySource#encoding

            // check the `primitive` TruffleRuby specific magic comment
            if (MagicCommentParser.isMagicTruffleRubyPrimitivesComment(name)) {
                allowTruffleRubyPrimitives = value.equalsIgnoreCase("true");
            }
        }
        parseEnvironment.allowTruffleRubyPrimitives = allowTruffleRubyPrimitives;
    }

    private TranslatorEnvironment environmentForFrame(RubyContext context, MaterializedFrame frame, int blockDepth) {
        if (frame == null) {
            return null;
        } else {
            final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                    CoreLibrary.JAVA_CORE_SOURCE_SECTION,
                    language.singleContext ? context.getRootLexicalScope() : null,
                    Arity.NO_ARGUMENTS,
                    "<unused>",
                    0,
                    "<unused>",
                    "external",
                    null);
            final MaterializedFrame parent = RubyArguments.getDeclarationFrame(frame);
            assert (blockDepth == 0) == (parent == null);
            boolean isModuleBody = blockDepth == 0 &&
                    RubyArguments.getMethod(frame).getSharedMethodInfo().isModuleBody();

            return new TranslatorEnvironment(
                    environmentForFrame(context, parent, blockDepth - 1),
                    parseEnvironment,
                    parseEnvironment.allocateReturnID(),
                    true,
                    isModuleBody,
                    sharedMethodInfo,
                    sharedMethodInfo.getMethodName(),
                    blockDepth,
                    null,
                    frame.getFrameDescriptor(),
                    "<unused>");
        }
    }

    public static void printParseTranslateExecuteMetric(String id, RubyContext context, Source source) {
        if (Metrics.getMetricsTime()) {
            if (context.getOptions().METRICS_TIME_PARSING_FILE) {
                String name = context.getLanguageSlow().getSourcePath(source);
                int lastSlash = name.lastIndexOf('/');
                int lastDot = name.lastIndexOf('.');
                if (lastSlash >= 0 && lastDot >= 0 && lastSlash + 1 < lastDot) {
                    name = name.substring(lastSlash + 1, lastDot);
                }
                Metrics.printTime(id + "-" + name);
            } else if (context.getCoreLibrary().isLoadingRubyCore()) {
                // Only show times for core (the biggest contributor) to avoid multiple metrics with
                // the same name, which is not supported in mx_truffleruby_benchmark.py.
                Metrics.printTime(id + "-core");
            }
        }
    }

}
