/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.Assumption;
import org.prism.Nodes;
import org.truffleruby.Layouts;
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
import org.truffleruby.language.locals.ReadDeclarationVariableNode;
import org.truffleruby.language.locals.ReadLocalNode;
import org.truffleruby.language.locals.ReadLocalVariableNode;
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
    public static final String METHOD_BLOCK_NAME = Layouts.TEMP_PREFIX + "method_block_arg";

    /** local variable name for * parameter */
    static final String DEFAULT_REST_NAME = Layouts.TEMP_PREFIX + "unnamed_rest";
    /** local variable name for ** parameter */
    static final String DEFAULT_KEYWORD_REST_NAME = Layouts.TEMP_PREFIX + "kwrest";
    /** local variable name for & parameter */
    public static final String DEFAULT_BLOCK_NAME = Layouts.TEMP_PREFIX + "unnamed_block";

    /** local variable name for * parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_REST_NAME = Layouts.TEMP_PREFIX + "forward_rest";
    /** local variable name for ** parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_KEYWORD_REST_NAME = Layouts.TEMP_PREFIX + "forward_kwrest";
    /** local variable name for & parameter caused by desugaring ... parameter (forward-everything) */
    public static final String FORWARDED_BLOCK_NAME = Layouts.TEMP_PREFIX + "forward_block";

    private final ParseEnvironment parseEnvironment;

    private EconomicMap<Object, Integer> nameToIndex = EconomicMap.create();
    private FrameDescriptor.Builder frameDescriptorBuilder;
    private FrameDescriptor frameDescriptor;
    /** The descriptor info is shared for all blocks at the same level (i.e., for TranslatorEnvironment direct
     * children), in order to save footprint. It is therefore created in the parent TranslatorEnvironment of those
     * blocks using that descriptor info. */
    private final BlockDescriptorInfo descriptorInfoForChildren;

    private final List<Integer> flipFlopStates = new ArrayList<>();

    private final ReturnID returnID;
    private final int blockDepth;
    private BreakID breakID;

    private final boolean ownScopeForAssignments;
    private final boolean isModuleBody;

    private final TranslatorEnvironment parent;
    private final SharedMethodInfo sharedMethodInfo;

    public final String modulePath;
    public final String methodName;
    /** Only set for literal blocks passed to a method, e.g. "foo" for `foo { ... }` */
    public String literalBlockPassedToMethod = null;
    /** Only set for def methods */
    public Nodes.ParametersNode parametersNode = null;

    // TODO(CS): overflow? and it should be per-context, or even more local
    private static final AtomicInteger tempIndex = new AtomicInteger();

    public TranslatorEnvironment(
            TranslatorEnvironment parent,
            ParseEnvironment parseEnvironment,
            ReturnID returnID,
            boolean ownScopeForAssignments,
            boolean isModuleBody,
            SharedMethodInfo sharedMethodInfo,
            String methodName,
            int blockDepth,
            BreakID breakID,
            FrameDescriptor descriptor,
            String modulePath) {
        assert blockDepth == sharedMethodInfo.getBlockDepth();

        this.parent = parent;

        if (descriptor == null) {
            if (blockDepth > 0) {
                BlockDescriptorInfo descriptorInfo = Objects.requireNonNull(parent.descriptorInfoForChildren);
                this.frameDescriptorBuilder = newFrameDescriptorBuilderForBlock(descriptorInfo);
                this.descriptorInfoForChildren = new BlockDescriptorInfo(descriptorInfo.getSpecialVariableAssumption());
            } else {
                var specialVariableAssumption = createSpecialVariableAssumption();
                this.frameDescriptorBuilder = newFrameDescriptorBuilderForMethod(specialVariableAssumption);
                this.descriptorInfoForChildren = new BlockDescriptorInfo(specialVariableAssumption);
            }
        } else {
            this.frameDescriptor = descriptor;
            this.descriptorInfoForChildren = new BlockDescriptorInfo(descriptor);

            assert descriptor.getNumberOfAuxiliarySlots() == 0;
            int slots = descriptor.getNumberOfSlots();
            for (int slot = 0; slot < slots; slot++) {
                Object identifier = descriptor.getSlotName(slot);
                if (identifier instanceof String) {
                    nameToIndex.put(identifier, slot);
                }
            }
        }

        this.parseEnvironment = parseEnvironment;
        this.returnID = returnID;
        this.ownScopeForAssignments = ownScopeForAssignments;
        this.isModuleBody = isModuleBody;
        this.sharedMethodInfo = sharedMethodInfo;
        this.methodName = methodName;
        this.blockDepth = blockDepth;
        this.breakID = breakID;
        this.modulePath = modulePath;
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
        return parent == null && isModuleBody;
    }

    /** Top-level scope and the lexical scope is Object, and self is the "main" object */
    public boolean isTopLevelObjectScope() {
        return isTopLevelScope() && modulePath == null;
    }

    // region frame descriptor
    public static FrameDescriptor.Builder newFrameDescriptorBuilderForBlock(BlockDescriptorInfo descriptorInfo) {
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

    private static FrameDescriptor.Builder newFrameDescriptorBuilderForMethod(Assumption specialVariableAssumption) {
        var builder = FrameDescriptor.newBuilder().defaultValue(Nil.INSTANCE);
        // We need to access this Assumption from the FrameDescriptor,
        // and there is no way to get a RootNode from a FrameDescriptor, so we store it in the descriptor info.
        // We do not store it as slot info for footprint, to avoid needing an info array per FrameDescriptor.
        builder.info(specialVariableAssumption);


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

    public static FrameDescriptor.Builder newFrameDescriptorBuilderForMethod() {
        var specialVariableAssumption = createSpecialVariableAssumption();
        return newFrameDescriptorBuilderForMethod(specialVariableAssumption);
    }

    public int declareVar(Object name) {
        assert name != null && !(name instanceof String && ((String) name).isEmpty());

        Integer existingSlot = nameToIndex.get(name);
        if (existingSlot != null) {
            return existingSlot;
        } else {
            int index = addSlot(name);
            nameToIndex.put(name, index);
            return index;
        }
    }

    private int addSlot(Object name) {
        return frameDescriptorBuilder.addSlot(FrameSlotKind.Illegal, name, null);
    }

    public String allocateLocalTemp(String indicator) {
        return Layouts.TEMP_PREFIX + indicator + "_" + tempIndex.getAndIncrement();
    }

    public int declareLocalTemp(String indicator) {
        final String name = allocateLocalTemp(indicator);
        // TODO: might not need to add to nameToIndex for temp vars
        return declareVar(name);
    }

    public Integer findFrameSlotOrNull(Object name) {
        assert name != null;
        return nameToIndex.get(name);
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

    public ReadLocalVariableNode readNode(int slot, Nodes.Node yarpNode) {
        var node = new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, slot);
        node.unsafeSetSourceSection(yarpNode.startOffset, yarpNode.length);
        return node;
    }

    public RubyNode findLocalVarOrNilNode(String name) {
        RubyNode node = findLocalVarNodeOrNull(name);
        if (node == null) {
            node = new NilLiteralNode();
        }
        return node;
    }

    public ReadLocalNode findLocalVarNode(String name) {
        final ReadLocalNode node = findLocalVarNodeOrNull(name);

        if (node == null) {
            throw CompilerDirectives.shouldNotReachHere(name + " local variable should be declared");
        }

        return node;
    }

    public ReadLocalNode findLocalVarNodeOrNull(String name) {
        assert name != null;
        TranslatorEnvironment current = this;
        int level = 0;

        while (current != null) {
            final Integer slot = current.findFrameSlotOrNull(name);

            if (slot != null) {
                final ReadLocalNode node;
                if (level == 0) {
                    node = new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, slot);
                } else {
                    node = new ReadDeclarationVariableNode(LocalVariableType.FRAME_LOCAL, level, slot);
                }

                return node;
            }

            if (current.getNeverAssignInParentScope()) {
                // Do not try to look above scope barriers (def, module)
                return null;
            }

            current = current.parent;
            level++;
        }

        return null;
    }

    public FrameDescriptor computeFrameDescriptor() {
        if (frameDescriptor != null) {
            return frameDescriptor;
        }

        frameDescriptor = frameDescriptorBuilder.build();
        descriptorInfoForChildren.setParentDescriptor(frameDescriptor);
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

    public boolean hasOwnScopeForAssignments() {
        return ownScopeForAssignments;
    }

    /** Whether this is a lexical scope barrier (def, module, class) */
    public boolean getNeverAssignInParentScope() {
        return !isBlock();
    }

    /** A way to check current scope is a module/class. If in a block, isModuleBody is always false. */
    public boolean isModuleBody() {
        return isModuleBody;
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }

    public List<Integer> getFlipFlopStates() {
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

    /** Used only in tests to make temporary variable names stable and not changed every time tests are run. It
     * shouldn't be used for anything except that purpose. */
    public static void resetTemporaryVariablesIndex() {
        tempIndex.set(0);
    }
}
