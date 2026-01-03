/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.specific.EUCJPEncoding;
import org.graalvm.shadowed.org.jcodings.specific.Windows_31JEncoding;
import org.prism.Nodes.ConstantReadNode;
import org.prism.Nodes.Node;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.PrimitiveNodeConstructor;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.IsNilNode;
import org.truffleruby.core.array.ArrayConcatNode;
import org.truffleruby.core.array.ArrayLiteralNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.cast.HashCastNodeGen;
import org.truffleruby.core.cast.SplatCastNode;
import org.truffleruby.core.cast.SplatCastNodeGen;
import org.truffleruby.core.cast.StringToSymbolNodeGen;
import org.truffleruby.core.cast.ToProcNodeGen;
import org.truffleruby.core.cast.ToSNode;
import org.truffleruby.core.cast.ToSNodeGen;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.hash.ConcatHashLiteralNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.module.ModuleNodes;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.range.RangeNodesFactory;
import org.truffleruby.core.range.RubyIntRange;
import org.truffleruby.core.range.RubyLongRange;
import org.truffleruby.core.regexp.ClassicRegexp;
import org.truffleruby.core.regexp.InterpolatedRegexpNodeGen;
import org.truffleruby.core.regexp.MatchDataNodes;
import org.truffleruby.core.regexp.RegexpOptions;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.rescue.AssignRescueVariableNode;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.InterpolatedStringNode;
import org.truffleruby.core.string.KCode;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.debug.ChaosNode;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.RubyTopLevelRootNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.ProfileArgumentNodeGen;
import org.truffleruby.language.arguments.ReadSelfNode;
import org.truffleruby.language.constants.ReadConstantNode;
import org.truffleruby.language.constants.ReadConstantWithDynamicScopeNode;
import org.truffleruby.language.constants.ReadConstantWithLexicalScopeNode;
import org.truffleruby.language.constants.WriteConstantNode;
import org.truffleruby.language.control.AndNodeGen;
import org.truffleruby.language.control.BreakNode;
import org.truffleruby.language.control.DeferredRaiseException;
import org.truffleruby.language.control.DynamicReturnNode;
import org.truffleruby.language.control.FrameOnStackNode;
import org.truffleruby.language.control.IfElseNode;
import org.truffleruby.language.control.IfElseNodeGen;
import org.truffleruby.language.control.IfNodeGen;
import org.truffleruby.language.control.InvalidReturnNode;
import org.truffleruby.language.control.LocalReturnNode;
import org.truffleruby.language.control.NextNode;
import org.truffleruby.language.control.NoMatchingPatternNodeGen;
import org.truffleruby.language.control.NotNodeGen;
import org.truffleruby.language.control.OnceNode;
import org.truffleruby.language.control.OrLazyValueDefinedNodeGen;
import org.truffleruby.language.control.OrNodeGen;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoNode;
import org.truffleruby.language.control.RetryNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.control.UnlessNodeGen;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.WhileNode;
import org.truffleruby.language.control.WhileNodeFactory;
import org.truffleruby.language.defined.DefinedNode;
import org.truffleruby.language.defined.DefinedWrapperNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.exceptions.EnsureNodeGen;
import org.truffleruby.language.exceptions.RescueClassesNode;
import org.truffleruby.language.exceptions.RescueNode;
import org.truffleruby.language.exceptions.RescueSplatNode;
import org.truffleruby.language.exceptions.RescueStandardErrorNode;
import org.truffleruby.language.exceptions.TryNodeGen;
import org.truffleruby.language.globals.AliasGlobalVarNode;
import org.truffleruby.language.globals.ReadGlobalVariableNodeGen;
import org.truffleruby.language.globals.ReadMatchReferenceNodes;
import org.truffleruby.language.globals.WriteGlobalVariableNodeGen;
import org.truffleruby.language.literal.BooleanLiteralNode;
import org.truffleruby.language.literal.FloatLiteralNode;
import org.truffleruby.language.literal.FrozenStringLiteralNode;
import org.truffleruby.language.literal.IntegerFixnumLiteralNode;
import org.truffleruby.language.literal.LongFixnumLiteralNode;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.literal.ObjectClassLiteralNode;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.literal.StringLiteralNode;
import org.truffleruby.language.literal.TruffleInternalModuleLiteralNode;
import org.truffleruby.language.locals.FindDeclarationVariableNodes.FrameSlotAndDepth;
import org.truffleruby.language.locals.FlipFlopNodeGen;
import org.truffleruby.language.locals.InitFlipFlopSlotNode;
import org.truffleruby.language.locals.ReadLocalNode;
import org.truffleruby.language.locals.WriteLocalNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.CatchBreakNode;
import org.truffleruby.language.methods.LiteralMethodDefinitionNode;
import org.truffleruby.language.methods.ModuleBodyDefinition;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.DefineClassNode;
import org.truffleruby.language.objects.DefineModuleNode;
import org.truffleruby.language.objects.DefineModuleNodeGen;
import org.truffleruby.language.objects.DynamicLexicalScopeNode;
import org.truffleruby.language.objects.GetDynamicLexicalScopeNode;
import org.truffleruby.language.objects.InsideModuleDefinitionNode;
import org.truffleruby.language.objects.LexicalScopeNode;
import org.truffleruby.language.objects.ReadInstanceVariableNode;
import org.truffleruby.language.objects.RunModuleDefinitionNode;
import org.truffleruby.language.objects.SelfNode;
import org.truffleruby.language.objects.SingletonClassNode.SingletonClassASTNode;
import org.truffleruby.language.objects.SingletonClassNodeGen.SingletonClassASTNodeGen;
import org.truffleruby.language.objects.WriteInstanceVariableNodeGen;
import org.truffleruby.language.objects.classvariables.ReadClassVariableNode;
import org.truffleruby.language.objects.classvariables.WriteClassVariableNode;
import org.prism.Nodes;
import org.truffleruby.language.supercall.ReadSuperArgumentsNode;
import org.truffleruby.language.supercall.ReadZSuperArgumentsNode;
import org.truffleruby.language.supercall.SuperCallNode;
import org.truffleruby.language.supercall.ZSuperOutsideMethodNode;
import org.truffleruby.language.yield.YieldExpressionNode;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import static org.truffleruby.parser.TranslatorEnvironment.DEFAULT_BLOCK_NAME;
import static org.truffleruby.parser.TranslatorEnvironment.DEFAULT_KEYWORD_REST_NAME;
import static org.truffleruby.parser.TranslatorEnvironment.DEFAULT_REST_NAME;
import static org.truffleruby.parser.TranslatorEnvironment.FORWARDED_BLOCK_NAME;
import static org.truffleruby.parser.TranslatorEnvironment.FORWARDED_KEYWORD_REST_NAME;
import static org.truffleruby.parser.TranslatorEnvironment.FORWARDED_REST_NAME;

/** Translate (or convert) AST provided by a parser (YARP parser) to Truffle AST.
 *
 * The main translator that delegates handling of some nodes (e.g. method or block definition) to helper translators.
 *
 * Every Prism node class is documented in the {@code org.prism.Nodes}. */
public class YARPTranslator extends YARPBaseTranslator {

    public static final int NO_FRAME_ON_STACK_MARKER = -1;

    public static final RescueNode[] EMPTY_RESCUE_NODE_ARRAY = new RescueNode[0];

    public Deque<Integer> frameOnStackMarkerSlotStack = new ArrayDeque<>();

    protected RubyDeferredWarnings rubyWarnings;

    /** whether a while-loop body is translated; needed to check correctness of operators like break/next/etc */
    private boolean translatingWhile = false;
    /** whether a for-loop body is translated; needed to enforce variables in the for-loop body to be declared outside
     * the for-loop */
    private boolean translatingForStatement = false;

    /** names of numbered parameters in procs */
    private static final String[] NUMBERED_PARAMETER_NAMES = {
            null,
            "_1",
            "_2",
            "_3",
            "_4",
            "_5",
            "_6",
            "_7",
            "_8",
            "_9"
    };

    static final String IT_PARAMETER_NAME = Layouts.TEMP_PREFIX + "it";

    /** all the encountered BEGIN {} blocks; they will be added finally at the beginning of the program AST */
    private final ArrayList<RubyNode> beginBlocks = new ArrayList<>();

    public YARPTranslator(TranslatorEnvironment environment, RubyDeferredWarnings rubyWarnings) {
        super(environment);
        this.rubyWarnings = rubyWarnings;
    }

    public RubyNode[] getBeginBlocks() {
        return beginBlocks.toArray(RubyNode.EMPTY_ARRAY);
    }

    /** enter point to translate a program; a single really public method */
    public RubyRootNode translate(Nodes.Node node) {
        var body = node.accept(this);
        var frameDescriptor = TranslatorEnvironment.newFrameDescriptorBuilderForMethod().build();
        var sourceSection = CoreLibrary.JAVA_CORE_SOURCE_SECTION;
        var sharedMethodInfo = new SharedMethodInfo(sourceSection, null, Arity.NO_ARGUMENTS, "<main>", 0, "<main>",
                null, null);
        return new RubyTopLevelRootNode(language, sourceSection, frameDescriptor, sharedMethodInfo, body,
                Split.HEURISTIC, null, Arity.NO_ARGUMENTS);
    }

