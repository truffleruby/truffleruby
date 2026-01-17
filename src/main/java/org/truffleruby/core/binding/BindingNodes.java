/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.binding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.annotations.Split;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.locals.FindDeclarationVariableNodes;
import org.truffleruby.language.locals.FindDeclarationVariableNodes.FindAndReadDeclarationVariableNode;
import org.truffleruby.language.locals.FindDeclarationVariableNodes.FrameSlotAndDepth;
import org.truffleruby.language.locals.FrameDescriptorNamesIterator;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.locals.WriteFrameSlotNode;
import org.truffleruby.parser.FrameDescriptorInfo;
import org.truffleruby.parser.TranslatorEnvironment;

@CoreModule(value = "Binding", isClass = true)
public abstract class BindingNodes {

    /** Creates a Binding without a SourceSection, only for Binding used internally and not exposed to the user. */
    public static RubyBinding createBinding(RubyContext context, RubyLanguage language, MaterializedFrame frame) {
        return createBinding(context, language, frame, null);
    }

    public static RubyBinding createBinding(RubyContext context, RubyLanguage language, MaterializedFrame frame,
            SourceSection sourceSection) {
        return new RubyBinding(
                context.getCoreLibrary().bindingClass,
                language.bindingShape,
                frame,
                sourceSection);
    }

    @TruffleBoundary
    public static FrameDescriptor newFrameDescriptor(FrameDescriptor parentDescriptor) {
        var parentInfo = FrameDescriptorInfo.of(parentDescriptor);
        var info = new FrameDescriptorInfo(parentDescriptor,
                parentInfo.getSharedMethodInfo().addOneBlockDepthButKeepParseNameAndRuntimeName());
        return TranslatorEnvironment.newFrameDescriptorBuilderForBlock(info).build();
    }

    static final int NEW_VAR_INDEX = 1;

    @TruffleBoundary
    public static FrameDescriptor newFrameDescriptor(FrameDescriptor parentDescriptor, String name) {
        assert name != null && !name.isEmpty();

        var parentInfo = FrameDescriptorInfo.of(parentDescriptor);
        var info = new FrameDescriptorInfo(parentDescriptor,
                parentInfo.getSharedMethodInfo().addOneBlockDepthButKeepParseNameAndRuntimeName());
        var builder = TranslatorEnvironment.newFrameDescriptorBuilderForBlock(info);
        int index = builder.addSlot(FrameSlotKind.Illegal, name, null);
        if (index != NEW_VAR_INDEX) {
            throw CompilerDirectives.shouldNotReachHere("new binding variable not at index 1");
        }
        return builder.build();
    }

    public static FrameDescriptor getFrameDescriptor(RubyBinding binding) {
        return binding.getFrame().getFrameDescriptor();
    }

    public static MaterializedFrame newFrame(MaterializedFrame parent, FrameDescriptor descriptor) {
        // createVirtualFrame().materialize() optimizes better than createMaterializedFrame()
        return Truffle.getRuntime().createVirtualFrame(
                RubyArguments.pack(
                        parent,
                        null,
                        RubyArguments.getMethod(parent),
                        RubyArguments.getDeclarationContext(parent),
                        null,
                        RubyArguments.getSelf(parent),
                        RubyArguments.getBlock(parent),
                        RubyArguments.getDescriptor(parent),
                        RubyArguments.getRawArguments(parent)),
                descriptor).materialize();
    }

    public static void insertAncestorFrame(RubyLanguage language, RubyBinding binding,
            MaterializedFrame mainScriptFrame) {
        var originalFrame = binding.getFrame();
        var frame = mainScriptFrame;

        while (originalFrame.getFrameDescriptor() != language.EMPTY_BINDING_DESCRIPTOR) {
            // copy it over but with the correct blockDepth, so we need to create new FrameDescriptors
            assert originalFrame.getFrameDescriptor().getNumberOfSlots() == NEW_VAR_INDEX + 1;
            String name = (String) originalFrame.getFrameDescriptor().getSlotName(NEW_VAR_INDEX);
            Object value = originalFrame.getValue(NEW_VAR_INDEX);

            var newDescriptor = newFrameDescriptor(frame.getFrameDescriptor(), name);
            frame = newFrame(frame, newDescriptor);
            frame.setObject(NEW_VAR_INDEX, value);

            originalFrame = RubyArguments.getDeclarationFrame(originalFrame);
        }

        binding.setFrame(frame);
    }

