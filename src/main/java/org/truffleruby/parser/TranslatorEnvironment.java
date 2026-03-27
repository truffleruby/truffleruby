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
package org.truffleruby.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.Assumption;
import org.ruby_lang.prism.Nodes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.graalvm.collections.EconomicMap;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.locals.LocalVariableType;
import org.truffleruby.language.locals.ReadLocalNode;
import org.truffleruby.language.locals.ReadLocalVariableNode;
import org.truffleruby.language.locals.WriteLocalNode;
import org.truffleruby.language.locals.WriteLocalVariableNode;
import org.truffleruby.language.methods.SharedMethodInfo;

import com.oracle.truffle.api.frame.FrameDescriptor;
import org.truffleruby.language.objects.SelfNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

public final class TranslatorEnvironment {

    /* Names of hidden local variables.
     * 
     * For each parameter in methods and blocks a local variable is declared to keep actual argument value. Use the
     * following names for parameters that don't have explicit names - anonymous rest, keyword rest and block.
     * 
     * Store values of anonymous parameters to forward them either implicitly to a super method call or explicitly to a
     * method call with *, **, & or "...". */

    /** local variable to access a block argument */
    public static final String METHOD_BLOCK_NAME = "%method_block_arg";

    /** local variable name for * parameter */
    static final String DEFAULT_REST_NAME = "%unnamed_rest";
    /** local variable name for ** parameter */
    static final String DEFAULT_KEYWORD_REST_NAME = "%kwrest";
    /** local variable name for & parameter */
    public static final String DEFAULT_BLOCK_NAME = "%unnamed_block";

    /** local variable name for * parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_REST_NAME = "%forward_rest";
    /** local variable name for ** parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_KEYWORD_REST_NAME = "%forward_kwrest";
    /** local variable name for & parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_BLOCK_NAME = "%forward_block";

    /** This should be 0, but because Prism does not increase the depth inside the `for` loop body and `END` body we
     * need to not add depthOffset to the depth for temporary variables which must always be read from the current
     * frame. So we work around by using a negative depth to handle this case. */
    public static final int CURRENT_FRAME_DEPTH = -1;

    private final TranslatorEnvironment parent;
    /** The depth reported by Prism inside the `for` loop body and `END` body do not match TruffleRuby and CRuby:
     * https://github.com/ruby/prism/issues/4010. So we compensate by having a depthOffset of 1 for those cases. 0 in
     * all other cases. */
    private final int depthOffset;
    private final ParseEnvironment parseEnvironment;

    private EconomicMap<Object, Integer> nameToIndex = EconomicMap.create();
    private FrameDescriptor.Builder frameDescriptorBuilder;
    private final FrameDescriptorInfo descriptorInfo;
    private List<FrameDescriptorInfo> childrenDescriptorInfos = null;
    private FrameDescriptor frameDescriptor;

    private List<Integer> flipFlopStates = null;

    private final ReturnID returnID;
    private final int blockDepth;
    private BreakID breakID;

    private final SharedMethodInfo sharedMethodInfo;

    public final String modulePath;
    public final String methodName;
    /** Only set for literal blocks passed to a method, e.g. "foo" for `foo { ... }` */
    public String literalBlockPassedToMethod = null;
    /** Only set for def methods */
    public Nodes.ParametersNode parametersNode = null;

    public TranslatorEnvironment(
            TranslatorEnvironment parent,
            int depthOffset,
            ParseEnvironment parseEnvironment,
            ReturnID returnID,
            SharedMethodInfo sharedMethodInfo,
            String methodName,
            int blockDepth,
            BreakID breakID,
            FrameDescriptor descriptor,
            String modulePath) {
        assert blockDepth == sharedMethodInfo.getBlockDepth();

        this.parent = parent;
        this.depthOffset = depthOffset;
        this.parseEnvironment = parseEnvironment;
        this.returnID = returnID;
        this.sharedMethodInfo = sharedMethodInfo;
        this.methodName = methodName;
        this.blockDepth = blockDepth;
        this.breakID = breakID;
        this.modulePath = modulePath;

        if (descriptor == null) {
            this.childrenDescriptorInfos = new ArrayList<>();

            if (blockDepth > 0) { // block
                if (parent.frameDescriptor != null) {
                    descriptorInfo = new FrameDescriptorInfo(parent.frameDescriptor, sharedMethodInfo);
                } else {
                    descriptorInfo = new FrameDescriptorInfo(parent.descriptorInfo, sharedMethodInfo);
                    parent.childrenDescriptorInfos.add(descriptorInfo);
                }
                this.frameDescriptorBuilder = newFrameDescriptorBuilderForBlock(descriptorInfo);
            } else { // method
                this.descriptorInfo = new FrameDescriptorInfo(createSpecialVariableAssumption(), sharedMethodInfo);
                this.frameDescriptorBuilder = newFrameDescriptorBuilderForMethod(descriptorInfo);
            }
        } else {
            this.frameDescriptor = descriptor;
            this.descriptorInfo = FrameDescriptorInfo.of(descriptor);
            this.childrenDescriptorInfos = null;

            assert descriptor.getNumberOfAuxiliarySlots() == 0;
            int slots = descriptor.getNumberOfSlots();
            for (int slot = 0; slot < slots; slot++) {
                Object identifier = descriptor.getSlotName(slot);
                if (identifier instanceof String) {
                    nameToIndex.put(identifier, slot);
                }
            }
        }
    }