    @Override
    public RubyNode visitAliasGlobalVariableNode(Nodes.AliasGlobalVariableNode node) {
        RubyNode rubyNode = new AliasGlobalVarNode(toString(node.old_name), toString(node.new_name));

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitAliasMethodNode(Nodes.AliasMethodNode node) {
        // expected InterpolatedSymbolNode (that should be evaluated in runtime)
        // or SymbolNode
        RubyNode rubyNode = new ModuleNodes.AliasKeywordNode(
                node.new_name.accept(this),
                node.old_name.accept(this));

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitAlternationPatternNode(Nodes.AlternationPatternNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitAndNode(Nodes.AndNode node) {
        RubyNode left = node.left.accept(this);
        RubyNode right = node.right.accept(this);

        RubyNode rubyNode = AndNodeGen.create(left, right);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitArgumentsNode(Nodes.ArgumentsNode node) {
        // returns either a single value or an array of multiple values
        final Nodes.Node[] values = node.arguments;

        if (values.length == 1) {
            return values[0].accept(this);
        }

        final RubyNode[] translatedValues = translate(values);
        final RubyNode rubyNode = ArrayLiteralNode.create(language, translatedValues);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitArrayNode(Nodes.ArrayNode node) {
        // to handle a splat operator properly (e.g [a, *b, c])
        final RubyNode rubyNode = translateExpressionsList(node.elements);

        // there are edge cases when node is already assigned a source section and flags (e.g. [*a])
        if (!rubyNode.hasSource()) {
            assignPositionAndFlags(node, rubyNode);
        }

        return rubyNode;
    }

    @Override
    public RubyNode visitArrayPatternNode(Nodes.ArrayPatternNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitAssocNode(Nodes.AssocNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitHashNode/getKeywordArgumentsDescriptor");
    }

    @Override
    public RubyNode visitAssocSplatNode(Nodes.AssocSplatNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitHashNode/getKeywordArgumentsDescriptor");
    }

    @Override
    public RubyNode visitBackReferenceReadNode(Nodes.BackReferenceReadNode node) {
        final RubyNode rubyNode = ReadGlobalVariableNodeGen.create(node.name);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitBeginNode(Nodes.BeginNode node) {
        RubyNode rubyNode;

        if (node.statements != null) {
            rubyNode = node.statements.accept(this);
        } else {
            rubyNode = new NilLiteralNode();
        }

        // fast path
        if (node.rescue_clause == null && node.ensure_clause == null) {
            assert node.else_clause == null;
            return rubyNode;
        }

        // begin/end block with rescue section(s)
        // ignore else section if there is no a rescue one,
        // because else branch without rescue branch is a syntax error
        if (node.rescue_clause != null) {

            final List<RescueNode> rescueNodes = new ArrayList<>();
            Nodes.RescueNode rescueClause = node.rescue_clause;

            // rescue clauses are organized into a linked list
            // traverse this linked list to translate and accumulate rescue nodes
            while (rescueClause != null) { // each rescue clause
                boolean canOmitBacktrace = language.options.BACKTRACES_OMIT_UNUSED &&
                        rescueClause.reference == null &&
                        (rescueClause.statements == null || (rescueClause.statements.body.length == 1 &&
                                isSideEffectFreeRescueExpression(rescueClause.statements.body[0])));

                if (rescueClause.exceptions.length != 0) {
                    // TODO: this duplicate rescue body 3 times for e.g. `rescue A, *b, C`, but we should avoid duplicating code
                    final ArrayList<Nodes.Node> exceptionNodes = new ArrayList<>();

                    for (Nodes.Node exceptionNode : rescueClause.exceptions) {

                        if (exceptionNode instanceof Nodes.SplatNode splatNode) {
                            if (!exceptionNodes.isEmpty()) {
                                // dump all the accumulated so far exception classes and clear the list
                                final RescueNode rescueNode = translateExceptionNodes(exceptionNodes, rescueClause,
                                        canOmitBacktrace);
                                rescueNodes.add(rescueNode);
                                exceptionNodes.clear();
                            }

                            final RubyNode splatTranslated = translateNodeOrNil(splatNode.expression);

                            RubyNode translatedBody;

                            if (rescueClause.reference != null) {
                                // translate body after reference as far as reference could be used inside body
                                // and if it's a local variable - it should be declared before reading
                                final RubyNode exceptionWriteNode = translateRescueException(rescueClause.reference);
                                translatedBody = translateNodeOrNil(rescueClause.statements);
                                translatedBody = sequence(rescueClause,
                                        exceptionWriteNode, translatedBody);
                            } else {
                                translatedBody = translateNodeOrNil(rescueClause.statements);
                            }

                            final RescueNode rescueNode = new RescueSplatNode(language, splatTranslated,
                                    translatedBody, canOmitBacktrace);
                            assignPositionAndFlags(splatNode, rescueNode);

                            rescueNodes.add(rescueNode);
                        } else {
                            // accumulate a list of exception classes (before a splat operator)
                            exceptionNodes.add(exceptionNode);
                        }
                    }

                    // process exception classes after the last splat operator
                    // or all the exception classes in a list if there is no splat operator
                    if (!exceptionNodes.isEmpty()) {
                        final RescueNode rescueNode = translateExceptionNodes(exceptionNodes, rescueClause,
                                canOmitBacktrace);
                        rescueNodes.add(rescueNode);
                    }
                } else {
                    // exception class isn't specified explicitly so use Ruby StandardError class

                    RubyNode translatedBody;

                    if (rescueClause.reference != null) {
                        // translate body after reference as far as reference could be used inside body
                        // and if it's a local variable - it should be declared before reading
                        RubyNode exceptionWriteNode = translateRescueException(rescueClause.reference);
                        translatedBody = translateNodeOrNil(rescueClause.statements);
                        translatedBody = sequence(rescueClause, exceptionWriteNode, translatedBody);
                    } else {
                        translatedBody = translateNodeOrNil(rescueClause.statements);
                    }

                    final RescueStandardErrorNode rescueNode = new RescueStandardErrorNode(translatedBody,
                            canOmitBacktrace);
                    assignPositionAndFlags(rescueClause, rescueNode);

                    rescueNodes.add(rescueNode);
                }

                rescueClause = rescueClause.subsequent;
            }

            RubyNode elsePart;

            if (node.else_clause == null) {
                elsePart = null;
            } else {
                elsePart = node.else_clause.accept(this);
            }

            rubyNode = TryNodeGen.create(
                    rubyNode,
                    rescueNodes.toArray(EMPTY_RESCUE_NODE_ARRAY),
                    elsePart);
            assignPositionAndFlags(node, rubyNode);
        }

        // with ensure section
        if (node.ensure_clause != null && node.ensure_clause.statements != null) {
            final RubyNode ensureBlock = node.ensure_clause.accept(this);
            rubyNode = EnsureNodeGen.create(rubyNode, ensureBlock);
            assignPositionOnly(node, rubyNode);
        }

        return rubyNode;
    }

    private RescueNode translateExceptionNodes(ArrayList<Nodes.Node> exceptionNodes, Nodes.RescueNode rescueClause,
            boolean canOmitBacktrace) {

        final Nodes.Node[] exceptionNodesArray = exceptionNodes.toArray(EMPTY_NODE_ARRAY);
        final RubyNode[] handlingClasses = translate(exceptionNodesArray);

        RubyNode translatedBody;
        if (rescueClause.reference != null) {
            // We need to translate the reference before the statements,
            // because the statements can use the variable defined by the reference.
            final RubyNode exceptionWriteNode = translateRescueException(rescueClause.reference);
            var translatedStatements = translateNodeOrNil(rescueClause.statements);
            translatedBody = sequence(rescueClause,
                    exceptionWriteNode, translatedStatements);
        } else {
            translatedBody = translateNodeOrNil(rescueClause.statements);
        }

        final RescueNode rescueNode = new RescueClassesNode(handlingClasses, translatedBody, canOmitBacktrace);
        assignPositionOnly(exceptionNodesArray, rescueNode);
        return rescueNode;
    }

    @Override
    public RubyNode visitBlockLocalVariableNode(Nodes.BlockLocalVariableNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in translateBlockAndLambda");
    }

    @Override
    public RubyNode visitBlockNode(Nodes.BlockNode node) {
        throw CompilerDirectives.shouldNotReachHere(
                "BlockNode should be translated specially by its parent node to pass the method name to which the block is passed");
    }

    private RubyNode visitBlockNode(Nodes.BlockNode node, String literalBlockPassedToMethod) {
        return translateBlockAndLambda(node, node.parameters, node.body, node.locals, literalBlockPassedToMethod);
    }

    private RubyNode translateBlockAndLambda(Nodes.Node node, Nodes.Node parametersNode,
            Nodes.Node body, String[] locals, String literalBlockPassedToMethod) {
        final boolean isStabbyLambda = node instanceof Nodes.LambdaNode;

        // Unset this flag for a `for`-loop's block
        final boolean hasOwnScope = !translatingForStatement;

        TranslatorEnvironment methodParent = environment.getSurroundingMethodEnvironment();
        final String methodName = methodParent.getMethodName();

        final int blockDepth = environment.getBlockDepth() + 1;

        final Nodes.ParametersNode parameters;
        if (parametersNode instanceof Nodes.BlockParametersNode blockParameters) {
            // NOTE: we ignore BlockParametersNode#locals, it is fully redundant with BlockNode#locals/LambdaNode#locals
            parameters = blockParameters.parameters != null ? blockParameters.parameters : ZERO_PARAMETERS_NODE;
        } else if (parametersNode instanceof Nodes.NumberedParametersNode numberedParameters) {
            // build Nodes.ParametersNode with required parameters _1, _2, etc
            final int maximum = numberedParameters.maximum;
            final var requireds = new Nodes.RequiredParameterNode[maximum];

            for (int i = 1; i <= maximum; i++) {
                String name = NUMBERED_PARAMETER_NAMES[i];
                requireds[i - 1] = new Nodes.RequiredParameterNode(0, 0, NO_FLAGS, name);
            }

            parameters = new Nodes.ParametersNode(0, 0, requireds, EMPTY_OPTIONAL_PARAMETER_NODE_ARRAY, null,
                    EMPTY_NODE_ARRAY, EMPTY_NODE_ARRAY, null, null);
        } else if (parametersNode instanceof Nodes.ItParametersNode) {
            var requireds = new Nodes.RequiredParameterNode[]{
                    new Nodes.RequiredParameterNode(0, 0, NO_FLAGS, IT_PARAMETER_NAME)
            };
            parameters = new Nodes.ParametersNode(0, 0, requireds, EMPTY_OPTIONAL_PARAMETER_NODE_ARRAY, null,
                    EMPTY_NODE_ARRAY, EMPTY_NODE_ARRAY, null, null);
        } else if (parametersNode == null) {
            parameters = ZERO_PARAMETERS_NODE;
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        final Arity arity = createArity(parameters);
        final ArgumentDescriptor[] argumentDescriptors = parametersNodeToArgumentDescriptors(parameters);

        // "block in foo"
        String originalName = SharedMethodInfo.getBlockName(blockDepth, methodName);
        // "block (2 levels) in M::C.foo"
        String parseName = SharedMethodInfo.getBlockName(blockDepth, methodParent.getSharedMethodInfo().getParseName());
        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                getSourceSection(node),
                environment.getStaticLexicalScopeOrNull(),
                arity,
                originalName,
                blockDepth,
                parseName,
                methodName,
                argumentDescriptors);

        // "stabby lambda" is a common name for the `->() {}` lambda syntax
        final ReturnID returnID = isStabbyLambda ? parseEnvironment.allocateReturnID() : environment.getReturnID();

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                parseEnvironment,
                returnID,
                hasOwnScope,
                false,
                sharedMethodInfo,
                environment.getMethodName(),
                blockDepth,
                parseEnvironment.allocateBreakID(),
                null,
                environment.modulePath);
        newEnvironment.literalBlockPassedToMethod = literalBlockPassedToMethod;

        final YARPBlockNodeTranslator methodCompiler = new YARPBlockNodeTranslator(
                newEnvironment,
                arity,
                parameters.block != null,
                rubyWarnings);

        methodCompiler.frameOnStackMarkerSlotStack = frameOnStackMarkerSlotStack;

        final RubyNode rubyNode = methodCompiler.compileBlockNode(
                body,
                parameters,
                parametersNode,
                locals,
                isStabbyLambda,
                getSourceSection(node));

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitBlockArgumentNode(Nodes.BlockArgumentNode node) {
        final RubyNode rubyNode;
        final RubyNode valueNode;

        if (node.expression == null) {
            // def foo(&) a(&) end
            valueNode = environment.findLocalVarNode(DEFAULT_BLOCK_NAME);
        } else {
            // a(&:b)
            valueNode = node.expression.accept(this);
        }

        rubyNode = ToProcNodeGen.create(valueNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitBlockParameterNode(Nodes.BlockParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitBlockParametersNode(Nodes.BlockParametersNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in translateBlockAndLambda");
    }

    @Override
    public RubyNode visitBreakNode(Nodes.BreakNode node) {
        // The break operator is invalid outside a proc body or while-loop body.
        // The parser doesn't handle it on its own so do it manually.
        if (!environment.isBlock() && !translatingWhile) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid break",
                            currentNode,
                            getSourceSection(node)));
        }

        final RubyNode argumentsNode = translateControlFlowArguments(node.arguments);
        final RubyNode rubyNode = new BreakNode(environment.getBreakID(), translatingWhile, argumentsNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitCallAndWriteNode(Nodes.CallAndWriteNode node) {
        // `a.b &&= value` is translated into `a.b || a.b = value`
        // receiver (a) should be executed only once that's why it's cached into a local variable

        assert node.receiver != null; // without receiver `a &&= b` leads to Nodes.LocalVariableAndWriteNode

        // evaluate receiver only once because it may have side effects
        final var receiverExpression = new YARPExecutedOnceExpression("value", node.receiver, this);
        final var writeReceiverNode = receiverExpression.getWriteNode();
        final var readReceiver = receiverExpression.getReadYARPNode();

        // use Prism nodes and rely on CallNode translation to automatically set RubyCallNode attributes

        // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
        short writeFlags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);

        // safe navigation flag is handled separately, so as optimisation remove it from the flags
        writeFlags = (short) (writeFlags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);
        short readFlags = (short) (node.flags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);

        final RubyNode readNode = callNode(node, readFlags, readReceiver, node.read_name, Nodes.Node.EMPTY_ARRAY)
                .accept(this);
        final RubyNode writeNode = callNode(node, writeFlags, readReceiver, node.write_name,
                node.value).accept(this);
        final RubyNode andNode = AndNodeGen.create(readNode, writeNode);

        final RubyNode sequence;

        if (node.isSafeNavigation()) {
            // immediately return `nil` if receiver is `nil`
            final RubyNode unlessNode = UnlessNodeGen.create(new IsNilNode(receiverExpression.getReadNode()),
                    andNode);
            sequence = sequence(writeReceiverNode, unlessNode);
        } else {
            sequence = sequence(writeReceiverNode, andNode);
        }

        // defined?(a.b &&= c) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitCallNode(Nodes.CallNode node) {
        var methodName = node.name;
        var receiver = node.receiver == null ? new SelfNode() : node.receiver.accept(this);

        var argumentsAndBlock = translateArgumentsAndBlock(node.arguments, node.block, methodName);
        var translatedArguments = argumentsAndBlock.arguments;

        if (parseEnvironment.inCore() && node.isVariableCall() && methodName.equals("undefined")) {
            // translate undefined
            final RubyNode rubyNode = new ObjectLiteralNode(NotProvided.INSTANCE);
            return assignPositionAndFlags(node, rubyNode);
        }

        if (node.receiver instanceof Nodes.StringNode stringNode &&
                (methodName.equals("freeze") || methodName.equals("-@") || methodName.equals("dedup"))) {
            final TruffleString tstring = TStringUtils.fromByteArray(stringNode.unescaped, sourceEncoding);
            final ImmutableRubyString frozenString = language.getImmutableString(tstring, sourceEncoding);
            final RubyNode rubyNode = new FrozenStringLiteralNode(frozenString, FrozenStrings.METHOD);

            return assignPositionAndFlags(node, rubyNode);
        }

        // A Primitive call.
        // `Primitive.foo arg1, arg2, argN` is translated into `FooPrimitiveNode(arg1, arg2, ..., argN)`.
        if (parseEnvironment.canUsePrimitives() &&
                node.receiver instanceof Nodes.ConstantReadNode constantReadNode &&
                constantReadNode.name.equals("Primitive")) {

            final PrimitiveNodeConstructor constructor = language.primitiveManager.getPrimitive(methodName);

            if (!constructor.isPublic() && !parseEnvironment.canUsePrivatePrimitives()) {
                final RubyContext context = RubyLanguage.getCurrentContext();
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().syntaxError(
                                "Primitive." + methodName +
                                        " is not public and cannot be used outside the TruffleRuby core library",
                                currentNode,
                                getSourceSection(node)));
            }

            if (translatedArguments.length != constructor.getPrimitiveArity()) {
                throw new Error(
                        "Incorrect number of arguments (expected " + constructor.getPrimitiveArity() + ") at " +
                                language.fileLine(getSourceSection(node)));
            }

            final RubyNode invokePrimitiveNode = constructor.createInvokePrimitiveNode(translatedArguments);

            assignPositionAndFlags(node, invokePrimitiveNode);
            return invokePrimitiveNode;
        }

        final var callNodeParameters = new RubyCallNodeParameters(
                receiver,
                methodName,
                argumentsAndBlock.block,
                argumentsAndBlock.argumentsDescriptor,
                translatedArguments,
                argumentsAndBlock.isSplatted,
                node.isIgnoreVisibility(),
                node.isVariableCall(),
                node.isSafeNavigation(),
                node.isAttributeWrite());
        final RubyNode callNode = language.coreMethodAssumptions.createCallNode(callNodeParameters);

        final var rubyNode = wrapCallWithLiteralBlock(argumentsAndBlock, callNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    record ArgumentsAndBlockTranslation(RubyNode block, RubyNode[] arguments, boolean isSplatted,
            ArgumentsDescriptor argumentsDescriptor, int frameOnStackMarkerSlot) {
    }

    private static RubyNode wrapCallWithLiteralBlock(ArgumentsAndBlockTranslation argumentsAndBlock,
            RubyNode callNode) {
        // wrap call node with literal block
        if (argumentsAndBlock.block instanceof BlockDefinitionNode blockDef) {
            // if we have a literal block, `break` breaks out of this call site
            final var frameOnStackNode = new FrameOnStackNode(callNode, argumentsAndBlock.frameOnStackMarkerSlot);
            return new CatchBreakNode(blockDef.getBreakID(), frameOnStackNode, false);
        } else {
            return callNode;
        }
    }

    /** Translate a method call arguments */
    private ArgumentsAndBlockTranslation translateArgumentsAndBlock(Nodes.ArgumentsNode argumentsNode, Nodes.Node block,
            String methodName) {
        Nodes.Node[] arguments;
        if (argumentsNode == null) {
            arguments = EMPTY_NODE_ARRAY;
        } else {
            arguments = argumentsNode.arguments;
        }

        boolean isForwardArguments = (arguments.length > 0 &&
                ArrayUtils.getLast(arguments) instanceof Nodes.ForwardingArgumentsNode);

        if (isForwardArguments) {
            // use depth = 0 as far as it's ignored
            final var readRest = new Nodes.LocalVariableReadNode(0, 0, FORWARDED_REST_NAME, 0);
            final var readKeyRest = new Nodes.LocalVariableReadNode(0, 0, FORWARDED_KEYWORD_REST_NAME, 0);

            final var splat = new Nodes.SplatNode(0, 0, readRest);
            final var keywordHash = new Nodes.KeywordHashNode(0, 0, (short) 0,
                    new Nodes.Node[]{ new Nodes.AssocSplatNode(0, 0, readKeyRest) });

            // replace '...' argument with rest and keyrest arguments
            final var forwarding = new Nodes.Node[arguments.length + 1];
            System.arraycopy(arguments, 0, forwarding, 0, arguments.length - 1);
            forwarding[forwarding.length - 2] = splat;
            forwarding[forwarding.length - 1] = keywordHash;

            arguments = forwarding;
        }

        // should be after handling of forward-argument as far as ... means there is a splatted argument
        boolean isSplatted = containYARPSplatNode(arguments);
        var argumentsDescriptor = getKeywordArgumentsDescriptor(arguments);

        final RubyNode[] translatedArguments;
        if (isSplatted) {
            translatedArguments = new RubyNode[]{ translateExpressionsList(arguments) };
        } else {
            translatedArguments = translate(arguments);
        }

        // No need to copy the array for call(*splat), the elements will be copied to the frame arguments
        if (isSplatted && translatedArguments.length == 1 &&
                translatedArguments[0] instanceof SplatCastNode splatNode) {
            splatNode.doNotCopy();
        }

        final RubyNode blockNode;
        final int frameOnStackMarkerSlot;
        if (block != null) {
            if (block instanceof Nodes.BlockNode b) {
                // a() {}
                frameOnStackMarkerSlot = environment.declareLocalTemp("frame_on_stack_marker");
                frameOnStackMarkerSlotStack.push(frameOnStackMarkerSlot);
                try {
                    blockNode = visitBlockNode(b, methodName);
                } finally {
                    frameOnStackMarkerSlotStack.pop();
                }
            } else if (block instanceof Nodes.BlockArgumentNode blockArgument) {
                // a(&:b)
                blockNode = blockArgument.accept(this);
                frameOnStackMarkerSlot = NO_FRAME_ON_STACK_MARKER;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else if (isForwardArguments) {
            // a(...)
            // use depth = 0 as far as it's ignored
            final var readBlock = new Nodes.LocalVariableReadNode(0, 0, FORWARDED_BLOCK_NAME, 0);
            final var readBlockNode = readBlock.accept(this);
            blockNode = ToProcNodeGen.create(readBlockNode);
            frameOnStackMarkerSlot = NO_FRAME_ON_STACK_MARKER;
        } else {
            blockNode = null;
            frameOnStackMarkerSlot = NO_FRAME_ON_STACK_MARKER;
        }

        return new ArgumentsAndBlockTranslation(blockNode, translatedArguments, isSplatted, argumentsDescriptor,
                frameOnStackMarkerSlot);
    }

    /** Detects if a method call arguments contain keyword arguments */
    private ArgumentsDescriptor getKeywordArgumentsDescriptor(Nodes.Node[] arguments) {
        if (arguments.length == 0) {
            return NoKeywordArgumentsDescriptor.INSTANCE;
        }

        // fast path to compute the descriptor without looking at all arguments
        Nodes.Node last = ArrayUtils.getLast(arguments);
        // if `...` then we know there are no literal keyword arguments, only **kwrest
        if (last instanceof Nodes.ForwardingArgumentsNode) {
            return KeywordArgumentsDescriptor.EMPTY;
        }
        // if there are no keywords the descriptor is NoKeywordArgumentsDescriptor.INSTANCE
        if (!(last instanceof Nodes.KeywordHashNode keywords)) {
            return NoKeywordArgumentsDescriptor.INSTANCE;
        }

        // analyse arguments
        final List<String> names = new ArrayList<>(); // keyword arguments symbolic keys
        boolean keyrest = false;                      // there is a ** operator, e.g. foo(a: 1, **h)
        boolean nonKeywordKeys = false;               // there are non-Symbol keys, e.g. foo("a" => 1)

        for (var n : keywords.elements) {
            if (n instanceof Nodes.AssocNode assoc && assoc.key instanceof Nodes.SymbolNode symbol) {
                names.add(toString(symbol.unescaped));
            } else if (n instanceof Nodes.AssocNode assoc && !(assoc.key instanceof Nodes.SymbolNode)) {
                nonKeywordKeys = true;
            } else if (n instanceof Nodes.AssocSplatNode) {
                keyrest = true;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        if (keyrest || nonKeywordKeys || !names.isEmpty()) {
            return language.keywordArgumentsDescriptorManager
                    .getArgumentsDescriptor(names.toArray(StringUtils.EMPTY_STRING_ARRAY));
        } else {
            return NoKeywordArgumentsDescriptor.INSTANCE;
        }
    }

    @Override
    public RubyNode visitCallOperatorWriteNode(Nodes.CallOperatorWriteNode node) {
        // e.g. `a.b += value` is translated into `a.b = a.b + value`,
        // receiver (a) should be executed only once - that's why it's cached in a local variable

        assert node.receiver != null; // without receiver `a += b` leads to Nodes.LocalVariableOperatorWriteNode

        final var receiverExpression = new YARPExecutedOnceExpression("value", node.receiver, this);
        final var writeReceiverNode = receiverExpression.getWriteNode();
        final var readReceiver = receiverExpression.getReadYARPNode();

        // use Prism nodes and rely on CallNode translation to automatically set RubyCallNode flags

        // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
        short writeFlags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);

        // the safe navigation flag is handled separately, so as optimisation remove it from the flags
        writeFlags = (short) (writeFlags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);
        short readFlags = (short) (node.flags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);

        final Nodes.Node read = callNode(node, readFlags, readReceiver, node.read_name, Nodes.Node.EMPTY_ARRAY);
        final Nodes.Node executeOperator = callNode(node, read, node.binary_operator, node.value);
        final Nodes.Node write = callNode(node, writeFlags, readReceiver, node.write_name,
                executeOperator);
        final RubyNode writeNode = write.accept(this);

        final RubyNode sequence;

        if (node.isSafeNavigation()) {
            // immediately return `nil` if receiver is `nil`
            final RubyNode unlessNode = UnlessNodeGen.create(new IsNilNode(receiverExpression.getReadNode()),
                    writeNode);
            sequence = sequence(writeReceiverNode, unlessNode);
        } else {
            sequence = sequence(writeReceiverNode, writeNode);
        }

        // defined?(a.b += c) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitCallOrWriteNode(Nodes.CallOrWriteNode node) {
        // `a.b ||= value` is translated into `a.b || a.b = value`
        // receiver (a) should be executed only once that's why it's cached into a local variable

        assert node.receiver != null; // without receiver `a ||= b` leads to Nodes.LocalVariableOrWriteNode

        final var receiverExpression = new YARPExecutedOnceExpression("value", node.receiver, this);
        final var writeReceiverNode = receiverExpression.getWriteNode();
        final var readReceiver = receiverExpression.getReadYARPNode();

        // use Prism nodes and rely on CallNode translation to automatically set RubyCallNode flags

        // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
        short writeFlags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);

        // safe navigation flag is handled separately, so as optimisation remove it from the flags
        writeFlags = (short) (writeFlags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);
        short readFlags = (short) (node.flags & ~Nodes.CallNodeFlags.SAFE_NAVIGATION);

        final RubyNode readNode = callNode(node, readFlags, readReceiver, node.read_name, Nodes.Node.EMPTY_ARRAY)
                .accept(this);
        final RubyNode writeNode = callNode(node, writeFlags, readReceiver, node.write_name,
                node.value).accept(this);
        final RubyNode orNode = OrLazyValueDefinedNodeGen.create(readNode, writeNode);

        final RubyNode sequence;

        if (node.isSafeNavigation()) {
            // return `nil` if receiver is `nil`
            final RubyNode unlessNode = UnlessNodeGen.create(new IsNilNode(receiverExpression.getReadNode()),
                    orNode);
            sequence = sequence(writeReceiverNode, unlessNode);
        } else {
            sequence = sequence(writeReceiverNode, orNode);
        }

        // defined?(a.b ||= c) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitCallTargetNode(Nodes.CallTargetNode node) {
        // extra argument should be added before node translation
        // to trigger correctly replacement with inlined nodes (e.g. InlinedIndexSetNodeGen)
        // that may rely on arguments count

        assert node.name.endsWith("=");

        final Nodes.Node[] arguments = { new Nodes.NilNode(0, 0) };
        final var argumentsNode = new Nodes.ArgumentsNode(0, 0, NO_FLAGS, arguments);

        // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
        // but preserve original flags because Prism may set them, e.g. SAFE_NAVIGATION flag
        short flags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);

        final var callNode = new Nodes.CallNode(node.startOffset, node.length, flags, node.receiver, node.name,
                argumentsNode, null);
        return callNode.accept(this);
    }

    @Override
    public RubyNode visitCapturePatternNode(Nodes.CapturePatternNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitCaseMatchNode(Nodes.CaseMatchNode node) {
        var translator = new YARPPatternMatchingTranslator(environment, this);

        // evaluate the case expression and store it in a local variable
        final var predicateExpression = new YARPExecutedOnceExpression("case in value", node.predicate, this);
        final var writePredicateNode = predicateExpression.getWriteNode();
        final var readPredicateNode = predicateExpression.getReadNode();

        /* Build an if expression from the in's and else. Work backwards because the first IfElseNode contains all the
         * others in its else clause. */

        RubyNode elseNode;
        if (node.else_clause == null) {
            elseNode = NoMatchingPatternNodeGen.create(readPredicateNode);
        } else {
            elseNode = node.else_clause.accept(this);
        }

        for (int n = node.conditions.length - 1; n >= 0; n--) {
            Nodes.InNode inNode = node.conditions[n];
            Nodes.Node patternNode = inNode.pattern;

            final RubyNode conditionNode = translator.translatePatternNode(patternNode, readPredicateNode);
            // Create the if node
            final RubyNode thenNode = translateNodeOrNil(inNode.statements);
            final IfElseNode ifNode = IfElseNodeGen.create(conditionNode, thenNode, elseNode);

            // This if becomes the else for the next if
            elseNode = ifNode;
        }

        final RubyNode ifNode = elseNode;

        // A top-level block assigns the temp then runs the if
        final RubyNode ret = sequence(writePredicateNode, ifNode);

        return assignPositionAndFlags(node, ret);
    }

    @Override
    public RubyNode visitCaseNode(Nodes.CaseNode node) {
        // There are two forms of case operator:
        // - one compares a list of expressions against a value,
        // - the other just checks a list of expressions for truth

        final RubyNode rubyNode;

        if (node.predicate != null) {
            // Form #1 - compare a list of expressions against a value (node.predicate)

            // evaluate the case expression and store it in a local variable
            final var predicateExpression = new YARPExecutedOnceExpression("case", node.predicate, this);
            final var writePredicateNode = predicateExpression.getWriteNode();
            final var readPredicateNode = predicateExpression.getReadNode();

            // Build an if expression from `when` and `else` branches.
            // Work backwards to make the first if contain all the others in its `else` clause.
            RubyNode elseNode = translateNodeOrNil(node.else_clause);

            final Nodes.WhenNode[] whenNodes = node.conditions;

            for (int n = whenNodes.length - 1; n >= 0; n--) {
                final Nodes.WhenNode whenNode = whenNodes[n];
                final Nodes.Node[] whenConditions = whenNode.conditions;
                boolean containSplatOperator = containYARPSplatNode(whenConditions);

                if (containSplatOperator) {
                    final RubyNode receiver = new TruffleInternalModuleLiteralNode();
                    final RubyNode whenConditionNode = translateExpressionsList(whenConditions);
                    final RubyNode[] arguments = new RubyNode[]{
                            whenConditionNode,
                            NodeUtil.cloneNode(readPredicateNode) };
                    final RubyNode predicateNode = createCallNode(receiver, "when_splat", arguments);

                    // create `if` node
                    final RubyNode thenNode = translateNodeOrNil(whenNode.statements);
                    final IfElseNode ifNode = IfElseNodeGen.create(predicateNode, thenNode, elseNode);

                    // this `if` becomes `else` branch of the outer `if`
                    elseNode = ifNode;
                } else {
                    // translate `when` with multiple expressions into a single `if` operator, e.g.
                    //   case x
                    //     when a, b, c
                    //  is translated into
                    //    if x === a || x === b || x === c

                    RubyNode predicateNode = null;

                    for (var whenCondition : whenConditions) {
                        final RubyNode receiver = whenCondition.accept(this);
                        final RubyNode[] arguments = new RubyNode[]{ NodeUtil.cloneNode(readPredicateNode) };
                        final RubyNode nextPredicateNode = createCallNode(receiver, "===", arguments);

                        if (predicateNode == null) {
                            predicateNode = nextPredicateNode;
                        } else {
                            predicateNode = OrNodeGen.create(predicateNode, nextPredicateNode);
                        }
                    }

                    // create `if` node
                    final RubyNode thenNode = translateNodeOrNil(whenNode.statements);
                    final IfElseNode ifNode = IfElseNodeGen.create(predicateNode, thenNode, elseNode);

                    // this `if` becomes `else` branch of the outer `if`
                    elseNode = ifNode;
                }
            }

            final RubyNode ifNode = elseNode;

            // A top-level block assigns the temp then runs the `if`
            rubyNode = sequence(writePredicateNode, ifNode);
        } else {
            // Form #2 - checks a list of expressions for truth

            // Build an if expression from `when` and `else` branches.
            // Work backwards to make the first if contain all the others in its `else` clause.

            RubyNode elseNode = translateNodeOrNil(node.else_clause);

            final Nodes.WhenNode[] whenNodes = node.conditions;

            for (int n = node.conditions.length - 1; n >= 0; n--) {
                final Nodes.WhenNode whenNode = whenNodes[n];
                final Nodes.Node[] whenConditions = whenNode.conditions;
                boolean containSplatOperator = containYARPSplatNode(whenConditions);

                if (!containSplatOperator) {
                    // translate `when` with multiple expressions into a single `if` operator, e.g.
                    //   case
                    //     when a, b, c
                    //  is translated into
                    //    if a || b || c

                    RubyNode predicateNode = null;

                    for (var whenCondition : whenConditions) {
                        final RubyNode nextPredicateNode = whenCondition.accept(this);

                        if (predicateNode == null) {
                            predicateNode = nextPredicateNode;
                        } else {
                            predicateNode = OrNodeGen.create(predicateNode, nextPredicateNode);
                        }
                    }

                    // create `if` node
                    final RubyNode thenNode = translateNodeOrNil(whenNode.statements);
                    final IfElseNode ifNode = IfElseNodeGen.create(predicateNode, thenNode, elseNode);

                    // this `if` becomes `else` branch of the outer `if`
                    elseNode = ifNode;
                } else {
                    // use Array#any? to check whether there is any truthy value
                    // whenConditions are translated into an array-producing node
                    // so `when a, *b, c` is translated into `[a, *b, c].any?`
                    final RubyNode whenConditionNode = translateExpressionsList(whenConditions);
                    final RubyNode receiver = whenConditionNode;
                    final RubyNode predicateNode = createCallNode(receiver, "any?", RubyNode.EMPTY_ARRAY);

                    // create `if` node
                    final RubyNode thenNode = translateNodeOrNil(whenNode.statements);
                    final IfElseNode ifNode = IfElseNodeGen.create(predicateNode, thenNode, elseNode);

                    // this `if` becomes `else` branch of the outer `if`
                    elseNode = ifNode;
                }
            }

            rubyNode = elseNode;
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassNode(Nodes.ClassNode node) {
        final RubyNode lexicalParent = getParentLexicalScopeForConstant(node.constant_path);
        final RubyNode superClass;

        if (node.superclass != null) {
            superClass = node.superclass.accept(this);
        } else {
            superClass = null;
        }

        final DefineClassNode defineOrGetClass = new DefineClassNode(node.name, lexicalParent, superClass);
        assignPositionOnly(node, defineOrGetClass); // to assign source location to a new constant

        final RubyNode rubyNode = openModule(
                node,
                defineOrGetClass,
                appendConstantPathToModulePath(node.constant_path),
                node.name,
                node.locals,
                node.body,
                OpenModule.CLASS,
                shouldUseDynamicConstantLookupForModuleBody(node));

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassVariableAndWriteNode(Nodes.ClassVariableAndWriteNode node) {
        // `@@a &&= value` is translated into @@a && @@a = value`
        // don't check whether variable is defined so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.ClassVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.ClassVariableReadNode(startOffset, length, node.name).accept(this);
        var andNode = AndNodeGen.create(readNode, writeNode);

        // defined?(@@a &&= b) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassVariableOperatorWriteNode(Nodes.ClassVariableOperatorWriteNode node) {
        // e.g. `@@a += value` is translated into @@a = @@a + value`
        // don't check whether variable is initialized so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var readNode = new Nodes.ClassVariableReadNode(startOffset, length, node.name);
        var desugared = new Nodes.ClassVariableWriteNode(startOffset, length, node.name,
                callNode(node, readNode, node.binary_operator, node.value));
        return desugared.accept(this);
    }

    @Override
    public RubyNode visitClassVariableOrWriteNode(Nodes.ClassVariableOrWriteNode node) {
        // `@@a ||= value` is translated into (defined?(@@a) && @@a) || @@a = value`
        // so we check whether variable is defined and no exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.ClassVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.ClassVariableReadNode(startOffset, length, node.name).accept(this);
        var andNode = AndNodeGen.create(new DefinedNode(readNode), readNode);

        final RubyNode rubyNode = OrLazyValueDefinedNodeGen.create(andNode, writeNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassVariableReadNode(Nodes.ClassVariableReadNode node) {
        final RubyNode rubyNode = new ReadClassVariableNode(
                getLexicalScopeNode("class variable lookup", node),
                node.name);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassVariableWriteNode(Nodes.ClassVariableWriteNode node) {
        final RubyNode rhs = node.value.accept(this);
        final RubyNode rubyNode = new WriteClassVariableNode(
                getLexicalScopeNode("set dynamic class variable", node),
                node.name,
                rhs);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitClassVariableTargetNode(Nodes.ClassVariableTargetNode node) {
        final RubyNode rubyNode = new WriteClassVariableNode(
                getLexicalScopeNode("set dynamic class variable", node),
                node.name,
                null);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantAndWriteNode(Nodes.ConstantAndWriteNode node) {
        // `A &&= value` is translated into `A && A = value`
        // don't check whether constant is defined and so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var readNode = new Nodes.ConstantReadNode(startOffset, length, node.name).accept(this);
        var writeNode = new Nodes.ConstantWriteNode(startOffset, length, node.name, node.value).accept(this);
        final RubyNode andNode = AndNodeGen.create(readNode, writeNode);

        // defined?(A &&= value) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantOperatorWriteNode(Nodes.ConstantOperatorWriteNode node) {
        // e.g. `A += value` is translated into A = A + value`
        // don't check whether constant is initialized so warnings will be emitted if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        // Use Nodes.CallNode and translate it to produce inlined operator nodes
        final var readNode = new Nodes.ConstantReadNode(startOffset, length, node.name);
        final var operatorNode = callNode(node, readNode, node.binary_operator, node.value);
        final var writeNode = new Nodes.ConstantWriteNode(startOffset, length, node.name, operatorNode);

        return writeNode.accept(this);
    }

    @Override
    public RubyNode visitConstantOrWriteNode(Nodes.ConstantOrWriteNode node) {
        // `A ||= value` is translated into `(defined?(A) && A) || A = value`
        // so we check whether constant is defined and no exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.ConstantWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.ConstantReadNode(startOffset, length, node.name).accept(this);
        var andNode = AndNodeGen.create(new DefinedNode(readNode), readNode);

        final RubyNode rubyNode = OrLazyValueDefinedNodeGen.create(andNode, writeNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantPathAndWriteNode(Nodes.ConstantPathAndWriteNode node) {
        // `A::B &&= value` is translated into `A::B && A::B = value`
        // don't check whether constant is defined and so exception will be raised if it isn't
        // A module/class (A::) should be executed only once - that's why it is cached in a local variable.

        final Nodes.ConstantPathNode target; // use instead of node.target
        final RubyNode writeParentNode;

        if (node.target.parent != null) {
            // A::B &&= 1
            var parentExpression = new YARPExecutedOnceExpression("value", node.target.parent, this);
            Nodes.Node readParent = parentExpression.getReadYARPNode();
            target = new Nodes.ConstantPathNode(node.target.startOffset,
                    node.target.length, readParent, node.target.name);

            writeParentNode = parentExpression.getWriteNode();
        } else {
            // ::A &&= 1
            target = node.target;
            writeParentNode = null;
        }

        var value = node.value.accept(this);

        var readNode = (ReadConstantNode) target.accept(this);
        var writeNode = (WriteConstantNode) readNode.makeWriteNode(value);
        final RubyNode andNode = AndNodeGen.create(readNode, writeNode);

        final RubyNode rubyNode;

        // defined?(A::B &&= value) should return 'assignment'
        if (writeParentNode != null) {
            RubyNode sequence = sequence(writeParentNode, andNode);
            rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        } else {
            rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantPathNode(Nodes.ConstantPathNode node) {
        final RubyNode moduleNode;

        if (node.parent != null) {
            // FOO
            moduleNode = node.parent.accept(this);
        } else {
            // ::FOO or FOO::BAR
            moduleNode = new ObjectClassLiteralNode();
        }

        final RubyNode rubyNode = new ReadConstantNode(moduleNode, node.name);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantPathOperatorWriteNode(Nodes.ConstantPathOperatorWriteNode node) {
        // e.g. `A::B += value` is translated into A::B = A::B + value`
        // don't check whether constant is initialized so warnings will be emitted if it isn't.
        // A module/class (A::) should be executed only once - that's why it is cached in a local variable.

        final Nodes.ConstantPathNode target; // use instead of node.target
        final RubyNode writeParentNode;

        if (node.target.parent != null) {
            // A::B += 1
            var parentExpression = new YARPExecutedOnceExpression("value", node.target.parent, this);
            Nodes.Node readParent = parentExpression.getReadYARPNode();
            target = new Nodes.ConstantPathNode(node.target.startOffset,
                    node.target.length, readParent, node.target.name);

            writeParentNode = parentExpression.getWriteNode();
        } else {
            // ::A += 1
            target = node.target;
            writeParentNode = null;
        }

        int startOffset = node.startOffset;
        int length = node.length;

        // Use Nodes.CallNode and translate it to produce inlined operator nodes
        final var operatorNode = callNode(node, target, node.binary_operator, node.value);
        final var writeNode = new Nodes.ConstantPathWriteNode(startOffset, length, target, operatorNode);

        final RubyNode rubyNode;

        if (writeParentNode != null) {
            // defined?(A::B += 1) returns 'expression' so don't use DefinedWrapperNode here
            rubyNode = sequence(writeParentNode, writeNode.accept(this));

            // rubyNode may be already assigned source code in case writeParentNode is null
            assignPositionAndFlagsIfMissing(node, rubyNode);
        } else {
            rubyNode = writeNode.accept(this);
        }

        return rubyNode;
    }

    @Override
    public RubyNode visitConstantPathOrWriteNode(Nodes.ConstantPathOrWriteNode node) {
        // `A::B ||= value` is translated into `(defined?(A::B) && A::B) || A::B = value`
        // check whether constant is defined so no exception will be raised if it isn't
        // A module/class (A::) should be executed only once - that's why it is cached in a local variable.

        final Nodes.ConstantPathNode target; // use instead of node.target
        final RubyNode writeParentNode;

        if (node.target.parent != null) {
            // A::B ||= 1
            var parentExpression = new YARPExecutedOnceExpression("value", node.target.parent, this);
            Nodes.Node readParent = parentExpression.getReadYARPNode();
            target = new Nodes.ConstantPathNode(node.target.startOffset,
                    node.target.length, readParent, node.target.name);

            writeParentNode = parentExpression.getWriteNode();
        } else {
            // ::A ||= 1
            target = node.target;
            writeParentNode = null;
        }

        var value = node.value.accept(this);

        var readNode = (ReadConstantNode) target.accept(this);
        var writeNode = (WriteConstantNode) readNode.makeWriteNode(value);
        var andNode = AndNodeGen.create(new DefinedNode(readNode), readNode);
        final RubyNode orNode = OrLazyValueDefinedNodeGen.create(andNode, writeNode);

        final RubyNode rubyNode;

        // defined?(A::B ||= value) should return 'assignment'
        if (writeParentNode != null) {
            RubyNode sequence = sequence(writeParentNode, orNode);
            rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        } else {
            rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, orNode);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantPathWriteNode(Nodes.ConstantPathWriteNode node) {
        final Nodes.ConstantPathNode constantPathNode = node.target;

        final RubyNode moduleNode;
        if (constantPathNode.parent != null) {
            // FOO::BAR = 1
            moduleNode = constantPathNode.parent.accept(this);
        } else {
            // ::FOO = 1
            moduleNode = new ObjectClassLiteralNode();
        }

        final RubyNode value = node.value.accept(this);
        final RubyNode rubyNode = new WriteConstantNode(constantPathNode.name, moduleNode, value);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantPathTargetNode(Nodes.ConstantPathTargetNode node) {
        final RubyNode moduleNode;
        if (node.parent != null) {
            // FOO::BAR = 1
            moduleNode = node.parent.accept(this);
        } else {
            // ::FOO = 1
            moduleNode = new ObjectClassLiteralNode();
        }

        final RubyNode rubyNode = new WriteConstantNode(node.name, moduleNode, null);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantReadNode(Nodes.ConstantReadNode node) {
        final RubyNode rubyNode;

        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER
                        .info(() -> "dynamic constant lookup at " + language.fileLine(getSourceSection(node)));
            }

            rubyNode = new ReadConstantWithDynamicScopeNode(node.name);
        } else {
            final LexicalScope lexicalScope = environment.getStaticLexicalScope();
            rubyNode = new ReadConstantWithLexicalScopeNode(lexicalScope, node.name);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantWriteNode(Nodes.ConstantWriteNode node) {
        final RubyNode value = node.value.accept(this);
        final RubyNode moduleNode = getLexicalScopeModuleNode("set dynamic constant", node);
        final RubyNode rubyNode = new WriteConstantNode(node.name, moduleNode, value);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitConstantTargetNode(Nodes.ConstantTargetNode node) {
        final RubyNode moduleNode = getLexicalScopeModuleNode("set dynamic constant", node);
        final RubyNode rubyNode = new WriteConstantNode(node.name, moduleNode, null);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitDefNode(Nodes.DefNode node) {
        final SingletonClassASTNode singletonClassNode;

        if (node.receiver != null) {
            // a method is defined for some object:
            //   def a.foo
            //   end
            final RubyNode receiver = node.receiver.accept(this);
            singletonClassNode = SingletonClassASTNodeGen.create(receiver);
        } else {
            singletonClassNode = null;
        }

        Nodes.ParametersNode parameters = node.parameters;
        if (parameters == null) {
            parameters = ZERO_PARAMETERS_NODE;
        }

        final Arity arity = createArity(parameters);
        final ArgumentDescriptor[] argumentDescriptors = parametersNodeToArgumentDescriptors(parameters);
        final String parseName = modulePathAndMethodName(node.name, node.receiver);

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                getSourceSection(node),
                environment.getStaticLexicalScopeOrNull(),
                arity,
                node.name,
                0,
                parseName,
                null,
                argumentDescriptors);

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                parseEnvironment,
                parseEnvironment.allocateReturnID(),
                true,
                false,
                sharedMethodInfo,
                node.name,
                0,
                null,
                null,
                environment.modulePath);
        newEnvironment.parametersNode = parameters;

        final var defNodeTranslator = new YARPDefNodeTranslator(
                language,
                newEnvironment,
                rubyWarnings);
        var callTargetSupplier = defNodeTranslator.buildMethodNodeCompiler(node, parameters, arity);

        final boolean isDefSingleton = singletonClassNode != null;

        RubyNode rubyNode = new LiteralMethodDefinitionNode(
                singletonClassNode,
                node.name,
                sharedMethodInfo,
                isDefSingleton,
                callTargetSupplier);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitDefinedNode(Nodes.DefinedNode node) {
        // handle defined?(yield) to prevent raising SyntaxError
        if (node.value instanceof Nodes.YieldNode && isInvalidYield()) {
            final var nilNode = new NilLiteralNode();
            return assignPositionAndFlags(node, nilNode);
        }

        final RubyNode value = node.value.accept(this);
        final RubyNode rubyNode = new DefinedNode(value);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitElseNode(Nodes.ElseNode node) {
        // empty `else` branch returns `nil` implicitly
        if (node.statements == null) {
            final RubyNode rubyNode = new NilLiteralNode();
            return assignPositionAndFlags(node, rubyNode);
        }
        return node.statements.accept(this);
    }

    @Override
    public RubyNode visitEmbeddedStatementsNode(Nodes.EmbeddedStatementsNode node) {
        if (node.statements == null) {
            // empty interpolation expression, e.g. in "a #{} b"
            RubyNode rubyNode = new ObjectLiteralNode(
                    language.getImmutableString(sourceEncoding.tencoding.getEmpty(), sourceEncoding));
            return assignPositionAndFlags(node, rubyNode);
        }

        return node.statements.accept(this);
    }

    @Override
    public RubyNode visitEmbeddedVariableNode(Nodes.EmbeddedVariableNode node) {
        return node.variable.accept(this);
    }

    @Override
    public RubyNode visitEnsureNode(Nodes.EnsureNode node) {
        // EnsureNode without statements should be handled in #visitBeginNode
        assert node.statements != null;

        return node.statements.accept(this);
    }

    @Override
    public RubyNode visitFalseNode(Nodes.FalseNode node) {
        RubyNode rubyNode = new BooleanLiteralNode(false);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitFindPatternNode(Nodes.FindPatternNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitFlipFlopNode(Nodes.FlipFlopNode node) {
        final RubyNode begin = node.left.accept(this);
        final RubyNode end = node.right.accept(this);

        // declare a local variable for each flip-flop operator to keep its state
        final var slotAndDepth = createFlipFlopState();
        final RubyNode rubyNode = FlipFlopNodeGen.create(begin, end, node.isExcludeEnd(), slotAndDepth.depth,
                slotAndDepth.slot);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitFloatNode(Nodes.FloatNode node) {
        final RubyNode rubyNode = new FloatLiteralNode(node.value);
        return assignPositionAndFlags(node, rubyNode);
    }

    /** A Ruby for-loop, such as:
     *
     * <pre>
     * for x in y
     *     z = 0
     *     puts x
     * end
     * </pre>
     *
     * naively desugars to:
     *
     * <pre>
     * y.each do |x|
     *     z = 0
     *     puts x
     * end
     * </pre>
     *
     * The main difference is that z and x are always going to be local to the scope outside the block, so it's a bit
     * more like:
     *
     * <pre>
     * z = nil  #  unless z is already defined
     * x = nil  #  unless x is already defined
     *
     * y.each do |temp|
     *    x = temp
     *    z = 0
     *    puts x
     * end
     * </pre>
     *
     * Assigning x the temporary variable instead of defining x as a block parameter forces x to be defined in a proper
     * scope.
     *
     * It also handles cases when the expression assigned in the for could is index assignment (a[b] in []), attribute
     * assignment (a.b in []), multiple assignment (a, b, c in []), or whatever:
     *
     * <pre>
     * for x[0] in y
     *     puts x[0]
     * end
     * </pre>
     *
     * http://blog.grayproductions.net/articles/the_evils_of_the_for_loop
     * http://stackoverflow.com/questions/3294509/for-vs-each-in-ruby
     *
     * The other complication is that normal locals should be defined in the enclosing scope, unlike a normal block. We
     * do that by setting a flag on this translator object when we visit the new iter, translatingForStatement, which we
     * recognise when visiting a block node. */
    @Override
    public RubyNode visitForNode(Nodes.ForNode node) {
        final String parameterName = environment.allocateLocalTemp("for");

        final var requireds = new Nodes.Node[]{ new Nodes.RequiredParameterNode(0, 0, NO_FLAGS, parameterName) };
        final var parameters = new Nodes.ParametersNode(0, 0, requireds, EMPTY_OPTIONAL_PARAMETER_NODE_ARRAY, null,
                Nodes.Node.EMPTY_ARRAY,
                Nodes.Node.EMPTY_ARRAY, null, null);
        final var blockParameters = new Nodes.BlockParametersNode(0, 0, parameters,
                EMPTY_BLOCK_LOCAL_VARIABLE_NODE_ARRAY);

        final var readParameter = new Nodes.LocalVariableReadNode(0, 0, parameterName, 0);
        final Nodes.Node writeIndex;

        // replace -Target node with a -Write one
        if (node.index instanceof Nodes.LocalVariableTargetNode target) {
            writeIndex = new Nodes.LocalVariableWriteNode(0, 0, target.name, target.depth, readParameter);
        } else if (node.index instanceof Nodes.InstanceVariableTargetNode target) {
            writeIndex = new Nodes.InstanceVariableWriteNode(0, 0, target.name, readParameter);
        } else if (node.index instanceof Nodes.ClassVariableTargetNode target) {
            writeIndex = new Nodes.ClassVariableWriteNode(0, 0, target.name, readParameter);
        } else if (node.index instanceof Nodes.GlobalVariableTargetNode target) {
            writeIndex = new Nodes.GlobalVariableWriteNode(0, 0, target.name, readParameter);
        } else if (node.index instanceof Nodes.ConstantTargetNode target) {
            writeIndex = new Nodes.ConstantWriteNode(0, 0, target.name, readParameter);
        } else if (node.index instanceof Nodes.ConstantPathTargetNode target) {
            final var constantPath = new Nodes.ConstantPathNode(0, 0, target.parent, target.name);
            writeIndex = new Nodes.ConstantPathWriteNode(0, 0, constantPath, readParameter);
        } else if (node.index instanceof Nodes.CallTargetNode target) {
            final var arguments = new Nodes.ArgumentsNode(0, 0, NO_FLAGS, new Nodes.Node[]{ readParameter });

            // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
            // but preserve original flags because Prism may set them, e.g. SAFE_NAVIGATION flag
            short flags = (short) (target.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);

            writeIndex = new Nodes.CallNode(0, 0, flags, target.receiver, target.name, arguments, null);
        } else if (node.index instanceof Nodes.IndexTargetNode target) {
            final Nodes.ArgumentsNode arguments;
            final Nodes.Node[] statements;

            if (target.arguments != null) {
                statements = new Nodes.Node[target.arguments.arguments.length + 1];
                System.arraycopy(target.arguments.arguments, 0, statements, 0, target.arguments.arguments.length);
            } else {
                statements = new Nodes.Node[1];
            }

            statements[statements.length - 1] = readParameter;
            arguments = new Nodes.ArgumentsNode(0, 0, NO_FLAGS, statements);

            writeIndex = new Nodes.CallNode(0, 0, target.flags, target.receiver, "[]=", arguments, target.block);
        } else if (node.index instanceof Nodes.MultiTargetNode target) {
            writeIndex = new Nodes.MultiWriteNode(0, 0, target.lefts, target.rest, target.rights, readParameter);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Unexpected node class for for-loop index");
        }

        final Nodes.Node body;
        final int bodyStartOffset = node.collection.endOffset();
        final int bodyLength = node.endOffset() - bodyStartOffset;

        if (node.statements != null) {
            Nodes.Node[] statements = new Nodes.Node[1 + node.statements.body.length];
            statements[0] = writeIndex;
            System.arraycopy(node.statements.body, 0, statements, 1, node.statements.body.length);

            body = new Nodes.StatementsNode(bodyStartOffset, bodyLength, statements);
        } else {
            // for loop with empty body
            var statements = new Nodes.Node[]{ writeIndex };
            body = new Nodes.StatementsNode(bodyStartOffset, bodyLength, statements);
        }

        // in the block environment declare local variable only for parameter
        // and skip declaration all the local variables defined in the block
        String[] locals = new String[]{ parameterName };
        final var block = new Nodes.BlockNode(bodyStartOffset, bodyLength, locals, blockParameters, body);
        final var eachCall = new Nodes.CallNode(node.startOffset,
                node.length, NO_FLAGS, node.collection, "each", null, block);

        final RubyNode rubyNode;
        final boolean translatingForStatement = this.translatingForStatement;
        this.translatingForStatement = true;

        try {
            rubyNode = eachCall.accept(this);
        } finally {
            this.translatingForStatement = translatingForStatement;
        }

        copyNewlineFlag(node, rubyNode);
        return rubyNode;
    }

    @Override
    public RubyNode visitForwardingArgumentsNode(Nodes.ForwardingArgumentsNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitCallNode");
    }

    @Override
    public RubyNode visitForwardingParameterNode(Nodes.ForwardingParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitForwardingSuperNode(Nodes.ForwardingSuperNode node) {
        var argumentsAndBlock = translateArgumentsAndBlock(null, node.block, environment.getMethodName());
        boolean insideDefineMethod = false;

        // find an enclosing method environment
        var environment = this.environment;
        while (environment.isBlock()) {
            if (Objects.equals(environment.literalBlockPassedToMethod, "define_method")) {
                insideDefineMethod = true;
            }
            environment = environment.getParent();
        }

        // `super` is used outside a method body
        if (environment.isModuleBody()) {
            return assignPositionAndFlags(node, new ZSuperOutsideMethodNode(insideDefineMethod));
        }

        var parametersNode = environment.parametersNode;
        if (parametersNode == null) {
            parametersNode = ZERO_PARAMETERS_NODE;
        }

        // TODO should this use `environment` and not `this.environment`? But that fails some specs
        var reloadTranslator = new YARPReloadArgumentsTranslator(this.environment, this,
                parametersNode);

        final RubyNode[] reloadSequence = reloadTranslator.reload(parametersNode);

        var descriptor = (parametersNode.keywords.length > 0 || parametersNode.keyword_rest != null)
                ? KeywordArgumentsDescriptor.EMPTY
                : NoKeywordArgumentsDescriptor.INSTANCE;
        final int restParamIndex = reloadTranslator.getRestParameterIndex();
        final RubyNode arguments = new ReadZSuperArgumentsNode(restParamIndex, reloadSequence);
        final RubyNode block = explicitOrInheritedBlock(argumentsAndBlock.block);
        final boolean isSplatted = reloadTranslator.getRestParameterIndex() != -1;

        RubyNode callNode = new SuperCallNode(isSplatted, arguments, block, descriptor);
        callNode = wrapCallWithLiteralBlock(argumentsAndBlock, callNode);

        return assignPositionAndFlags(node, callNode);
    }

    @Override
    public RubyNode visitGlobalVariableAndWriteNode(Nodes.GlobalVariableAndWriteNode node) {
        // `$a &&= value` is translated into `$a && $a = value`
        // don't check whether variable is defined so a warning will be emitted if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.GlobalVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.GlobalVariableReadNode(startOffset, length, node.name).accept(this);
        var andNode = AndNodeGen.create(readNode, writeNode);

        // defined?($a ||= value) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitGlobalVariableOperatorWriteNode(Nodes.GlobalVariableOperatorWriteNode node) {
        // e.g. `$a += value` is translated into $a = $a + value`
        // don't check whether variable is initialized so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var readNode = new Nodes.GlobalVariableReadNode(startOffset, length, node.name);
        var desugared = new Nodes.GlobalVariableWriteNode(startOffset, length, node.name,
                callNode(node, readNode, node.binary_operator, node.value));
        return desugared.accept(this);
    }

    @Override
    public RubyNode visitGlobalVariableOrWriteNode(Nodes.GlobalVariableOrWriteNode node) {
        // `$a ||= value` is translated into `(defined?($a) && $a) || $a = value`
        // check whether variable is defined so no warnings will be emitted if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.GlobalVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.GlobalVariableReadNode(startOffset, length, node.name).accept(this);
        var definedCheck = AndNodeGen.create(new DefinedNode(readNode), readNode);

        final RubyNode rubyNode = OrLazyValueDefinedNodeGen.create(definedCheck, writeNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitGlobalVariableReadNode(Nodes.GlobalVariableReadNode node) {
        final RubyNode rubyNode = ReadGlobalVariableNodeGen.create(node.name);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitGlobalVariableWriteNode(Nodes.GlobalVariableWriteNode node) {
        final RubyNode value = node.value.accept(this);
        final RubyNode rubyNode = WriteGlobalVariableNodeGen.create(node.name, value);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitGlobalVariableTargetNode(Nodes.GlobalVariableTargetNode node) {
        final RubyNode rubyNode = WriteGlobalVariableNodeGen.create(node.name, null);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitHashNode(Nodes.HashNode node) {
        if (node.elements.length == 0) { // an empty Hash literal like h = {}
            final RubyNode rubyNode = HashLiteralNode.create(RubyNode.EMPTY_ARRAY, language);
            return assignPositionAndFlags(node, rubyNode);
        }

        final List<RubyNode> hashConcats = new ArrayList<>();
        final List<RubyNode> keyValues = new ArrayList<>();

        for (Nodes.Node pair : node.elements) {
            if (pair instanceof Nodes.AssocSplatNode assocSplatNode) {
                // This case is for splats {a: 1, **{b: 2}, c: 3}
                if (!keyValues.isEmpty()) {
                    final RubyNode hashLiteralSoFar = HashLiteralNode
                            .create(keyValues.toArray(RubyNode.EMPTY_ARRAY), language);
                    hashConcats.add(hashLiteralSoFar);
                }

                final RubyNode valueNode;

                if (assocSplatNode.value != null) {
                    valueNode = assocSplatNode.value.accept(this);
                } else {
                    // forwarding ** in a method call, e.g.
                    //   def foo(**)
                    //     bar(**)
                    //   end
                    valueNode = environment.findLocalVarNode(DEFAULT_KEYWORD_REST_NAME);
                }

                hashConcats.add(HashCastNodeGen.HashCastASTNodeGen.create(valueNode));
                keyValues.clear();
            } else if (pair instanceof Nodes.AssocNode assocNode) {
                RubyNode keyNode = assocNode.key.accept(this);

                // String literals in a key position become frozen
                if (keyNode instanceof StringLiteralNode stringNode) {
                    var frozenString = language.getImmutableString(stringNode.getTString(),
                            stringNode.getEncoding());
                    keyNode = new FrozenStringLiteralNode(frozenString, FrozenStrings.EXPRESSION);
                }

                keyValues.add(keyNode);

                // when value is omitted, e.g. `a = 1; {a: }`
                if (assocNode.value instanceof Nodes.ImplicitNode implicit) {
                    if (implicit.value instanceof Nodes.CallNode call) {
                        // a special case for a method call because
                        // Prism doesn't set VARIABLE_CALL flag
                        int flags = call.flags | Nodes.CallNodeFlags.VARIABLE_CALL;
                        final var copy = new Nodes.CallNode(call.startOffset, call.length, (short) flags, call.receiver,
                                call.name, call.arguments,
                                call.block);

                        final RubyNode valueNode = copy.accept(this);
                        keyValues.add(valueNode);
                    } else {
                        // expect here Nodes.LocalVariableReadNode or Nodes.ConstantReadNode
                        final RubyNode valueNode = implicit.value.accept(this);
                        keyValues.add(valueNode);
                    }
                } else {
                    keyValues.add(assocNode.value.accept(this));
                }
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        if (!keyValues.isEmpty()) {
            final RubyNode hashLiteralSoFar = HashLiteralNode.create(keyValues.toArray(RubyNode.EMPTY_ARRAY), language);
            hashConcats.add(hashLiteralSoFar);
        }

        if (hashConcats.size() == 1) {
            final RubyNode rubyNode = hashConcats.get(0);
            return assignPositionAndFlags(node, rubyNode);
        }

        final RubyNode rubyNode = new ConcatHashLiteralNode(hashConcats.toArray(RubyNode.EMPTY_ARRAY));
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitHashPatternNode(Nodes.HashPatternNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitIfNode(Nodes.IfNode node) {
        final RubyNode conditionNode = node.predicate.accept(this);
        final RubyNode thenNode = node.statements == null ? null : node.statements.accept(this);
        final RubyNode elseNode = node.subsequent == null ? null : node.subsequent.accept(this);
        final RubyNode rubyNode;

        if (thenNode != null && elseNode != null) {
            rubyNode = IfElseNodeGen.create(conditionNode, thenNode, elseNode);
            return assignPositionAndFlags(node, rubyNode);
        } else if (thenNode != null) {
            rubyNode = IfNodeGen.create(conditionNode, thenNode);
            return assignPositionAndFlags(node, rubyNode);
        } else if (elseNode != null) {
            rubyNode = UnlessNodeGen.create(conditionNode, elseNode);
            return assignPositionAndFlags(node, rubyNode);
        } else {
            // when "then" and "else" branches are empty, e.g.
            //   if (condition)
            //   end
            return sequence(node, conditionNode, new NilLiteralNode());
        }
    }

    @Override
    public RubyNode visitImaginaryNode(Nodes.ImaginaryNode node) {
        // Translate as Complex.convert(0, b) ignoring visibility

        final RubyNode objectClassNode = new ObjectClassLiteralNode();
        final ReadConstantNode complexModuleNode = new ReadConstantNode(objectClassNode, "Complex");

        final RubyNode realComponentNode = new IntegerFixnumLiteralNode(0);
        final RubyNode imaginaryComponentNode = node.numeric.accept(this);
        RubyNode[] arguments = new RubyNode[]{ realComponentNode, imaginaryComponentNode };

        RubyNode rubyNode = createCallNode(complexModuleNode, "convert", arguments);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitImplicitNode(Nodes.ImplicitNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in #visitHashNode");
    }

    @Override
    public RubyNode visitImplicitRestNode(Nodes.ImplicitRestNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitInNode(Nodes.InNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitIndexAndWriteNode(Nodes.IndexAndWriteNode node) {
        // arguments
        final Nodes.Node[] arguments;
        if (node.arguments != null) {
            arguments = node.arguments.arguments;
        } else {
            arguments = Nodes.Node.EMPTY_ARRAY;
        }

        final RubyNode rubyNode = translateIndexOrAndIndexAndWriteNodes(true, node.receiver, arguments, node.block,
                node.value, node.flags);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitIndexOperatorWriteNode(Nodes.IndexOperatorWriteNode node) {
        // e.g. `a[b] += value` is translated into `a[b] = a[b] + value`,

        // receiver (a) and arguments (b) should be executed only once -
        // that's why they are cached in local variables

        assert node.receiver != null;

        // receiver
        final var receiverExpression = new YARPExecutedOnceExpression("opelementassign", node.receiver, this);
        final var writeReceiverNode = receiverExpression.getWriteNode();
        final var readReceiver = receiverExpression.getReadYARPNode();

        // arguments
        final Nodes.Node[] arguments;
        if (node.arguments != null) {
            arguments = node.arguments.arguments;
        } else {
            arguments = Nodes.Node.EMPTY_ARRAY;
        }
        final int argumentsCount = arguments.length;

        // block argument
        final RubyNode writeBlockNode;
        final Nodes.BlockArgumentNode blockArgument;

        if (node.block != null) {
            var expression = new YARPExecutedOnceExpression("value", node.block, this);
            writeBlockNode = expression.getWriteNode();
            Nodes.Node readBlock = expression.getReadYARPNode();
            // imitate Nodes.CallNode structure with &block argument
            blockArgument = new Nodes.BlockArgumentNode(0, 0, readBlock);
        } else {
            writeBlockNode = null;
            blockArgument = null;
        }

        final RubyNode[] writeArgumentsNodes = new RubyNode[argumentsCount];
        final Nodes.Node[] readArguments = new Nodes.Node[argumentsCount];
        for (int i = 0; i < argumentsCount; i++) {
            final var expression = new YARPExecutedOnceExpression("value", arguments[i], this);
            writeArgumentsNodes[i] = expression.getWriteNode();
            readArguments[i] = expression.getReadYARPNode();
        }

        final Nodes.Node read = new Nodes.CallNode(0, 0, node.flags, readReceiver, "[]",
                new Nodes.ArgumentsNode(0, 0, NO_FLAGS, readArguments), blockArgument);
        final Nodes.Node executeOperator = callNode(node, read, node.binary_operator, node.value);

        final Nodes.Node[] readArgumentsAndResult = new Nodes.Node[argumentsCount + 1];
        System.arraycopy(readArguments, 0, readArgumentsAndResult, 0, argumentsCount);
        readArgumentsAndResult[argumentsCount] = executeOperator;

        short writeFlags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);
        final Nodes.Node write = new Nodes.CallNode(0, 0, writeFlags, readReceiver, "[]=",
                new Nodes.ArgumentsNode(0, 0, NO_FLAGS, readArgumentsAndResult), blockArgument);
        final RubyNode writeNode = write.accept(this);
        final RubyNode writeArgumentsNode = sequence(writeArgumentsNodes);
        final RubyNode sequence;

        if (node.block != null) {
            // add block argument write node
            sequence = sequence(writeArgumentsNode, writeBlockNode, writeReceiverNode, writeNode);
        } else {
            sequence = sequence(writeArgumentsNode, writeReceiverNode, writeNode);
        }

        // defined?(a[b] += value) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitIndexOrWriteNode(Nodes.IndexOrWriteNode node) {
        // arguments
        final Nodes.Node[] arguments;
        if (node.arguments != null) {
            arguments = node.arguments.arguments;
        } else {
            arguments = Nodes.Node.EMPTY_ARRAY;
        }

        final RubyNode rubyNode = translateIndexOrAndIndexAndWriteNodes(false, node.receiver, arguments, node.block,
                node.value, node.flags);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitIndexTargetNode(Nodes.IndexTargetNode node) {
        // extra argument should be added before node translation
        // to trigger correctly replacement with inlined nodes (e.g. InlinedIndexSetNodeGen)
        // that may rely on arguments count

        final Nodes.Node[] arguments;
        final Nodes.ArgumentsNode argumentsNode;

        // add extra argument
        if (node.arguments == null) {
            arguments = new Nodes.Node[1];
        } else {
            arguments = new Nodes.Node[node.arguments.arguments.length + 1];
            for (int i = 0; i < node.arguments.arguments.length; i++) {
                arguments[i] = node.arguments.arguments[i];
            }
        }

        arguments[arguments.length - 1] = new Nodes.NilNode(0, 0);

        // arguments
        if (node.arguments == null) {
            argumentsNode = new Nodes.ArgumentsNode(0, 0, NO_FLAGS, arguments);
        } else {
            argumentsNode = new Nodes.ArgumentsNode(node.arguments.startOffset,
                    node.arguments.length, node.arguments.flags, arguments);
        }

        // Prism doesn't set ATTRIBUTE_WRITE flag, so we should add it manually
        // but preserve original flags because Prism may set them, e.g. IGNORE_VISIBILITY flag
        short writeFlags = (short) (node.flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);
        final var callNode = new Nodes.CallNode(node.startOffset,
                node.length, writeFlags, node.receiver, "[]=", argumentsNode, node.block);
        return callNode.accept(this);
    }


    private RubyNode translateIndexOrAndIndexAndWriteNodes(boolean isAndOperator, Nodes.Node receiver,
            Nodes.Node[] arguments, Nodes.Node block, Nodes.Node value, short flags) {
        // Handle both &&= and ||= operators:
        //   `a[b] ||= c` is translated into `a[b] || a[b] = c`
        //   `a[b] &&= c` is translated into `a[b] && a[b] = c`

        // receiver (a) and arguments (b) should be executed only once
        // that's why they are cached in local variables

        assert receiver != null;

        // receiver
        final var receiverExpression = new YARPExecutedOnceExpression("opelementassign", receiver, this);
        final var writeReceiverNode = receiverExpression.getWriteNode();
        final var readReceiver = receiverExpression.getReadYARPNode();

        final RubyNode[] writeArgumentsNodes = new RubyNode[arguments.length];
        final Nodes.Node[] readArguments = new Nodes.Node[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            final var expression = new YARPExecutedOnceExpression("value", arguments[i], this);
            writeArgumentsNodes[i] = expression.getWriteNode();
            readArguments[i] = expression.getReadYARPNode();
        }

        // block argument
        final RubyNode writeBlockNode;
        final Nodes.BlockArgumentNode blockArgument;

        if (block != null) {
            var expression = new YARPExecutedOnceExpression("value", block, this);
            writeBlockNode = expression.getWriteNode();
            Nodes.Node readBlock = expression.getReadYARPNode();
            // imitate Nodes.CallNode structure with &block argument
            blockArgument = new Nodes.BlockArgumentNode(0, 0, readBlock);
        } else {
            writeBlockNode = null;
            blockArgument = null;
        }

        final Nodes.Node read = new Nodes.CallNode(0, 0, flags, readReceiver, "[]",
                new Nodes.ArgumentsNode(0, 0, NO_FLAGS, readArguments), blockArgument);
        final RubyNode readNode = read.accept(this);

        final Nodes.Node[] readArgumentsAndValue = new Nodes.Node[arguments.length + 1];
        System.arraycopy(readArguments, 0, readArgumentsAndValue, 0, arguments.length);
        readArgumentsAndValue[arguments.length] = value;

        short writeFlags = (short) (flags | Nodes.CallNodeFlags.ATTRIBUTE_WRITE);
        final Nodes.Node write = new Nodes.CallNode(0, 0, writeFlags, readReceiver, "[]=",
                new Nodes.ArgumentsNode(0, 0, NO_FLAGS, readArgumentsAndValue), blockArgument);
        final RubyNode writeNode = write.accept(this);

        final RubyNode operatorNode;
        if (isAndOperator) {
            operatorNode = AndNodeGen.create(readNode, writeNode);
        } else {
            operatorNode = OrLazyValueDefinedNodeGen.create(readNode, writeNode);
        }

        final RubyNode writeArgumentsNode = sequence(writeArgumentsNodes);
        final RubyNode sequence;

        if (block != null) {
            // add block argument write node
            sequence = sequence(writeArgumentsNode, writeBlockNode, writeReceiverNode, operatorNode);
        } else {
            sequence = sequence(writeArgumentsNode, writeReceiverNode, operatorNode);
        }

        // defined?(a[b] ||= c) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, sequence);
        return rubyNode;
    }

    @Override
    public RubyNode visitInstanceVariableAndWriteNode(Nodes.InstanceVariableAndWriteNode node) {
        // `@a &&= value` is translated into `@a && @a = value`
        // don't check whether variable is initialized because even if an instance variable
        // is not set then it returns nil and does not have side effects (warnings or exceptions)

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.InstanceVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.InstanceVariableReadNode(startOffset, length, node.name).accept(this);
        var andNode = AndNodeGen.create(readNode, writeNode);

        // defined?(@a &&= b) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInstanceVariableOperatorWriteNode(Nodes.InstanceVariableOperatorWriteNode node) {
        // e.g. `@a += value` is translated into @a = @a + value`
        // don't check whether variable is defined so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;

        var readNode = new Nodes.InstanceVariableReadNode(startOffset, length, node.name);
        var desugared = new Nodes.InstanceVariableWriteNode(startOffset, length, node.name,
                callNode(node, readNode, node.binary_operator, node.value));
        return desugared.accept(this);
    }

    @Override
    public RubyNode visitInstanceVariableOrWriteNode(Nodes.InstanceVariableOrWriteNode node) {
        // `@a ||= value` is translated into `@a || @a = value`

        // No need to check `defined?(@ivar)` before reading, as `@ivar` even if not set returns nil and does not have
        // side effects (warnings or exceptions)

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.InstanceVariableWriteNode(startOffset, length, node.name, node.value).accept(this);
        var readNode = new Nodes.InstanceVariableReadNode(startOffset, length, node.name).accept(this);

        final RubyNode rubyNode = OrLazyValueDefinedNodeGen.create(readNode, writeNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInstanceVariableReadNode(Nodes.InstanceVariableReadNode node) {
        final RubyNode rubyNode = new ReadInstanceVariableNode(node.name);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInstanceVariableWriteNode(Nodes.InstanceVariableWriteNode node) {
        final RubyNode value = node.value.accept(this);
        final RubyNode rubyNode = WriteInstanceVariableNodeGen.create(node.name, value);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInstanceVariableTargetNode(Nodes.InstanceVariableTargetNode node) {
        final RubyNode rubyNode = WriteInstanceVariableNodeGen.create(node.name, null);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitIntegerNode(Nodes.IntegerNode node) {
        final RubyNode rubyNode = translateNumericValue(node.value);
        return assignPositionAndFlags(node, rubyNode);
    }

    private RubyNode translateNumericValue(Object value) {
        if (value instanceof Integer i) {
            return new IntegerFixnumLiteralNode(i);
        } else if (value instanceof Long l) {
            return new LongFixnumLiteralNode(l);
        } else if (value instanceof BigInteger bigInteger) {
            return new ObjectLiteralNode(new RubyBignum(bigInteger));
        } else {
            throw CompilerDirectives.shouldNotReachHere(value.getClass().getName());
        }
    }

    @Override
    public RubyNode visitInterpolatedMatchLastLineNode(Nodes.InterpolatedMatchLastLineNode node) {
        // replace regexp with /.../ =~ $_

        final var regexp = new Nodes.InterpolatedRegularExpressionNode(node.startOffset,
                node.length, node.flags, node.parts);
        final var regexpNode = regexp.accept(this);
        final var lastLineNode = ReadGlobalVariableNodeGen.create("$_");

        final RubyNode rubyNode = createCallNode(false, regexpNode, "=~", lastLineNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInterpolatedRegularExpressionNode(Nodes.InterpolatedRegularExpressionNode node) {
        var encodingAndOptions = getRegexpEncodingAndOptions(new Nodes.RegularExpressionFlags(node.flags));
        var options = encodingAndOptions.options;
        final ToSNode[] children = translateInterpolatedPartsIgnoreForceEncodingFlags(node.parts);

        final RubyEncoding encoding;
        if (!options.isKcodeDefault()) { // explicit encoding
            encoding = encodingAndOptions.encoding;
        } else {
            // Use BINARY explicitly probably because forcing encoding isn't implemented yet in Prism
            // Needed until https://github.com/ruby/prism/issues/2620 is fixed
            // The logic comes from ParserSupport#createMaster
            encoding = Encodings.BINARY;
        }

        for (ToSNode child : children) {
            // Expect that String fragments are represented either with FrozenStringLiteralNode or StringLiteralNode.
            // StringLiteralNode is possible in the following case: /a #{ "b" } c/
            if (child.getValueNode() instanceof FrozenStringLiteralNode ||
                    child.getValueNode() instanceof StringLiteralNode) {
                final TStringWithEncoding fragment;

                if (child.getValueNode() instanceof FrozenStringLiteralNode frozenStringLiteralNode) {
                    ImmutableRubyString frozenString = frozenStringLiteralNode.getFrozenString();
                    fragment = new TStringWithEncoding(frozenString.tstring, frozenString.encoding);
                } else if (child.getValueNode() instanceof StringLiteralNode stringLiteralNode) {
                    fragment = new TStringWithEncoding(stringLiteralNode.getTString(), stringLiteralNode.getEncoding());
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }

                try {
                    // MRI: reg_fragment_check
                    var strEnc = ClassicRegexp.setRegexpEncoding(fragment, options, sourceEncoding, currentNode);
                    ClassicRegexp.preprocessCheck(strEnc);
                } catch (DeferredRaiseException dre) {
                    throw regexpErrorToSyntaxError(dre, node);
                }
            }
        }

        RubyNode rubyNode = InterpolatedRegexpNodeGen.create(encoding, options, children);

        if (node.isOnce()) {
            rubyNode = new OnceNode(rubyNode);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInterpolatedStringNode(Nodes.InterpolatedStringNode node) {
        if (isSourceEncodingOnly(node)) {
            // skip encoding negotiation
            return visitStringNode(concatStringNodes(node));
        }

        final ToSNode[] children = translateInterpolatedParts(node.parts);

        final RubyNode rubyNode = new InterpolatedStringNode(children, sourceEncoding);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInterpolatedSymbolNode(Nodes.InterpolatedSymbolNode node) {
        final ToSNode[] children = translateInterpolatedParts(node.parts);
        final RubyNode stringNode = new InterpolatedStringNode(children, sourceEncoding);

        final RubyNode rubyNode = StringToSymbolNodeGen.create(stringNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitInterpolatedXStringNode(Nodes.InterpolatedXStringNode node) {
        // replace `` literal with a Kernel#` method call

        var stringNode = new Nodes.InterpolatedStringNode(node.startOffset, node.length, NO_FLAGS, node.parts);
        final RubyNode string = stringNode.accept(this);

        final RubyNode rubyNode = createCallNode(new SelfNode(), "`", string);
        return assignPositionAndFlags(node, rubyNode);
    }

    /** Return whether all parts of interpolated String are String literals, and they have the same encoding - source
     * file encoding (e.g. in `s = "abc" "def" "ghi"`) */
    private boolean isSourceEncodingOnly(Nodes.InterpolatedStringNode node) {
        for (var part : node.parts) {
            if (!(part instanceof Nodes.StringNode stringNode)) {
                return false;
            }

            // check all the possible force-encoding flags - forced encoding should equal the source encoding

            if (stringNode.isForcedBinaryEncoding() && sourceEncoding != Encodings.BINARY) {
                return false;
            }

            if (stringNode.isForcedUtf8Encoding() && sourceEncoding != Encodings.UTF_8) {
                return false;
            }
        }

        return true;
    }

    /** Join parts of interpolated String into a single Prism.StringNode */
    private static Nodes.StringNode concatStringNodes(Nodes.InterpolatedStringNode node) {
        Nodes.Node[] parts = node.parts;
        assert parts.length > 0;

        int totalSize = 0;
        for (var part : parts) {
            totalSize += ((Nodes.StringNode) part).unescaped.length;
        }

        byte[] concatenated = new byte[totalSize];
        int i = 0;
        for (var part : parts) {
            byte[] bytes = ((Nodes.StringNode) part).unescaped;
            System.arraycopy(bytes, 0, concatenated, i, bytes.length);
            i += bytes.length;
        }

        int start = parts[0].startOffset;
        var last = ArrayUtils.getLast(parts);
        int length = last.endOffset() - start;
        short flags = node.isFrozen() ? Nodes.StringFlags.FROZEN : NO_FLAGS;

        // Prism may assign a new-line flag to one of the nested parts instead of the outer String node
        boolean isNewLineFlag = node.hasNewLineFlag();
        for (var part : parts) {
            if (part.hasNewLineFlag()) {
                isNewLineFlag = true;
            }
        }

        var stringNode = new Nodes.StringNode(start, length, flags, concatenated);
        stringNode.setNewLineFlag(isNewLineFlag);
        return stringNode;
    }

    /** Translate parts of interpolated String, Symbol or Regexp */
    private ToSNode[] translateInterpolatedParts(Nodes.Node[] parts) {
        final ToSNode[] children = new ToSNode[parts.length];

        for (int i = 0; i < parts.length; i++) {
            RubyNode expression = parts[i].accept(this);
            children[i] = ToSNodeGen.create(expression);
        }

        return children;
    }

    /** Regexp encoding negotiation does not work correctly if such flags are kept, e.g. for /#{ }\xc2\xa1/e in
     * test_m17n.rb. Not clear what is a good solution yet. */
    private ToSNode[] translateInterpolatedPartsIgnoreForceEncodingFlags(Nodes.Node[] parts) {
        final ToSNode[] children = new ToSNode[parts.length];

        for (int i = 0; i < parts.length; i++) {
            RubyNode expression;
            if (parts[i] instanceof Nodes.StringNode stringNode) {
                short flags = stringNode.isFrozen() ? Nodes.StringFlags.FROZEN : NO_FLAGS;
                Nodes.StringNode stringNodeNoForceEncoding = new Nodes.StringNode(stringNode.startOffset,
                        stringNode.length, flags, stringNode.unescaped);

                // Prism might assign new line flag not to the outer regexp node but to its first part instead
                copyNewLineFlag(stringNode, stringNodeNoForceEncoding);

                expression = stringNodeNoForceEncoding.accept(this);
            } else {
                expression = parts[i].accept(this);
            }
            children[i] = ToSNodeGen.create(expression);
        }

        return children;
    }

    @Override
    public RubyNode visitItLocalVariableReadNode(Nodes.ItLocalVariableReadNode node) {
        return assignPositionAndFlags(node, environment.findLocalVarNode(IT_PARAMETER_NAME));
    }

    @Override
    public RubyNode visitItParametersNode(Nodes.ItParametersNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in translateBlockAndLambda");
    }

    @Override
    public RubyNode visitKeywordHashNode(Nodes.KeywordHashNode node) {
        // translate it like a HashNode, whether it is keywords or not is checked in getKeywordArgumentsDescriptor()
        final var hash = new Nodes.HashNode(node.startOffset, node.length, node.elements);
        return hash.accept(this);
    }

    @Override
    public RubyNode visitKeywordRestParameterNode(Nodes.KeywordRestParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitLambdaNode(Nodes.LambdaNode node) {
        return translateBlockAndLambda(node, node.parameters, node.body, node.locals, null);
    }

    @Override
    public RubyNode visitLocalVariableReadNode(Nodes.LocalVariableReadNode node) {
        final String name = node.name;

        final RubyNode rubyNode = environment.findLocalVarNode(name);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitLocalVariableAndWriteNode(Nodes.LocalVariableAndWriteNode node) {
        // `a &&= value` is translated into `a && a = value`
        // don't check whether variable is initialized because even if a local variable
        // is not set then it returns nil and does not have side effects (warnings or exceptions)

        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.LocalVariableWriteNode(startOffset, length, node.name, node.depth, node.value)
                .accept(this);
        var readNode = new Nodes.LocalVariableReadNode(startOffset, length, node.name, node.depth).accept(this);
        var andNode = AndNodeGen.create(readNode, writeNode);

        // defined?(a &&= b) should return 'assignment'
        final RubyNode rubyNode = new DefinedWrapperNode(language.coreStrings.ASSIGNMENT, andNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitLocalVariableOperatorWriteNode(Nodes.LocalVariableOperatorWriteNode node) {
        // e.g. `a += value` is translated into a = a + value`
        // don't check whether variable is initialized so exception will be raised if it isn't

        int startOffset = node.startOffset;
        int length = node.length;
        var readNode = new Nodes.LocalVariableReadNode(startOffset, length, node.name, node.depth);
        var desugared = new Nodes.LocalVariableWriteNode(startOffset, length, node.name, node.depth,
                callNode(node, readNode, node.binary_operator, node.value));
        return desugared.accept(this);
    }

    @Override
    public RubyNode visitLocalVariableOrWriteNode(Nodes.LocalVariableOrWriteNode node) {
        // `a ||= value` is translated into `a || a = value`

        // No need to check `defined?(var)` before reading, as `var` even if not set returns nil and does not have
        // side effects (warnings or exceptions)
        int startOffset = node.startOffset;
        int length = node.length;

        var writeNode = new Nodes.LocalVariableWriteNode(startOffset, length, node.name, node.depth, node.value)
                .accept(this);
        var readNode = new Nodes.LocalVariableReadNode(startOffset, length, node.name, node.depth).accept(this);

        final RubyNode rubyNode = OrLazyValueDefinedNodeGen.create(readNode, writeNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public WriteLocalNode visitLocalVariableWriteNode(Nodes.LocalVariableWriteNode node) {
        final String name = node.name;
        final ReadLocalNode lhs = environment.findLocalVarNode(name);
        final RubyNode rhs = node.value.accept(this);
        final WriteLocalNode rubyNode = lhs.makeWriteNode(rhs);

        assignPositionAndFlags(node, rubyNode);
        return rubyNode;
    }

    @Override
    public WriteLocalNode visitLocalVariableTargetNode(Nodes.LocalVariableTargetNode node) {
        final String name = node.name;
        final ReadLocalNode lhs = environment.findLocalVarNode(name);
        final WriteLocalNode rubyNode = lhs.makeWriteNode(null);

        assignPositionAndFlags(node, rubyNode);
        return rubyNode;
    }

    @Override
    public RubyNode visitMatchLastLineNode(Nodes.MatchLastLineNode node) {
        // replace regexp with /.../ =~ $_

        final var regexp = new Nodes.RegularExpressionNode(node.startOffset, node.length, node.flags, node.unescaped);
        final var regexpNode = regexp.accept(this);
        final var lastLineNode = ReadGlobalVariableNodeGen.create("$_");

        final RubyNode rubyNode = createCallNode(false, regexpNode, "=~", lastLineNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitMatchPredicateNode(Nodes.MatchPredicateNode node) {
        var translator = new YARPPatternMatchingTranslator(environment, this);

        // Evaluate the expression and store it in a local
        final int tempSlot = environment.declareLocalTemp("value_of_=>");
        final ReadLocalNode readTemp = environment.readNode(tempSlot, node);
        final RubyNode assignTemp = readTemp.makeWriteNode(node.value.accept(this));

        RubyNode condition = translator.translatePatternNode(node.pattern, readTemp);

        final RubyNode ret = sequence(assignTemp, condition);
        return assignPositionAndFlags(node, ret);
    }

    @Override
    public RubyNode visitMatchRequiredNode(Nodes.MatchRequiredNode node) {
        var translator = new YARPPatternMatchingTranslator(environment, this);

        // Evaluate the expression and store it in a local
        final int tempSlot = environment.declareLocalTemp("value_of_=>");
        final ReadLocalNode readTemp = environment.readNode(tempSlot, node);
        final RubyNode assignTemp = readTemp.makeWriteNode(node.value.accept(this));

        RubyNode condition = translator.translatePatternNode(node.pattern, readTemp);
        RubyNode check = UnlessNodeGen.create(condition, NoMatchingPatternNodeGen.create(NodeUtil.cloneNode(readTemp)));

        final RubyNode ret = sequence(assignTemp, check);
        return assignPositionAndFlags(node, ret);
    }

    @Override
    public RubyNode visitMatchWriteNode(Nodes.MatchWriteNode node) {
        // node.call already contains an AST for the #=~ method call (/.../.=~(s))
        assert node.call.receiver instanceof Nodes.RegularExpressionNode;
        assert node.call.name.equals("=~");
        assert node.call.arguments != null;
        assert node.call.arguments.arguments.length == 1;
        assert node.targets.length > 0;

        RubyNode matchNode = node.call.accept(this);

        // build nodes to initialize local variables for Regexp named capture groups
        final int numberOfNames = node.targets.length;
        String[] names = new String[numberOfNames];

        for (int i = 0; i < numberOfNames; i++) {
            names[i] = node.targets[i].name;
        }

        final RubyNode[] setters = new RubyNode[numberOfNames];
        final RubyNode[] nilSetters = new RubyNode[numberOfNames];
        final int tempSlot = environment.declareLocalTemp("match_data");

        for (int i = 0; i < numberOfNames; i++) {
            final String name = names[i];

            TranslatorEnvironment environmentToDeclareIn = environment;

            // TODO: use Nodes.LocalVariableTargetNode#depth field
            while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                environmentToDeclareIn = environmentToDeclareIn.getParent();
            }
            environmentToDeclareIn.declareVar(name);
            nilSetters[i] = match2NilSetter(name);
            setters[i] = match2NonNilSetter(name, tempSlot);
        }

        final RubyNode readNode = ReadGlobalVariableNodeGen.create("$~");
        final ReadLocalNode tempVarReadNode = environment.readNode(tempSlot);
        final RubyNode readMatchDataNode = tempVarReadNode.makeWriteNode(readNode);
        final RubyNode rubyNode = new ReadMatchReferenceNodes.SetNamedVariablesMatchNode(matchNode, readMatchDataNode,
                setters, nilSetters);

        return assignPositionAndFlags(node, rubyNode);
    }

    private RubyNode match2NilSetter(String name) {
        return environment.findLocalVarNode(name).makeWriteNode(new NilLiteralNode());
    }

    private RubyNode match2NonNilSetter(String name, int tempSlot) {
        ReadLocalNode varNode = environment.findLocalVarNode(name);
        ReadLocalNode tempVarNode = environment.readNode(tempSlot);
        MatchDataNodes.GetFixedNameMatchNode getIndexNode = new MatchDataNodes.GetFixedNameMatchNode(tempVarNode,
                language.getSymbol(name));
        return varNode.makeWriteNode(getIndexNode);
    }

    @Override
    public RubyNode visitMissingNode(Nodes.MissingNode node) {
        throw fail(node);
    }

    @Override
    public RubyNode visitModuleNode(Nodes.ModuleNode node) {
        final RubyNode lexicalParent = getParentLexicalScopeForConstant(node.constant_path);

        final DefineModuleNode defineModuleNode = DefineModuleNodeGen.create(node.name, lexicalParent);
        assignPositionOnly(node, defineModuleNode); // to assign source location to a new constant

        final RubyNode rubyNode = openModule(
                node,
                defineModuleNode,
                appendConstantPathToModulePath(node.constant_path),
                node.name,
                node.locals,
                node.body,
                OpenModule.MODULE,
                shouldUseDynamicConstantLookupForModuleBody(node));

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitMultiWriteNode(Nodes.MultiWriteNode node) {
        final RubyNode rubyNode;
        var translator = new YARPMultiWriteNodeTranslator(node, language, this);
        rubyNode = translator.translate();

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitMultiTargetNode(Nodes.MultiTargetNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitForNode and other places");
    }

    @Override
    public RubyNode visitNextNode(Nodes.NextNode node) {
        // The next operator is invalid outside a proc body or while-loop body.
        // The parser doesn't handle it on its own so do it manually.
        if (!environment.isBlock() && !translatingWhile) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid next",
                            currentNode,
                            getSourceSection(node)));
        }

        final RubyNode argumentsNode = translateControlFlowArguments(node.arguments);
        final RubyNode rubyNode = new NextNode(argumentsNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitNilNode(Nodes.NilNode node) {
        final RubyNode rubyNode = new NilLiteralNode();
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitNoKeywordsParameterNode(Nodes.NoKeywordsParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitNumberedParametersNode(Nodes.NumberedParametersNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in translateBlockAndLambda");
    }

    @Override
    public RubyNode visitNumberedReferenceReadNode(Nodes.NumberedReferenceReadNode node) {
        // numbered references that are too large, e.g. $4294967296, are always `nil`
        if (node.number == 0) {
            final RubyNode rubyNode = new NilLiteralNode();
            return assignPositionAndFlags(node, rubyNode);
        }

        final RubyNode lastMatchNode = ReadGlobalVariableNodeGen.create("$~");
        final RubyNode rubyNode = new ReadMatchReferenceNodes.ReadNthMatchNode(lastMatchNode, node.number);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitOptionalKeywordParameterNode(Nodes.OptionalKeywordParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitOptionalParameterNode(Nodes.OptionalParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitOrNode(Nodes.OrNode node) {
        final RubyNode left = node.left.accept(this);
        final RubyNode right = node.right.accept(this);

        final RubyNode rubyNode = OrNodeGen.create(left, right);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitParametersNode(Nodes.ParametersNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitParenthesesNode(Nodes.ParenthesesNode node) {
        if (node.body == null) {
            final RubyNode rubyNode = new NilLiteralNode();
            return assignPositionAndFlags(node, rubyNode);
        }
        return node.body.accept(this);
    }

    @Override
    public RubyNode visitPinnedExpressionNode(Nodes.PinnedExpressionNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitPinnedVariableNode(Nodes.PinnedVariableNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPPatternMatchingTranslator");
    }

    @Override
    public RubyNode visitPostExecutionNode(Nodes.PostExecutionNode node) {
        // END blocks run after any other code - not just code in the same file
        // Turn into a call to Truffle::KernelOperations.at_exit

        // Create Prism CallNode to avoid duplication block literal related logic
        final var receiver = new Nodes.ConstantPathNode(
                0, 0, new Nodes.ConstantReadNode(0, 0, "Truffle"),
                "KernelOperations");
        final var arguments = new Nodes.ArgumentsNode(0, 0, NO_FLAGS, new Nodes.Node[]{ new Nodes.FalseNode(0, 0) });
        final var block = new Nodes.BlockNode(0, 0, StringUtils.EMPTY_STRING_ARRAY, null, node.statements);

        final var callNode = new Nodes.CallNode(0, 0, NO_FLAGS, receiver, "at_exit", arguments, block).accept(this);
        final RubyNode rubyNode = new OnceNode(callNode);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitPreExecutionNode(Nodes.PreExecutionNode node) {
        // BEGIN should be located in the top-level context, so it safe to evaluate a block right now
        RubyNode sequence = node.statements.accept(this);
        beginBlocks.add(sequence);
        return null;
    }

    @Override
    public RubyNode visitProgramNode(Nodes.ProgramNode node) {
        // declare variable defined at the top-level
        for (String name : node.locals) {
            environment.declareVar(name);
        }

        // Don't prepend BEGIN blocks here because there are additional nodes prepended
        // after program translation to handle Ruby's -l and -n command line options.
        // So BEGIN blocks should precede these additional nodes at the very
        // beginning of a program.
        return node.statements.accept(this);
    }

    @Override
    public RubyNode visitRangeNode(Nodes.RangeNode node) {
        var left = translateNodeOrNil(node.left);
        var right = translateNodeOrNil(node.right);

        final RubyNode rubyNode;
        if (left instanceof IntegerFixnumLiteralNode l && right instanceof IntegerFixnumLiteralNode r) {
            final Object range = new RubyIntRange(node.isExcludeEnd(), l.getValue(), r.getValue());
            rubyNode = new ObjectLiteralNode(range);
        } else if (left instanceof LongFixnumLiteralNode l && right instanceof LongFixnumLiteralNode r) {
            final Object range = new RubyLongRange(node.isExcludeEnd(), l.getValue(), r.getValue());
            rubyNode = new ObjectLiteralNode(range);
        } else {
            rubyNode = RangeNodesFactory.RangeLiteralNodeGen.create(left, right, node.isExcludeEnd());
        }
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitRationalNode(Nodes.RationalNode node) {
        // Translate as Rational.convert private method call

        final RubyNode objectClassNode = new ObjectClassLiteralNode();
        final ReadConstantNode rationalModuleNode = new ReadConstantNode(objectClassNode, "Rational");
        final RubyNode numeratorNode = translateNumericValue(node.numerator);
        final RubyNode denominatorNode = translateNumericValue(node.denominator);

        RubyNode[] arguments = new RubyNode[]{ numeratorNode, denominatorNode, new BooleanLiteralNode(true) };
        RubyNode rubyNode = createCallNode(rationalModuleNode, "convert", arguments);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitRedoNode(Nodes.RedoNode node) {
        // The redo operator is invalid outside a proc body or while-loop body.
        // The parser doesn't handle it on its own so do it manually.
        if (!environment.isBlock() && !translatingWhile) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid redo",
                            currentNode,
                            getSourceSection(node)));
        }

        final RubyNode rubyNode = new RedoNode();
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitRegularExpressionNode(Nodes.RegularExpressionNode node) {
        var encodingAndOptions = getRegexpEncodingAndOptions(new Nodes.RegularExpressionFlags(node.flags));
        var encoding = encodingAndOptions.encoding;
        var options = encodingAndOptions.options;
        var source = TruffleString.fromByteArrayUncached(node.unescaped, encoding.tencoding, false);
        var sourceWithEnc = new TStringWithEncoding(source, encoding);

        final RubyRegexp regexp;
        try {
            // Needed until https://github.com/ruby/prism/issues/2620 is fixed
            sourceWithEnc = ClassicRegexp.setRegexpEncoding(sourceWithEnc, options, sourceEncoding, currentNode);

            regexp = RubyRegexp.create(language, sourceWithEnc.tstring, sourceWithEnc.encoding,
                    options, currentNode);
        } catch (DeferredRaiseException dre) {
            throw regexpErrorToSyntaxError(dre, node);
        }

        final ObjectLiteralNode literalNode = new ObjectLiteralNode(regexp);
        return assignPositionAndFlags(node, literalNode);
    }

    private record RegexpEncodingAndOptions(RubyEncoding encoding, RegexpOptions options) {
    }

    /** Return Regexp modifiers (e.g. m, i, x, ...) and encoding. Encoding is based on encoding modifiers (n, u, e, s)
     * and could be inferred (forced) from Regexp source characters. If there are no encoding modifiers and encoding is
     * not forced - the source file encoding is returned. */
    private RegexpEncodingAndOptions getRegexpEncodingAndOptions(Nodes.RegularExpressionFlags flags) {
        RubyEncoding regexpEncoding;

        // regexp options
        final KCode kcode;
        final boolean fixed;
        boolean explicitEncoding = true;

        if (flags.isAscii8bit()) {
            fixed = false;
            kcode = KCode.NONE;
            regexpEncoding = Encodings.BINARY;
        } else if (flags.isUtf8()) {
            fixed = true;
            kcode = KCode.UTF8;
            regexpEncoding = Encodings.UTF_8;
        } else if (flags.isEucJp()) {
            fixed = true;
            kcode = KCode.EUC;
            regexpEncoding = Encodings.getBuiltInEncoding(EUCJPEncoding.INSTANCE);
        } else if (flags.isWindows31j()) {
            fixed = true;
            kcode = KCode.SJIS;
            regexpEncoding = Encodings.getBuiltInEncoding(Windows_31JEncoding.INSTANCE);
        } else {
            fixed = false;
            kcode = KCode.NONE;
            regexpEncoding = sourceEncoding;
            explicitEncoding = false;
        }

        // Don't check forced encoding flags until https://github.com/ruby/prism/issues/2620 is fixed
        // if (!explicitEncoding) {
        //     if (flags.isForcedBinaryEncoding()) {
        //         regexpEncoding = Encodings.BINARY;
        //     } else if (flags.isForcedUsAsciiEncoding()) {
        //         regexpEncoding = Encodings.US_ASCII;
        //     } else if (flags.isForcedUtf8Encoding()) {
        //         regexpEncoding = Encodings.UTF_8;
        //     }
        // }

        final RegexpOptions options = new RegexpOptions(kcode, fixed, flags.isOnce(), flags.isExtended(),
                flags.isMultiLine(), flags.isIgnoreCase(), flags.isAscii8bit(), !explicitEncoding, true);
        return new RegexpEncodingAndOptions(regexpEncoding, options);
    }

    private RaiseException regexpErrorToSyntaxError(DeferredRaiseException dre, Nodes.Node node) {
        var context = RubyLanguage.getCurrentContext();
        RaiseException raiseException = dre.getException(context);
        if (raiseException.getException().getLogicalClass() == context.getCoreLibrary().regexpErrorClass) {
            // Convert RegexpError to SyntaxError when found during parsing/translating for compatibility
            throw new RaiseException(context, context.getCoreExceptions().syntaxError(raiseException.getMessage(),
                    currentNode, getSourceSection(node)));
        } else {
            throw raiseException;
        }
    }

    @Override
    public RubyNode visitRequiredKeywordParameterNode(Nodes.RequiredKeywordParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitRequiredParameterNode(Nodes.RequiredParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitRescueModifierNode(Nodes.RescueModifierNode node) {
        // use Ruby StandardError class as far as exception class cannot be specified

        RubyNode tryNode = node.expression.accept(this);
        final RubyNode rescueExpressionNode = node.rescue_expression.accept(this);
        boolean canOmitBacktrace = language.options.BACKTRACES_OMIT_UNUSED &&
                isSideEffectFreeRescueExpression(node.rescue_expression);
        final RescueStandardErrorNode rescueNode = new RescueStandardErrorNode(rescueExpressionNode, canOmitBacktrace);

        final RubyNode rubyNode = TryNodeGen.create(
                tryNode,
                new RescueNode[]{ rescueNode },
                null);

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitRescueNode(Nodes.RescueNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitBeginNode");
    }

    @Override
    public RubyNode visitRestParameterNode(Nodes.RestParameterNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in YARPLoadArgumentsTranslator");
    }

    @Override
    public RubyNode visitRetryNode(Nodes.RetryNode node) {
        final RubyNode rubyNode = new RetryNode();
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitReturnNode(Nodes.ReturnNode node) {
        final RubyNode rubyNode;
        final RubyNode argumentsNode = translateControlFlowArguments(node.arguments);

        // either in block or lambda
        if (environment.isBlock()) {
            // Lambda behaves a bit differently from block and "return" to a class/module body is correct -
            // so DynamicReturnNode should be used instead of InvalidReturnNode.
            // It's handled later and InvalidReturnNode is replaced with DynamicReturnNode in YARPBlockTranslator.
            final ReturnID returnID = environment.getReturnID();
            if (returnID == ReturnID.MODULE_BODY) {
                rubyNode = new InvalidReturnNode(argumentsNode);
            } else {
                rubyNode = new DynamicReturnNode(returnID, argumentsNode);
            }
        } else {
            rubyNode = new LocalReturnNode(argumentsNode);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSelfNode(Nodes.SelfNode node) {
        final RubyNode rubyNode = new SelfNode();
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSingletonClassNode(Nodes.SingletonClassNode node) {
        final RubyNode receiverNode = node.expression.accept(this);
        var singletonClassNode = SingletonClassASTNodeGen.create(receiverNode);
        assignPositionOnly(node, singletonClassNode);

        boolean dynamicConstantLookup = environment.isDynamicConstantLookup();

        final String moduleBasename = "<singleton class>";
        String modulePath = moduleBasename;
        if (!dynamicConstantLookup) {
            if (environment.isModuleBody() && node.expression instanceof Nodes.SelfNode) {
                // Common case of class << self in a module body, the constant lookup scope is still static.
                if (environment.isTopLevelObjectScope()) {
                    // Special pattern recognized by #modulePathAndMethodName:
                    modulePath = "main::<singleton class>";
                } else {
                    modulePath = TranslatorEnvironment.composeModulePath(environment.modulePath, moduleBasename);
                }
            } else if (environment.isTopLevelScope()) {
                // At the top-level of a file, opening the singleton class of an expression executed only once
            } else {
                // Switch to dynamic constant lookup
                dynamicConstantLookup = true;
                if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                    RubyLanguage.LOGGER.info(
                            () -> "start dynamic constant lookup at " + language.fileLine(getSourceSection(node)));
                }
            }
        }

        final RubyNode rubyNode = openModule(
                node,
                singletonClassNode,
                modulePath,
                moduleBasename,
                node.locals,
                node.body,
                OpenModule.SINGLETON_CLASS,
                dynamicConstantLookup);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSourceEncodingNode(Nodes.SourceEncodingNode node) {
        final RubyNode rubyNode = new ObjectLiteralNode(sourceEncoding);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSourceFileNode(Nodes.SourceFileNode node) {
        RubyEncoding encoding = Encodings.FILESYSTEM;
        var path = TruffleString.fromByteArrayUncached(node.filepath, encoding.tencoding);
        var rubyNode = new StringLiteralNode(path, encoding);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSourceLineNode(Nodes.SourceLineNode node) {
        // Note: we use the YARP source here, notably to account for the lineOffset.
        // Instead of getSourceSection(node).getStartLine() which would also create the TextMap early.
        int line = parseEnvironment.yarpSource.line(node.startOffset);
        var rubyNode = new IntegerFixnumLiteralNode(line);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSplatNode(Nodes.SplatNode node) {
        final RubyNode rubyNode;

        if (node.expression != null) {
            final RubyNode valueNode = node.expression.accept(this);
            rubyNode = SplatCastNodeGen.create(language, SplatCastNode.NilBehavior.CONVERT, false,
                    valueNode);
        } else {
            // forwarding * in a method call, e.g.
            //   def foo(*)
            //     bar(*)
            //   end

            // no need for SplatCastNodeGen for * because it's always an Array and cannot be reassigned
            rubyNode = environment.findLocalVarNode(DEFAULT_REST_NAME);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitStatementsNode(Nodes.StatementsNode node) {
        RubyNode[] rubyNodes = translate(node.body);
        return sequence(node, rubyNodes);
    }

    @Override
    public RubyNode visitShareableConstantNode(Nodes.ShareableConstantNode node) {
        // the shareable constants logic isn't supported yet, so treat shareable constants as regular
        return node.write.accept(this);
    }

    @Override
    public RubyNode visitStringNode(Nodes.StringNode node) {
        final RubyNode rubyNode;
        final RubyEncoding encoding;

        if (node.isForcedUtf8Encoding()) {
            encoding = Encodings.UTF_8;
        } else if (node.isForcedBinaryEncoding()) {
            encoding = Encodings.BINARY;
        } else {
            encoding = sourceEncoding;
        }

        byte[] bytes = node.unescaped;

        if (!node.isFrozen()) {
            final TruffleString cachedTString = language.tstringCache.getTString(bytes, encoding);
            rubyNode = new StringLiteralNode(cachedTString, encoding);
        } else {
            ImmutableRubyString immutableString = language.immutableStrings.get(bytes, encoding);
            rubyNode = new FrozenStringLiteralNode(immutableString, FrozenStrings.EXPRESSION);
        }

        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitSuperNode(Nodes.SuperNode node) {
        var argumentsAndBlock = translateArgumentsAndBlock(node.arguments, node.block, environment.getMethodName());

        final RubyNode arguments = new ReadSuperArgumentsNode(
                argumentsAndBlock.arguments,
                argumentsAndBlock.isSplatted);
        final RubyNode block = explicitOrInheritedBlock(argumentsAndBlock.block);

        RubyNode callNode = new SuperCallNode(argumentsAndBlock.isSplatted, arguments, block,
                argumentsAndBlock.argumentsDescriptor);
        callNode = wrapCallWithLiteralBlock(argumentsAndBlock, callNode);

        return assignPositionAndFlags(node, callNode);
    }

    /** Return either explicit block argument or an inherited block parameter of the enclosing method */
    private RubyNode explicitOrInheritedBlock(RubyNode blockNode) {
        if (blockNode != null) {
            return blockNode;
        } else {
            return environment.findLocalVarOrNilNode(TranslatorEnvironment.METHOD_BLOCK_NAME);
        }
    }

    @Override
    public RubyNode visitSymbolNode(Nodes.SymbolNode node) {
        final var symbol = translateSymbol(node);

        final RubyNode rubyNode = new ObjectLiteralNode(symbol);
        return assignPositionAndFlags(node, rubyNode);
    }

    public RubySymbol translateSymbol(Nodes.SymbolNode node) {
        final RubyEncoding encoding;

        if (node.isForcedUtf8Encoding()) {
            encoding = Encodings.UTF_8;
        } else if (node.isForcedUsAsciiEncoding()) {
            encoding = Encodings.US_ASCII;
        } else if (node.isForcedBinaryEncoding()) {
            encoding = Encodings.BINARY;
        } else {
            encoding = sourceEncoding;
        }

        var tstring = TStringUtils.fromByteArray(node.unescaped, encoding);
        return language.getSymbol(tstring, encoding);
    }

    @Override
    public RubyNode visitTrueNode(Nodes.TrueNode node) {
        final RubyNode rubyNode = new BooleanLiteralNode(true);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitUndefNode(Nodes.UndefNode node) {
        final RubyNode[] names = translate(node.names);
        final RubyNode rubyNode = new ModuleNodes.UndefNode(names);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitUnlessNode(Nodes.UnlessNode node) {
        final RubyNode conditionNode = node.predicate.accept(this);
        final RubyNode thenNode = node.statements == null ? null : node.statements.accept(this);
        final RubyNode elseNode = node.else_clause == null ? null : node.else_clause.accept(this);
        final RubyNode rubyNode;

        if (thenNode != null && elseNode != null) {
            rubyNode = IfElseNodeGen.create(conditionNode, elseNode, thenNode);
            return assignPositionAndFlags(node, rubyNode);
        } else if (thenNode != null) {
            rubyNode = UnlessNodeGen.create(conditionNode, thenNode);
            return assignPositionAndFlags(node, rubyNode);
        } else if (elseNode != null) {
            rubyNode = IfNodeGen.create(conditionNode, elseNode);
            return assignPositionAndFlags(node, rubyNode);
        } else {
            // unless (condition)
            // end
            rubyNode = sequence(node, conditionNode, new NilLiteralNode());
            return rubyNode;
        }
    }

    @Override
    public RubyNode visitUntilNode(Nodes.UntilNode node) {
        final RubyNode rubyNode = translateWhileNode(node, node.predicate, node.statements, true,
                !node.isBeginModifier());
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitWhenNode(Nodes.WhenNode node) {
        throw CompilerDirectives.shouldNotReachHere("handled in visitCaseNode");
    }

    @Override
    public RubyNode visitWhileNode(Nodes.WhileNode node) {
        final RubyNode rubyNode = translateWhileNode(node, node.predicate, node.statements, false,
                !node.isBeginModifier());
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitXStringNode(Nodes.XStringNode node) {
        // replace `` literal with a Kernel#` method call

        // convert EncodingFlags to StringFlags
        int flags = Nodes.StringFlags.FROZEN; // it's always frozen

        if (node.isForcedBinaryEncoding()) {
            flags |= Nodes.StringFlags.FORCED_BINARY_ENCODING;
        }

        if (node.isForcedUtf8Encoding()) {
            flags |= Nodes.StringFlags.FORCED_UTF8_ENCODING;
        }

        var stringNode = new Nodes.StringNode(node.startOffset, node.length, (short) flags, node.unescaped);
        final RubyNode string = stringNode.accept(this);

        final RubyNode rubyNode = createCallNode(new SelfNode(), "`", string);
        return assignPositionAndFlags(node, rubyNode);
    }

    @Override
    public RubyNode visitYieldNode(Nodes.YieldNode node) {
        // The yield operator is invalid outside a method body.
        // The parser doesn't handle it on its own so do it manually.
        if (isInvalidYield()) {
            final RubyContext context = RubyLanguage.getCurrentContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxError(
                            "Invalid yield",
                            currentNode,
                            getSourceSection(node)));
        }

        var argumentsAndBlock = translateArgumentsAndBlock(node.arguments, null, "<yield>");

        RubyNode readBlock = environment.findLocalVarOrNilNode(TranslatorEnvironment.METHOD_BLOCK_NAME);

        var rubyNode = new YieldExpressionNode(
                argumentsAndBlock.isSplatted,
                argumentsAndBlock.argumentsDescriptor,
                argumentsAndBlock.arguments,
                readBlock);

        return assignPositionAndFlags(node, rubyNode);
    }

    /** Declare variable in the nearest non-block outer lexical scope - either method, class or top-level */
    protected FrameSlotAndDepth createFlipFlopState() {
        final var target = environment.getSurroundingMethodOrEvalEnvironment();
        final int frameSlot = target.declareLocalTemp("flipflop");
        target.getFlipFlopStates().add(frameSlot);

        // Relative distance between environments where the local variable is used and is declared.
        // In case of eval the target environment is not a method/module/top-level and has its own non-zero depth.
        int depth = environment.getBlockDepth() - target.getBlockDepth();

        return new FrameSlotAndDepth(frameSlot, depth);
    }

    /** Translate a list of nodes, e.g. break/return operands, into an array producing node. It returns ArrayLiteralNode
     * subclass in the simplest case (when there is no splat operator) or combination of
     * ArrayConcatNode/ArrayLiteralNode/SplatCastNodeGen nodes to "join" destructured by splat operator array with other
     * operands. */
    private RubyNode translateExpressionsList(Nodes.Node[] nodes) {
        assert nodes != null;

        if (nodes.length == 0) {
            return ArrayLiteralNode.create(language, RubyNode.EMPTY_ARRAY);
        }

        boolean containSplatOperator = containYARPSplatNode(nodes);

        // fast path (no SplatNode)

        if (!containSplatOperator) {
            RubyNode[] rubyNodes = translate(nodes);
            return ArrayLiteralNode.create(language, rubyNodes);
        }

        // generic path

        ArrayList<RubyNode> arraysToConcat = new ArrayList<>();
        ArrayList<RubyNode> current = new ArrayList<>();

        // group nodes before/after/between splat operators into array literals and concat them, e.g.
        //   a, b, *c, d
        // is translated into
        //   ArrayConcatNode(ArrayLiteralNode(a, b), "splat-operator-node", ArrayLiteralNode(d))
        for (Nodes.Node node : nodes) {
            if (node instanceof Nodes.SplatNode) {
                if (!current.isEmpty()) {
                    arraysToConcat.add(ArrayLiteralNode.create(language, current.toArray(RubyNode.EMPTY_ARRAY)));
                    current = new ArrayList<>();
                }
                arraysToConcat.add(node.accept(this));
            } else {
                current.add(node.accept(this));
            }
        }

        if (!current.isEmpty()) {
            arraysToConcat.add(ArrayLiteralNode.create(language, current.toArray(RubyNode.EMPTY_ARRAY)));
        }

        final RubyNode rubyNode;

        if (arraysToConcat.size() == 1) {
            rubyNode = arraysToConcat.get(0);
        } else {
            rubyNode = new ArrayConcatNode(arraysToConcat.toArray(RubyNode.EMPTY_ARRAY));
        }

        return rubyNode;
    }

    private String modulePathAndMethodName(String methodName, Nodes.Node receiver) {
        boolean onSingleton = receiver != null;

        String modulePath = environment.modulePath;
        if (modulePath == null) {
            if (onSingleton) {
                if (environment.isTopLevelObjectScope() && receiver instanceof Nodes.SelfNode) { // def self.foo at top-level
                    modulePath = "main";
                } else if (environment.isTopLevelObjectScope() &&
                        receiver instanceof Nodes.ConstantReadNode constantReadNode) { // def String.foo at top-level
                    modulePath = constantReadNode.name;
                } else {
                    return methodName; // method of an unknown singleton class
                }
            } else {
                if (environment.isTopLevelObjectScope()) {
                    modulePath = "Object";
                } else {
                    return methodName; // instance method of an unknown module
                }
            }
        } else {
            if (onSingleton && !(receiver instanceof Nodes.SelfNode)) {
                // It's not defined in modulePath but potentially some other module
                return methodName;
            }
        }

        if (modulePath.endsWith("::<singleton class>") && !onSingleton) {
            modulePath = modulePath.substring(0, modulePath.length() - "::<singleton class>".length());
            onSingleton = true;
        }

        return SharedMethodInfo.modulePathAndMethodName(modulePath, methodName, onSingleton);
    }

    private RubyNode getLexicalScopeNode(String kind, Nodes.Node yarpNode) {
        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER.info(() -> kind + " at " + language.fileLine(getSourceSection(yarpNode)));
            }
            return new GetDynamicLexicalScopeNode();
        } else {
            return new ObjectLiteralNode(environment.getStaticLexicalScope());
        }
    }

    /** Translates a module or class body to create a new module or class or "reopen" an existing one. */
    private RubyNode openModule(Node moduleNode, RubyNode defineOrGetNode, String modulePath, String moduleBasename,
            String[] locals, Node bodyNode, OpenModule type, boolean dynamicConstantLookup) {
        final String methodName = type.format(moduleBasename);

        final LexicalScope newLexicalScope = dynamicConstantLookup
                ? null
                : new LexicalScope(environment.getStaticLexicalScope());

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                getSourceSection(moduleNode),
                newLexicalScope,
                Arity.NO_ARGUMENTS,
                methodName,
                0,
                methodName,
                null,
                null);

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                environment,
                parseEnvironment,
                ReturnID.MODULE_BODY,
                true,
                true,
                sharedMethodInfo,
                methodName,
                0,
                null,
                null,
                modulePath);

        // declare local variables defined in a module or class body
        for (String name : locals) {
            newEnvironment.declareVar(name);
        }

        final YARPTranslator moduleTranslator = new YARPTranslator(newEnvironment, rubyWarnings);
        final ModuleBodyDefinition definition = moduleTranslator.compileClassNode(moduleNode, bodyNode);
        return new RunModuleDefinitionNode(definition, defineOrGetNode);
    }

    /** Translates module and class nodes.
     * <p>
     * In Ruby, a module or class definition is somewhat like a method. It has a local scope and a value for self, which
     * is the module or class object that is being defined. Therefore for a module or class definition we translate into
     * a special method. We run that method with self set to be the newly allocated module or class.
     * </p>
     */
    private ModuleBodyDefinition compileClassNode(Nodes.Node moduleNode, Nodes.Node bodyNode) {
        RubyNode body = translateNodeOrNil(bodyNode);
        body = new InsideModuleDefinitionNode(body);
        assignPositionOnly(moduleNode, body); // source location is needed to trigger :class TracePoint event

        if (environment.getFlipFlopStates().size() > 0) {
            body = sequence(initFlipFlopStates(environment), body);
        }

        final RubyNode writeSelfNode = loadSelf(language);
        body = sequence(writeSelfNode, body);

        final RubyRootNode rootNode = new RubyRootNode(
                language,
                getSourceSection(moduleNode),
                environment.computeFrameDescriptor(),
                environment.getSharedMethodInfo(),
                body,
                Split.NEVER,
                environment.getReturnID());

        return new ModuleBodyDefinition(
                environment.getSharedMethodInfo().getOriginalName(),
                environment.getSharedMethodInfo(),
                rootNode.getCallTarget(),
                environment.getStaticLexicalScopeOrNull());
    }

    private String appendConstantPathToModulePath(Nodes.Node node) {
        if (node instanceof Nodes.ConstantReadNode constantReadNode) {
            return TranslatorEnvironment.composeModulePath(environment.modulePath, constantReadNode.name);
        } else if (node instanceof Nodes.ConstantPathNode pathNode) {
            if (pathNode.parent == null) { // ::A
                return pathNode.name;
            }

            ArrayDeque<String> parts = new ArrayDeque<>();
            parts.addFirst(pathNode.name);
            Nodes.Node parent = pathNode.parent;
            boolean isAbsolute = false;

            while (true) {
                if (parent == null) {
                    isAbsolute = true;
                    break;
                }

                if (parent instanceof ConstantReadNode readNode) {
                    parts.addFirst(readNode.name);
                    break;
                }

                if (parent instanceof Nodes.ConstantPathNode parentPath) {
                    parts.addFirst(parentPath.name);
                    parent = parentPath.parent;
                } else {
                    return null; // not representable as a module path, e.g. `module expr::Foo`
                }
            }

            String constantPath = String.join("::", parts);
            if (isAbsolute) {
                return constantPath;
            } else {
                return TranslatorEnvironment.composeModulePath(environment.modulePath, constantPath);
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere(node.getClass().getName());
        }
    }

    /** Translates a prefix of a constant's path (e.g. A::B for A::B::C) */
    private RubyNode getParentLexicalScopeForConstant(Nodes.Node node) {
        final RubyNode rubyNode;

        if (node instanceof Nodes.ConstantReadNode) { // use current lexical scope
            // bare class declaration (e.g. class Foo/module Foo)
            rubyNode = getLexicalScopeModuleNode("dynamic constant lookup", node);
        } else if (node instanceof Nodes.ConstantPathNode pathNode) {
            if (pathNode.parent != null) {
                // A::B
                rubyNode = pathNode.parent.accept(this);
            } else {
                // ::A
                rubyNode = new ObjectClassLiteralNode();
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        return rubyNode;
    }

    private RubyNode getLexicalScopeModuleNode(String kind, Nodes.Node node) {
        if (environment.isDynamicConstantLookup()) {
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER.info(() -> kind + " at " + language.fileLine(getSourceSection(node)));
            }
            return new DynamicLexicalScopeNode();
        } else {
            return new LexicalScopeNode(environment.getStaticLexicalScope());
        }
    }

    protected static RubyNode initFlipFlopStates(TranslatorEnvironment environment) {
        final RubyNode[] initNodes = createArray(environment.getFlipFlopStates().size());

        for (int n = 0; n < initNodes.length; n++) {
            initNodes[n] = new InitFlipFlopSlotNode(environment.getFlipFlopStates().get(n));
        }

        return sequence(initNodes);
    }

    public static RubyNode loadSelf(RubyLanguage language) {
        return new WriteLocalVariableNode(SelfNode.SELF_INDEX, profileArgument(language, new ReadSelfNode()));
    }

    public static RubyNode profileArgument(RubyLanguage language, RubyNode argumentNode) {
        RubyNode node = argumentNode;

        if (language.options.PROFILE_ARGUMENTS) {
            node = ProfileArgumentNodeGen.create(node);
        }

        if (language.options.CHAOS_DATA) {
            node = ChaosNode.create(node);
        }

        return node;
    }

    private boolean shouldUseDynamicConstantLookupForModuleBody(Nodes.Node node) {
        if (environment.isDynamicConstantLookup()) {
            return true;
        }

        if (environment.isModuleBody()) { // A new class/module under another class/module
            return false;
        } else if (environment.isTopLevelScope()) {
            // At the top-level of a file, executing the module/class body only once
            return false;
        } else {
            // Switch to dynamic constant lookup
            if (language.options.LOG_DYNAMIC_CONSTANT_LOOKUP) {
                RubyLanguage.LOGGER
                        .info(() -> "start dynamic constant lookup at " + language.fileLine(getSourceSection(node)));
            }
            return true;
        }
    }

    /** Returns whether the yield operator is used in improper place. Yield is invalid outside a method body. */
    private boolean isInvalidYield() {
        return environment.getSurroundingMethodEnvironment().isModuleBody();
    }

    /** Translate operands of break/next/... operators.
     * 
     * Single operand is translated into a single value. Multiple operands - into collection.
     *
     * Translated arguments are represented by a single node - either by a single argument value, ArrayLiteralNode or
     * combination of ArrayConcatNode/ArrayAppendOneNodeGen nodes (if there is splat operator - `break 1, *a, 2`) */
    private RubyNode translateControlFlowArguments(Nodes.ArgumentsNode node) {
        if (node == null) {
            return new NilLiteralNode();
        }

        final Nodes.Node[] values = node.arguments;

        if (values.length == 1) {
            return values[0].accept(this);
        }

        final RubyNode rubyNode = translateExpressionsList(values);

        return assignPositionAndFlags(node, rubyNode);
    }

    private RubyNode translateRescueException(Nodes.Node target) {
        final RubyNode rubyNode = target.accept(this);
        final AssignableNode assignableNode = (AssignableNode) rubyNode;
        return new AssignRescueVariableNode(assignableNode);
    }

    private RubyNode translateWhileNode(Nodes.Node node, Nodes.Node predicate, Nodes.StatementsNode statements,
            boolean conditionInversed, boolean evaluateConditionBeforeBody) {
        RubyNode condition = predicate.accept(this);
        if (conditionInversed) {
            condition = NotNodeGen.create(condition);
        }

        final RubyNode body;
        final BreakID whileBreakID = parseEnvironment.allocateBreakID();

        final boolean oldTranslatingWhile = translatingWhile;
        translatingWhile = true;
        final BreakID oldBreakID = environment.getBreakID();
        environment.setBreakIDForWhile(whileBreakID);
        frameOnStackMarkerSlotStack.push(NO_FRAME_ON_STACK_MARKER);

        try {
            body = translateNodeOrNil(statements);
        } finally {
            frameOnStackMarkerSlotStack.pop();
            environment.setBreakIDForWhile(oldBreakID);
            translatingWhile = oldTranslatingWhile;
        }

        final RubyNode loop;

        // in case of `begin ... end while ()`
        // the begin/end block is executed before condition
        if (evaluateConditionBeforeBody) {
            loop = new WhileNode(WhileNodeFactory.WhileRepeatingNodeGen.create(condition, body));
        } else {
            loop = new WhileNode(WhileNodeFactory.DoWhileRepeatingNodeGen.create(condition, body));
        }

        final RubyNode rubyNode = new CatchBreakNode(whileBreakID, loop, true);
        return rubyNode;
    }

    /** A node is side-effect-free if it cannot access $! */
    protected boolean isSideEffectFreeRescueExpression(Nodes.Node node) {
        return node instanceof Nodes.InstanceVariableReadNode ||
                node instanceof Nodes.LocalVariableReadNode ||
                node instanceof Nodes.ClassVariableReadNode ||
                node instanceof Nodes.SourceFileNode ||
                node instanceof Nodes.StringNode ||
                node instanceof Nodes.SymbolNode ||
                node instanceof Nodes.IntegerNode ||
                node instanceof Nodes.FloatNode ||
                node instanceof Nodes.ImaginaryNode ||
                node instanceof Nodes.RationalNode ||
                node instanceof Nodes.SelfNode ||
                node instanceof Nodes.TrueNode ||
                node instanceof Nodes.FalseNode ||
                node instanceof Nodes.NilNode;
    }

    private boolean containYARPSplatNode(Nodes.Node[] nodes) {
        for (var n : nodes) {
            if (n instanceof Nodes.SplatNode) {
                return true;
            }
        }

        return false;
    }

    private ArgumentDescriptor[] parametersNodeToArgumentDescriptors(Nodes.ParametersNode parametersNode) {
        if (parametersNode == ZERO_PARAMETERS_NODE) {
            return ArgumentDescriptor.EMPTY_ARRAY;
        }

        ArrayList<ArgumentDescriptor> descriptors = new ArrayList<>();

        for (var node : parametersNode.requireds) {
            if (node instanceof Nodes.MultiTargetNode) {
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonreq));
            } else {
                String name = ((Nodes.RequiredParameterNode) node).name;
                var descriptor = new ArgumentDescriptor(ArgumentType.req, name);
                descriptors.add(descriptor);
            }
        }

        for (var node : parametersNode.optionals) {
            var descriptor = new ArgumentDescriptor(ArgumentType.opt, node.name);
            descriptors.add(descriptor);
        }

        // Proc#parameters doesn't report anonymous rest parameter for implicit rest parameter (|a,|).
        // So just ignore Nodes.ImplicitRestNode (that is only available in blocks).
        if (parametersNode.rest instanceof Nodes.RestParameterNode restParameterNode) {
            if (restParameterNode.name == null) {
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonrest));
            } else {
                var descriptor = new ArgumentDescriptor(ArgumentType.rest, restParameterNode.name);
                descriptors.add(descriptor);
            }
        }

        for (var node : parametersNode.posts) {
            if (node instanceof Nodes.MultiTargetNode) {
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonreq));
            } else {
                String name = ((Nodes.RequiredParameterNode) node).name;
                var descriptor = new ArgumentDescriptor(ArgumentType.req, name);
                descriptors.add(descriptor);
            }
        }

        for (var node : parametersNode.keywords) {
            final ArgumentDescriptor descriptor;

            if (node instanceof Nodes.RequiredKeywordParameterNode required) {
                descriptor = new ArgumentDescriptor(ArgumentType.keyreq, required.name);
            } else if (node instanceof Nodes.OptionalKeywordParameterNode optional) {
                descriptor = new ArgumentDescriptor(ArgumentType.key, optional.name);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }

            descriptors.add(descriptor);
        }

        if (parametersNode.keyword_rest != null) {
            if (parametersNode.keyword_rest instanceof Nodes.KeywordRestParameterNode) {
                final var keywordRestParameterNode = (Nodes.KeywordRestParameterNode) parametersNode.keyword_rest;

                if (keywordRestParameterNode.name == null) {
                    descriptors.add(new ArgumentDescriptor(ArgumentType.anonkeyrest, DEFAULT_KEYWORD_REST_NAME));
                } else {
                    descriptors.add(new ArgumentDescriptor(ArgumentType.keyrest, keywordRestParameterNode.name));
                }
            } else if (parametersNode.keyword_rest instanceof Nodes.ForwardingParameterNode) {
                // ... => *, **, &
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonrest, FORWARDED_REST_NAME));
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonkeyrest, FORWARDED_KEYWORD_REST_NAME));
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonblock, FORWARDED_BLOCK_NAME));
            } else if (parametersNode.keyword_rest instanceof Nodes.NoKeywordsParameterNode) {
                final var descriptor = new ArgumentDescriptor(ArgumentType.nokey);
                descriptors.add(descriptor);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        if (parametersNode.block != null) {
            if (parametersNode.block.name == null) {
                // def a(&) ... end
                descriptors.add(new ArgumentDescriptor(ArgumentType.anonblock, DEFAULT_BLOCK_NAME));
            } else {
                var descriptor = new ArgumentDescriptor(ArgumentType.block, parametersNode.block.name);
                descriptors.add(descriptor);
            }
        }

        return descriptors.toArray(ArgumentDescriptor.EMPTY_ARRAY);
    }

    private Arity createArity(Nodes.ParametersNode parametersNode) {
        if (parametersNode == ZERO_PARAMETERS_NODE) {
            // Arity.NO_ARGUMENTS would be tempting here but that would affect method identity
            return new Arity(0, 0, false);
        }

        final String[] keywordArguments;
        final int requiredKeywordArgumentsCount;

        if (parametersNode.keywords.length > 0) {
            final List<String> requiredKeywords = new ArrayList<>();
            final List<String> optionalKeywords = new ArrayList<>();

            for (var node : parametersNode.keywords) {
                if (node instanceof Nodes.RequiredKeywordParameterNode required) {
                    requiredKeywords.add(required.name);
                } else if (node instanceof Nodes.OptionalKeywordParameterNode optional) {
                    optionalKeywords.add(optional.name);
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            final List<String> keywords = new ArrayList<>(requiredKeywords);
            keywords.addAll(optionalKeywords);

            keywordArguments = keywords.toArray(StringUtils.EMPTY_STRING_ARRAY);
            requiredKeywordArgumentsCount = requiredKeywords.size();
        } else {
            keywordArguments = Arity.NO_KEYWORDS;
            requiredKeywordArgumentsCount = 0;
        }

        final boolean hasRest;
        if (parametersNode.keyword_rest instanceof Nodes.ForwardingParameterNode) {
            hasRest = true;
        } else {
            hasRest = parametersNode.rest != null;
        }

        // blocks only can have implicit rest parameter (|a,|)
        final boolean isImplicitRest = parametersNode.rest instanceof Nodes.ImplicitRestNode;

        // NOTE: when ... parameter is present then YARP keeps ForwardingParameterNode in ParametersNode#keyword_rest field.
        //      So `parametersNode.keyword_rest != null` works correctly to check if there is a keyword rest argument.
        return new Arity(
                parametersNode.requireds.length,
                parametersNode.optionals.length,
                hasRest,
                isImplicitRest,
                parametersNode.posts.length,
                keywordArguments,
                requiredKeywordArgumentsCount,
                parametersNode.keyword_rest != null);
    }

}