    @TruffleBoundary
    public static boolean assignsNewUserVariables(FrameDescriptor descriptor) {
        for (Object identifier : FrameDescriptorNamesIterator.iterate(descriptor)) {
            if (!BindingNodes.isHiddenVariable(identifier)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHiddenVariable(Object name) {
        if (name instanceof String) {
            return isHiddenVariable((String) name);
        } else {
            return true;
        }
    }

    @Idempotent
    static boolean isHiddenVariable(String name) {
        assert !name.isEmpty();
        final char first = name.charAt(0);
        return first == '$' || // Frame-local global variable
                first == Layouts.TEMP_PREFIX_CHAR;
    }

    @CoreMethod(names = { "dup", "clone" })
    public abstract static class DupNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyBinding dup(RubyBinding binding) {
            return new RubyBinding(
                    coreLibrary().bindingClass,
                    getLanguage().bindingShape,
                    binding.getFrame(),
                    binding.sourceSection);
        }
    }

    /** Same as {@link LocalVariableDefinedNode} but returns false instead of raising an exception for hidden
     * variables. */
    @ImportStatic({ BindingNodes.class, FindDeclarationVariableNodes.class })
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HasLocalVariableNode extends RubyBaseNode {

        public abstract boolean execute(Node node, RubyBinding binding, String name);

        @Specialization(
                guards = {
                        "name == cachedName",
                        "getFrameDescriptor(binding) == descriptor" },
                limit = "getCacheLimit()")
        static boolean localVariableDefinedCached(RubyBinding binding, String name,
                @Cached("name") String cachedName,
                @Cached("getFrameDescriptor(binding)") FrameDescriptor descriptor,
                @Cached("findFrameSlotOrNull(name, binding.getFrame())") FrameSlotAndDepth cachedFrameSlot) {
            return cachedFrameSlot != null;
        }

        @TruffleBoundary
        @Specialization(replaces = "localVariableDefinedCached")
        static boolean localVariableDefinedUncached(RubyBinding binding, String name) {
            return FindDeclarationVariableNodes.findFrameSlotOrNull(name, binding.getFrame()) != null;
        }

        protected int getCacheLimit() {
            return getLanguage().options.BINDING_LOCAL_VARIABLE_CACHE;
        }

    }

    @ImportStatic({ BindingNodes.class, FindDeclarationVariableNodes.class })
    @CoreMethod(names = "local_variable_defined?", required = 1, split = Split.ALWAYS)
    public abstract static class BindingLocalVariableDefinedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean localVariableDefined(RubyBinding binding, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LocalVariableDefinedNode localVariableDefinedNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            return localVariableDefinedNode.execute(this, binding, name);
        }
    }


    @ImportStatic({ BindingNodes.class, FindDeclarationVariableNodes.class })
    @GenerateCached(false)
    @GenerateInline
    @ReportPolymorphism // inline cache
    public abstract static class LocalVariableDefinedNode extends RubyBaseNode {

        public abstract boolean execute(Node node, RubyBinding binding, String name);

        @Specialization(
                guards = {
                        "name == cachedName",
                        "!isHiddenVariable(cachedName)",
                        "getFrameDescriptor(binding) == descriptor" },
                limit = "getCacheLimit()")
        static boolean localVariableDefinedCached(RubyBinding binding, String name,
                @Cached("name") String cachedName,
                @Cached("getFrameDescriptor(binding)") FrameDescriptor descriptor,
                @Cached("findFrameSlotOrNull(name, binding.getFrame())") FrameSlotAndDepth cachedFrameSlot) {
            return cachedFrameSlot != null;
        }

        @TruffleBoundary
        @Specialization(guards = "!isHiddenVariable(name)", replaces = "localVariableDefinedCached")
        static boolean localVariableDefinedUncached(RubyBinding binding, String name) {
            return FindDeclarationVariableNodes.findFrameSlotOrNull(name, binding.getFrame()) != null;
        }

        @TruffleBoundary
        @Specialization(guards = "isHiddenVariable(name)")
        static boolean localVariableDefinedLastLine(Node node, RubyBinding binding, String name) {
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).nameError("Bad local variable name", binding, name, node));
        }