    public static String composeModulePath(String modulePath, String name) {
        return modulePath != null ? modulePath + "::" + name : name;
    }

    public boolean isDynamicConstantLookup() {
        return sharedMethodInfo.getStaticLexicalScopeOrNull() == null;
    }

    public LexicalScope getStaticLexicalScope() {
        return sharedMethodInfo.getStaticLexicalScope();
    }

    public LexicalScope getStaticLexicalScopeOrNull() {
        return sharedMethodInfo.getStaticLexicalScopeOrNull();
    }

    public TranslatorEnvironment getParent() {
        return parent;
    }

    /** Top-level scope, i.e. from main script/load/require. The lexical scope might not be Object in the case of
     * {@code load(file, wrap=true)}. */
    public boolean isTopLevelScope() {
        return parent == null && isModuleBody();
    }

    /** Top-level scope and the lexical scope is Object, and self is the "main" object */
    public boolean isTopLevelObjectScope() {
        return isTopLevelScope() && modulePath == null;
    }

    // region frame descriptor
    public static FrameDescriptor.Builder newFrameDescriptorBuilderForBlock(FrameDescriptorInfo descriptorInfo) {
        var builder = FrameDescriptor.newBuilder().defaultValue(Nil.INSTANCE);
        builder.info(Objects.requireNonNull(descriptorInfo));

        int selfIndex = builder.addSlot(FrameSlotKind.Illegal, SelfNode.SELF_IDENTIFIER, null);
        if (selfIndex != SelfNode.SELF_INDEX) {
            throw CompilerDirectives.shouldNotReachHere("(self) should be at index 0");
        }

        return builder;
    }

    private static Assumption createSpecialVariableAssumption() {
        return Assumption.create(SpecialVariableStorage.ASSUMPTION_NAME);
    }

    private static FrameDescriptor.Builder newFrameDescriptorBuilderForMethod(FrameDescriptorInfo descriptorInfo) {
        var builder = FrameDescriptor.newBuilder().defaultValue(Nil.INSTANCE).info(descriptorInfo);

        int selfIndex = builder.addSlot(FrameSlotKind.Illegal, SelfNode.SELF_IDENTIFIER, null);
        if (selfIndex != SelfNode.SELF_INDEX) {
            throw CompilerDirectives.shouldNotReachHere("(self) should be at index 0");
        }

        int svarsSlot = builder.addSlot(FrameSlotKind.Illegal, SpecialVariableStorage.SLOT_NAME, null);
        if (svarsSlot != SpecialVariableStorage.SLOT_INDEX) {
            throw CompilerDirectives.shouldNotReachHere("svars should be at index 1");
        }

        return builder;
    }

    public static FrameDescriptor.Builder newFrameDescriptorBuilderForMethod(SharedMethodInfo sharedMethodInfo) {
        var descriptorInfo = new FrameDescriptorInfo(createSpecialVariableAssumption(), sharedMethodInfo);
        return newFrameDescriptorBuilderForMethod(descriptorInfo);
    }

    public int declareVar(Object name) {
        assert name != null && !(name instanceof String && ((String) name).isEmpty());

        int index = addSlot(name);
        Object prev = nameToIndex.putIfAbsent(name, index);
        if (prev != null) {
            throw CompilerDirectives.shouldNotReachHere("Expected variable " + name + " to not already be declared");
        }
        return index;
    }