        protected int getCacheLimit() {
            return getLanguage().options.BINDING_LOCAL_VARIABLE_CACHE;
        }
    }

    @CoreMethod(names = "local_variable_get", required = 1)
    @ImportStatic(BindingNodes.class)
    public abstract static class BindingLocalVariableGetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object localVariableGet(RubyBinding binding, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LocalVariableGetNode localVariableGetNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            return localVariableGetNode.execute(this, binding, name);
        }
    }

    @GenerateUncached
    @ImportStatic(BindingNodes.class)
    @GenerateInline
    @GenerateCached(false)
    public abstract static class LocalVariableGetNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyBinding binding, String name);

        @Specialization(guards = "!isHiddenVariable(name)")
        static Object localVariableGet(Node node, RubyBinding binding, String name,
                @Cached FindAndReadDeclarationVariableNode readNode) {
            MaterializedFrame frame = binding.getFrame();
            Object result = readNode.execute(frame, node, name, null);
            if (result == null) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).nameErrorLocalVariableNotDefined(name, binding, node));
            }
            return result;
        }

        @TruffleBoundary
        @Specialization(guards = "isHiddenVariable(name)")
        static Object localVariableGetLastLine(Node node, RubyBinding binding, String name) {
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).nameError("Bad local variable name", binding, name, node));
        }
    }

    @CoreMethod(names = "local_variable_set", required = 2)
    public abstract static class BindingLocalVariableSetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object localVariableSet(RubyBinding binding, Object nameObject, Object value,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LocalVariableSetNode localVariableSetNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            return localVariableSetNode.execute(this, binding, name, value);
        }
    }


    @ReportPolymorphism // inline cache
    @GenerateUncached
    @ImportStatic({ BindingNodes.class, FindDeclarationVariableNodes.class })
    @GenerateCached(false)
    @GenerateInline
    public abstract static class LocalVariableSetNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyBinding binding, String name, Object value);

        @Specialization(
                guards = {
                        "name == cachedName",
                        "!isHiddenVariable(cachedName)",
                        "getFrameDescriptor(binding) == cachedFrameDescriptor",
                        "cachedFrameSlot != null" },
                limit = "getCacheLimit()")
        static Object localVariableSetCached(RubyBinding binding, String name, Object value,
                @Cached("name") String cachedName,
                @Cached("getFrameDescriptor(binding)") FrameDescriptor cachedFrameDescriptor,
                @Cached("findFrameSlotOrNull(name, binding.getFrame())") FrameSlotAndDepth cachedFrameSlot,
                @Cached(parameters = "cachedFrameSlot.slot") WriteFrameSlotNode writeLocalVariableNode) {
            final MaterializedFrame frame = RubyArguments
                    .getDeclarationFrame(binding.getFrame(), cachedFrameSlot.depth);
            writeLocalVariableNode.executeWrite(frame, value);
            return value;
        }

        @Specialization(
                guards = {
                        "name == cachedName",
                        "!isHiddenVariable(cachedName)",
                        "getFrameDescriptor(binding) == cachedFrameDescriptor",
                        "cachedFrameSlot == null" },
                limit = "getCacheLimit()")
        static Object localVariableSetNewCached(RubyBinding binding, String name, Object value,
                @Cached("name") String cachedName,
                @Cached("getFrameDescriptor(binding)") FrameDescriptor cachedFrameDescriptor,
                @Cached("findFrameSlotOrNull(name, binding.getFrame())") FrameSlotAndDepth cachedFrameSlot,
                @Cached("newFrameDescriptor(cachedFrameDescriptor, name)") FrameDescriptor newDescriptor,
                @Cached(parameters = "NEW_VAR_INDEX") WriteFrameSlotNode writeLocalVariableNode) {
            var frame = newFrame(binding.getFrame(), newDescriptor);
            binding.setFrame(frame);
            writeLocalVariableNode.executeWrite(frame, value);
            return value;
        }

        @TruffleBoundary
        @Specialization(
                guards = "!isHiddenVariable(name)",
                replaces = { "localVariableSetCached", "localVariableSetNewCached" })
        static Object localVariableSetUncached(RubyBinding binding, String name, Object value) {
            MaterializedFrame frame = binding.getFrame();
            final FrameSlotAndDepth frameSlot = FindDeclarationVariableNodes.findFrameSlotOrNull(name, frame);
            final int slot;
            if (frameSlot != null) {
                frame = RubyArguments.getDeclarationFrame(frame, frameSlot.depth);
                slot = frameSlot.slot;
            } else {
                var newDescriptor = newFrameDescriptor(frame.getFrameDescriptor(), name);
                frame = newFrame(frame, newDescriptor);
                binding.setFrame(frame);
                assert newDescriptor.getSlotName(NEW_VAR_INDEX) == name;
                slot = NEW_VAR_INDEX;
            }
            frame.setObject(slot, value);
            return value;
        }

        @TruffleBoundary
        @Specialization(guards = "isHiddenVariable(name)")
        static Object localVariableSetLastLine(Node node, RubyBinding binding, String name, Object value) {
            throw new RaiseException(
                    getContext(node),
                    coreExceptions(node).nameError("Bad local variable name", binding, name, node));
        }

        protected int getCacheLimit() {
            return getLanguage().options.BINDING_LOCAL_VARIABLE_CACHE;
        }
    }

    @Primitive(name = "local_variable_names")
    @ImportStatic(BindingNodes.class)
    public abstract static class LocalVariablesNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "getFrameDescriptor(binding) == cachedFrameDescriptor", limit = "getCacheLimit()")
        RubyArray localVariablesCached(RubyBinding binding,
                @Cached("getFrameDescriptor(binding)") FrameDescriptor cachedFrameDescriptor,
                @Cached("listLocalVariablesAsSymbols(getContext(), binding.getFrame())") RubyArray names) {
            return names;
        }

        @Specialization(replaces = "localVariablesCached")
        RubyArray localVariables(RubyBinding binding) {
            return listLocalVariablesAsSymbols(getContext(), binding.getFrame());
        }

        @TruffleBoundary
        public RubyArray listLocalVariablesAsSymbols(RubyContext context, MaterializedFrame frame) {
            final Set<Object> names = new LinkedHashSet<>();
            while (frame != null) {
                addNamesFromFrame(frame, names);
                frame = RubyArguments.getDeclarationFrame(frame);
            }
            return ArrayHelpers.createArray(context, getLanguage(), names.toArray());
        }

        private void addNamesFromFrame(Frame frame, Set<Object> names) {
            for (Object identifier : FrameDescriptorNamesIterator.iterate(frame.getFrameDescriptor())) {
                if (!isHiddenVariable(identifier)) {
                    names.add(getSymbol((String) identifier));
                }
            }
        }

        @TruffleBoundary
        public static List<String> listLocalVariablesWithDuplicates(MaterializedFrame frame, String receiverName) {
            List<String> members = new ArrayList<>();
            Frame currentFrame = frame;
            while (currentFrame != null) {
                final FrameDescriptor frameDescriptor = currentFrame.getFrameDescriptor();
                for (Object identifier : FrameDescriptorNamesIterator.iterate(frameDescriptor)) {
                    if (!isHiddenVariable(identifier)) {
                        members.add((String) identifier);
                    }
                }
                if (receiverName != null) {
                    members.add(receiverName);
                }
                currentFrame = RubyArguments.getDeclarationFrame(currentFrame);
            }
            return members;
        }

        protected int getCacheLimit() {
            return getLanguage().options.BINDING_LOCAL_VARIABLE_CACHE;
        }
    }

    @CoreMethod(names = "receiver")
    public abstract static class ReceiverNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object receiver(RubyBinding binding) {
            return RubyArguments.getSelf(binding.getFrame());
        }
    }

    @CoreMethod(names = "source_location")
    public abstract static class SourceLocationNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object sourceLocation(RubyBinding binding,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            var sourceSection = binding.sourceSection;
            return getLanguage().rubySourceLocation(sourceSection, fromJavaStringNode, this);
        }
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object allocate(RubyClass rubyClass) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorAllocatorUndefinedFor(rubyClass, this));
        }
    }

    @Primitive(name = "create_empty_binding")
    public abstract static class CreateEmptyBindingNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyBinding binding(VirtualFrame frame) {
            // Use the current frame to initialize the arguments, etc, correctly
            final MaterializedFrame newFrame = newFrame(frame.materialize(), getLanguage().EMPTY_BINDING_DESCRIPTOR);
            RubyArguments.setDeclarationFrame(newFrame, null); // detach from the current frame
            return BindingNodes.createBinding(getContext(), getLanguage(), newFrame, getEncapsulatingSourceSection());
        }
    }

}