    private int addSlot(Object name) {
        return frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, name, null);
    }

    public int declareLocalTemp(String indicator) {
        final String name = parseEnvironment.allocateLocalTemp(indicator);
        // TODO: might not need to add to nameToIndex for temp vars
        return declareVar(name);
    }

    private int findFrameSlotOrMinusOne(Object name) {
        assert name != null;
        return nameToIndex.get(name, -1);
    }

    public int findFrameSlot(Object name) {
        assert name != null;
        Integer index = nameToIndex.get(name);
        if (index == null) {
            throw CompilerDirectives.shouldNotReachHere("Could not find slot " + name);
        }
        return index;
    }

    public ReadLocalVariableNode readNode(int slot) {
        return new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, slot);
    }

    public WriteLocalVariableNode writeNode(int slot, RubyNode valueNode) {
        return new WriteLocalVariableNode(slot, valueNode);
    }

    public ReadLocalVariableNode readNode(int slot, Nodes.Node yarpNode) {
        var node = new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, slot);
        node.unsafeSetSourceSection(yarpNode.startOffset, yarpNode.length);
        return node;
    }

    public ReadLocalNode readNode(String name, int prismNodeDepth) {
        // Copy of the actualDepth logic because we need both scope and actualDepth
        int remainingDepth = prismNodeDepth + depthOffset;
        int actualDepth = 0;
        var scope = this;
        while (remainingDepth > 0) {
            scope = scope.parent;
            remainingDepth = remainingDepth - 1 + scope.depthOffset;
            actualDepth++;
        }

        int slot = scope.findFrameSlot(name);
        return ReadLocalNode.create(slot, actualDepth);
    }

    public WriteLocalNode writeNode(String name, int prismNodeDepth, RubyNode valueNode) {
        // Copy of the actualDepth logic because we need both scope and actualDepth
        int remainingDepth = prismNodeDepth + depthOffset;
        int actualDepth = 0;
        var scope = this;
        while (remainingDepth > 0) {
            scope = scope.parent;
            remainingDepth = remainingDepth - 1 + scope.depthOffset;
            actualDepth++;
        }

        int slot = scope.findFrameSlot(name);
        return WriteLocalNode.create(slot, actualDepth, valueNode);
    }

    public ReadLocalNode readFromMethodFrameNode(String name) {
        int depth = getBlockDepth();
        int slot = getSurroundingMethodEnvironment().findFrameSlot(name);
        return ReadLocalNode.create(slot, depth);
    }

    public RubyNode readFromMethodFrameNodeOrNil(String name) {
        int depth = getBlockDepth();
        int slot = getSurroundingMethodEnvironment().findFrameSlotOrMinusOne(name);
        return slot != -1 ? ReadLocalNode.create(slot, depth) : new NilLiteralNode();
    }

    public FrameDescriptor computeFrameDescriptor() {
        if (frameDescriptor != null) {
            return frameDescriptor;
        }

        frameDescriptor = frameDescriptorBuilder.build();

        for (var childDescriptorInfo : childrenDescriptorInfos) {
            childDescriptorInfo.setParentDescriptor(frameDescriptor);
        }
        childrenDescriptorInfos = null;

        frameDescriptorBuilder = null;
        nameToIndex = null;
        return frameDescriptor;
    }
    // endregion

    public ReturnID getReturnID() {
        return returnID;
    }

    public ParseEnvironment getParseEnvironment() {
        return parseEnvironment;
    }

    /** A way to check current scope is a module/class. If in a block, isModuleBody is always false. */
    public boolean isModuleBody() {
        return sharedMethodInfo.isModuleBody();
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }

    public boolean hasFlipFlopStates() {
        return flipFlopStates != null && !flipFlopStates.isEmpty();
    }

    public List<Integer> getFlipFlopStates() {
        if (flipFlopStates == null) {
            flipFlopStates = new ArrayList<>();
        }
        return flipFlopStates;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isBlock() {
        return blockDepth > 0;
    }

    public int getBlockDepth() {
        return blockDepth;
    }

    public BreakID getBreakID() {
        return breakID;
    }

    public void setBreakIDForWhile(BreakID breakID) {
        this.breakID = breakID;
    }

    public TranslatorEnvironment getSurroundingMethodEnvironment() {
        TranslatorEnvironment methodParent = this;
        while (methodParent.isBlock()) {
            methodParent = methodParent.getParent();
        }
        return methodParent;
    }

    /** Return either outer method/module/top level environment or in case of eval("...") the outermost environment of
     * the parsed (by eval) code */
    public TranslatorEnvironment getSurroundingMethodOrEvalEnvironment() {
        TranslatorEnvironment environment = this;

        // eval's parsing environment still has frameDescriptor not initialized,
        // but all the outer scopes are related to already parsed code and have frameDescriptor != null.
        while (environment.isBlock() && environment.getParent().frameDescriptor == null) {
            environment = environment.getParent();
        }
        return environment;
    }

}
