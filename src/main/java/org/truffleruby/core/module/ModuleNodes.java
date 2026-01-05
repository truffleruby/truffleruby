/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.ByteIndexOfStringNode;
import org.truffleruby.RubyContext;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.ReRaiseInlinedExceptionNode;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.cast.BooleanCastWithDefaultNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.SingleValueCastNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToPathNode;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.cast.ToStringOrSymbolNode;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.constant.WarnAlreadyInitializedNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.MethodFilter;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.method.RubyUnboundMethod;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.string.TStringConstants;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.LazyWarnNode;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyLambdaRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.WarningNode.UncachedWarningNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.constants.ConstantEntry;
import org.truffleruby.language.constants.GetConstantNode;
import org.truffleruby.language.constants.LookupConstantInterface;
import org.truffleruby.language.constants.LookupConstantNode;
import org.truffleruby.language.constants.WarnDeprecatedConstantNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.loader.EvalLoader;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.DeclarationContext.FixedDefaultDefinee;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.methods.UsingNode;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.objects.classvariables.CheckClassVariableNameNode;
import org.truffleruby.language.objects.classvariables.ClassVariableStorage;
import org.truffleruby.language.objects.classvariables.LookupClassVariableNode;
import org.truffleruby.language.objects.classvariables.SetClassVariableNode;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.parser.Identifiers;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.builtins.PrimitiveNode.FAILURE;
import static org.truffleruby.core.module.ModuleNodes.GenerateAccessorNode.Accessor.BOTH;
import static org.truffleruby.core.module.ModuleNodes.GenerateAccessorNode.Accessor.READER;
import static org.truffleruby.core.module.ModuleNodes.GenerateAccessorNode.Accessor.WRITER;

@CoreModule(value = "Module", isClass = true)
public abstract class ModuleNodes {

    @TruffleBoundary
    public static RubyModule createModule(RubyContext context, SourceSection sourceSection, RubyClass selfClass,
            RubyModule lexicalParent, String name, Node currentNode) {
        final RubyModule module = new RubyModule(
                selfClass,
                context.getLanguageSlow().moduleShape,
                context.getLanguageSlow(),
                sourceSection,
                lexicalParent,
                name);

        module.fields.afterConstructed();

        if (lexicalParent != null) {
            lexicalParent.fields.setConstant(context, currentNode, name, module);
        }
        return module;
    }

    @CoreMethod(names = "===", required = 1)
    public abstract static class ContainsInstanceNode extends CoreMethodArrayArgumentsNode {

        @Child private IsANode isANode = IsANode.create();

        @Specialization
        boolean containsInstance(RubyModule module, Object instance) {
            return isANode.executeIsA(instance, module);
        }

    }

    @CoreMethod(names = "<", required = 1)
    public abstract static class IsSubclassOfNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeIsSubclassOf(VirtualFrame frame, RubyModule self, Object other);

        @TruffleBoundary
        @Specialization
        Object isSubclassOf(RubyModule self, RubyModule other) {
            if (self == other) {
                return false;
            }

            if (ModuleOperations.includesModule(self, other)) {
                return true;
            }

            if (ModuleOperations.includesModule(other, self)) {
                return false;
            }

            return nil;
        }

        @Specialization(guards = "!isRubyModule(other)")
        Object isSubclassOfOther(RubyModule self, Object other) {
            throw new RaiseException(getContext(), coreExceptions().typeError("compared with non class/module", this));
        }

    }

    @CoreMethod(names = "<=", required = 1)
    public abstract static class IsSubclassOfOrEqualToNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeIsSubclassOfOrEqualTo(RubyModule self, Object other);

        @TruffleBoundary
        @Specialization
        Object isSubclassOfOrEqualTo(RubyModule self, RubyModule other) {
            if (self == other || ModuleOperations.includesModule(self, other)) {
                return true;
            }

            if (ModuleOperations.includesModule(other, self)) {
                return false;
            }

            return nil;
        }

        @Specialization(guards = "!isRubyModule(other)")
        Object isSubclassOfOrEqualToOther(RubyModule self, Object other) {
            throw new RaiseException(getContext(), coreExceptions().typeError("compared with non class/module", this));
        }

    }

    @CoreMethod(names = ">", required = 1)
    public abstract static class IsSuperclassOfNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeIsSuperclassOf(VirtualFrame frame, RubyModule self, Object other);

        @TruffleBoundary
        @Specialization
        Object isSuperclassOf(RubyModule self, RubyModule other) {
            if (self == other) {
                return false;
            }

            if (ModuleOperations.includesModule(other, self)) {
                return true;
            }

            if (ModuleOperations.includesModule(self, other)) {
                return false;
            }

            return nil;
        }

        @Specialization(guards = "!isRubyModule(other)")
        Object isSuperclassOfOther(RubyModule self, Object other) {
            throw new RaiseException(getContext(), coreExceptions().typeError("compared with non class/module", this));
        }

    }

    @CoreMethod(names = ">=", required = 1)
    public abstract static class IsSuperclassOfOrEqualToNode extends CoreMethodArrayArgumentsNode {

        public abstract Object executeIsSuperclassOfOrEqualTo(VirtualFrame frame, RubyModule self, Object other);

        @TruffleBoundary
        @Specialization
        Object isSuperclassOfOrEqualTo(RubyModule self, RubyModule other) {
            if (self == other || ModuleOperations.includesModule(other, self)) {
                return true;
            }

            if (ModuleOperations.includesModule(self, other)) {
                return false;
            }

            return nil;
        }

        @Specialization(guards = "!isRubyModule(other)")
        Object isSuperclassOfOrEqualToOther(RubyModule self, Object other) {
            throw new RaiseException(getContext(), coreExceptions().typeError("compared with non class/module", this));
        }

    }

    @CoreMethod(names = "<=>", required = 1)
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Child private IsSubclassOfOrEqualToNode subclassNode;

        private Object isSubclass(RubyModule self, RubyModule other) {
            if (subclassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                subclassNode = insert(ModuleNodesFactory.IsSubclassOfOrEqualToNodeFactory.create(null));
            }
            return subclassNode.executeIsSubclassOfOrEqualTo(self, other);
        }

        @Specialization
        Object compare(RubyModule self, RubyModule other) {
            if (self == other) {
                return 0;
            }

            final Object isSubclass = isSubclass(self, other);

            if (isSubclass == nil) {
                return nil;
            } else {
                return (boolean) isSubclass ? -1 : 1;
            }
        }

        @Specialization(guards = "!isRubyModule(other)")
        Object compareOther(RubyModule self, Object other) {
            return nil;
        }

    }

    @CoreMethod(names = "alias_method", required = 2, raiseIfFrozenSelf = true, split = Split.NEVER)
    public abstract static class AliasMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol aliasMethod(RubyModule module, Object newNameObject, Object oldNameObject,
                @Cached ToSymbolNode toSymbolNode) {
            final var newName = toSymbolNode.execute(this, newNameObject);
            final var oldName = toSymbolNode.execute(this, oldNameObject);
            return aliasMethod(module, newName, oldName, this);
        }

        @TruffleBoundary
        static RubySymbol aliasMethod(RubyModule module, RubySymbol newName, RubySymbol oldName, RubyNode node) {
            RubyContext context = RubyContext.get(node);
            module.fields.checkFrozen(context, node);

            final InternalMethod method = module.fields
                    .deepMethodSearch(context, oldName.getString());

            if (method == null) {
                throw new RaiseException(context, context.getCoreExceptions().nameErrorUndefinedMethod(
                        oldName.getString(),
                        module,
                        node));
            }

            final InternalMethod aliasMethod = method.withName(newName.getString());
            module.addMethodConsiderNameVisibility(context, aliasMethod, aliasMethod.getVisibility(), node);
            return newName;
        }

    }

    public static final class AliasKeywordNode extends RubyContextSourceNode {

        @Child private RubyNode newName;
        @Child private RubyNode oldName;

        public AliasKeywordNode(RubyNode newName, RubyNode oldName) {
            this.newName = newName;
            this.oldName = oldName;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            var module = RubyArguments.getDeclarationContext(frame).getModuleToDefineMethods();

            final Object newNameObject = newName.execute(frame);
            final Object oldNameObject = oldName.execute(frame);

            RubySymbol newNameSymbol = (RubySymbol) newNameObject;
            RubySymbol oldNameSymbol = (RubySymbol) oldNameObject;
            return AliasMethodNode.aliasMethod(module, newNameSymbol, oldNameSymbol, this);
        }

        @Override
        public RubyNode cloneUninitialized() {
            return new AliasKeywordNode(newName, oldName).copyFlags(this);
        }

    }

    @CoreMethod(names = "ancestors")
    public abstract static class AncestorsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray ancestors(RubyModule self) {
            final List<RubyModule> ancestors = new ArrayList<>();
            for (RubyModule module : self.fields.ancestors()) {
                ancestors.add(module);
            }

            return createArray(ancestors.toArray());
        }
    }

    @CoreMethod(names = "append_features", required = 1, visibility = Visibility.PRIVATE, split = Split.NEVER)
    public abstract static class AppendFeaturesNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object appendFeatures(RubyModule features, RubyModule target,
                @Cached InlinedBranchProfile errorProfile) {
            if (features instanceof RubyClass) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeError("append_features must be called only on modules", this));
            }
            target.fields.include(getContext(), this, features);
            return nil;
        }
    }

    @GenerateUncached
    public abstract static class GeneratedReaderNode extends AlwaysInlinedMethodNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        Object reader(Frame callerFrame, RubyDynamicObject self, Object[] rubyArgs, RootCallTarget target,
                @CachedLibrary("self") DynamicObjectLibrary objectLibrary) {
            // Or a subclass of RubyRootNode with an extra field?
            final String ivarName = RubyRootNode.of(target).getSharedMethodInfo().getNotes();
            CompilerAsserts.partialEvaluationConstant(ivarName);

            return objectLibrary.getOrDefault(self, ivarName, nil);
        }

        @Specialization(guards = "!isRubyDynamicObject(self)")
        Object notObject(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target) {
            return nil;
        }
    }

    @GenerateUncached
    public abstract static class GeneratedWriterNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "!isFrozenNode.execute(self)")
        Object writer(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared IsFrozenNode isFrozenNode,
                @Cached WriteObjectFieldNode writeObjectFieldNode) {
            final String ivarName = RubyRootNode.of(target).getSharedMethodInfo().getNotes();
            CompilerAsserts.partialEvaluationConstant(ivarName);

            final Object value = RubyArguments.getArgument(rubyArgs, 0);
            writeObjectFieldNode.execute(this, (RubyDynamicObject) self, ivarName, value);
            return value;
        }

        @Specialization(guards = "isFrozenNode.execute(self)")
        Object frozen(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared IsFrozenNode isFrozenNode) {
            throw new RaiseException(getContext(), coreExceptions().frozenError(self, this));
        }
    }

    public abstract static class GenerateAccessorNode extends AlwaysInlinedMethodNode {
        enum Accessor {
            READER,
            WRITER,
            BOTH
        }

        protected Object[] generateAccessors(Frame callerFrame, RubyModule module, Object[] names,
                Accessor accessor, RootCallTarget target) {
            needCallerFrame(callerFrame, target);
            final Visibility visibility = DeclarationContext
                    .findVisibilityCheckSelfAndDefaultDefinee(module, callerFrame);
            return createAccessors(module, names, accessor, visibility);
        }

        @TruffleBoundary
        private Object[] createAccessors(RubyModule module, Object[] names, Accessor accessor,
                Visibility visibility) {
            final Node currentNode = getAdoptedNode(this);
            final SourceSection sourceSection;
            if (currentNode != null) {
                sourceSection = currentNode.getEncapsulatingSourceSection();
            } else {
                sourceSection = CoreLibrary.UNAVAILABLE_SOURCE_SECTION;
            }

            Object[] generatedMethods = accessor == BOTH ? new Object[names.length * 2] : new Object[names.length];
            int i = 0;
            for (Object nameObject : names) {
                final String name = NameToJavaStringNode.executeUncached(nameObject);
                if (accessor == BOTH) {
                    generatedMethods[i++] = createAccessor(module, name, READER, visibility, sourceSection);
                    generatedMethods[i++] = createAccessor(module, name, WRITER, visibility, sourceSection);
                } else {
                    generatedMethods[i++] = createAccessor(module, name, accessor, visibility, sourceSection);
                }
            }
            return generatedMethods;
        }

        @TruffleBoundary
        private RubySymbol createAccessor(RubyModule module, String name, Accessor accessor, Visibility visibility,
                SourceSection sourceSection) {
            assert accessor != BOTH;
            final Arity arity = accessor == READER ? Arity.NO_ARGUMENTS : Arity.ONE_REQUIRED;
            final String ivar = "@" + name;
            final String accessorName = accessor == READER ? name : name + "=";

            String parseName = SharedMethodInfo.moduleAndMethodNameIfModuleIsFullyNamed(module, accessorName, arity);

            final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                    sourceSection,
                    LexicalScope.IGNORE,
                    arity,
                    accessorName,
                    0,
                    parseName,
                    ivar, // notes
                    null);

            final NodeFactory<? extends RubyBaseNode> alwaysInlinedNodeFactory = accessor == READER
                    ? ModuleNodesFactory.GeneratedReaderNodeFactory.getInstance()
                    : ModuleNodesFactory.GeneratedWriterNodeFactory.getInstance();

            final RubyRootNode reRaiseRootNode = new RubyRootNode(
                    getLanguage(),
                    sourceSection,
                    null,
                    sharedMethodInfo,
                    new ReRaiseInlinedExceptionNode(alwaysInlinedNodeFactory),
                    Split.NEVER,
                    ReturnID.INVALID);
            final RootCallTarget callTarget = reRaiseRootNode.getCallTarget();

            final InternalMethod method = new InternalMethod(
                    getContext(),
                    sharedMethodInfo,
                    LexicalScope.IGNORE,
                    DeclarationContext.NONE,
                    accessorName,
                    module,
                    visibility,
                    false,
                    alwaysInlinedNodeFactory,
                    null,
                    callTarget,
                    null);

            module.fields.addMethod(getContext(), this, method);
            return getLanguage().getSymbol(method.getName());
        }
    }

    @GenerateUncached
    @CoreMethod(names = "attr", rest = true, alwaysInlined = true)
    public abstract static class AttrNode extends GenerateAccessorNode {
        @Specialization
        Object attr(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target) {
            final boolean setter;
            Object[] names = RubyArguments.getPositionalArguments(rubyArgs);
            if (names.length == 2 && names[1] instanceof Boolean) {
                warnObsoletedBooleanArgument();
                setter = (boolean) names[1];
                names = new Object[]{ names[0] };
            } else {
                setter = false;
            }

            return createArray(generateAccessors(callerFrame, module, names, setter ? BOTH : READER, target));
        }

        @TruffleBoundary
        private void warnObsoletedBooleanArgument() {
            final UncachedWarningNode warningNode = UncachedWarningNode.INSTANCE;
            if (warningNode.shouldWarn()) {
                final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
                warningNode.warningMessage(sourceSection, "optional boolean argument is obsoleted");
            }
        }
    }

    @GenerateUncached
    @CoreMethod(names = "attr_accessor", rest = true, alwaysInlined = true)
    public abstract static class AttrAccessorNode extends GenerateAccessorNode {
        @Specialization
        Object attrAccessor(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target) {
            Object[] names = RubyArguments.getPositionalArguments(rubyArgs);
            return createArray(generateAccessors(callerFrame, module, names, BOTH, target));
        }
    }

    @GenerateUncached
    @CoreMethod(names = "attr_reader", rest = true, alwaysInlined = true)
    public abstract static class AttrReaderNode extends GenerateAccessorNode {
        @Specialization
        Object attrReader(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target) {
            Object[] names = RubyArguments.getPositionalArguments(rubyArgs);
            return createArray(generateAccessors(callerFrame, module, names, READER, target));
        }
    }

    @GenerateUncached
    @CoreMethod(names = "attr_writer", rest = true, alwaysInlined = true)
    public abstract static class AttrWriterNode extends GenerateAccessorNode {
        @Specialization
        Object attrWriter(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target) {
            Object[] names = RubyArguments.getPositionalArguments(rubyArgs);
            return createArray(generateAccessors(callerFrame, module, names, WRITER, target));
        }
    }

    @CoreMethod(names = "autoload", required = 2)
    public abstract static class AutoloadNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "libFilename.isRubyString(this, filenameAsPath)", limit = "1")
        static Object autoload(RubyModule module, Object nameObject, Object filename,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached ToPathNode toPathNode,
                @Bind Node node,
                @Bind("toPathNode.execute(node, filename)") Object filenameAsPath,
                @Cached RubyStringLibrary libFilename) {
            final var name = nameToJavaStringNode.execute(node, nameObject);
            if (!Identifiers.isValidConstantName(name)) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).nameError(
                                StringUtils.format("autoload must be constant name: %s", name),
                                module,
                                name,
                                node));
            }

            if (libFilename.getTString(node, filenameAsPath).isEmpty()) {
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError("empty file name", node));
            }

            final String javaStringFilename = StringOperations.getJavaString(filenameAsPath);
            module.fields.setAutoloadConstant(getLanguage(node), getContext(node), node, name, filenameAsPath,
                    javaStringFilename);
            return nil;
        }
    }

    @Primitive(name = "module_anonymous?")
    public abstract static class IsAnonymousNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isAnonymous(RubyModule module) {
            return module.fields.isAnonymousOrTemporary();
        }

    }

    @CoreMethod(names = "autoload?", required = 1, optional = 1)
    public abstract static class IsAutoloadNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        Object isAutoload(RubyModule module, Object nameObject, Object maybeInherit,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            final var inherit = booleanCastWithDefaultNode.execute(this, maybeInherit, true);
            final ConstantLookupResult constant = ModuleOperations.lookupConstantWithInherit(
                    getContext(),
                    module,
                    name,
                    inherit,
                    this,
                    false,
                    false);

            if (constant.isAutoload() && !constant.getConstant().getAutoloadConstant().isAutoloadingThread()) {
                return constant.getConstant().getAutoloadConstant().getFeature();
            } else {
                return nil;
            }
        }
    }

    @GenerateUncached
    @CoreMethod(names = { "class_eval", "module_eval" }, optional = 3, needsBlock = true, alwaysInlined = true)
    public abstract static class ClassEvalNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "isBlockProvided(rubyArgs)")
        Object evalWithBlock(Frame callerFrame, RubyModule self, Object[] rubyArgs, RootCallTarget target,
                @Cached @Exclusive InlinedBranchProfile wrongNumberOfArgumentsProfile,
                @Cached ClassExecBlockNode classExecNode) {
            final int count = RubyArguments.getPositionalArgumentsCount(rubyArgs);

            if (count > 0) {
                wrongNumberOfArgumentsProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().argumentError(count, 0, this));
            }

            final Object block = RubyArguments.getBlock(rubyArgs);
            return classExecNode.execute(this, NoKeywordArgumentsDescriptor.INSTANCE, self, new Object[]{ self },
                    (RubyProc) block);
        }

        @Specialization(guards = "!isBlockProvided(rubyArgs)")
        static Object evalWithString(Frame callerFrame, RubyModule self, Object[] rubyArgs, RootCallTarget target,
                @Cached @Exclusive InlinedBranchProfile wrongNumberOfArgumentsProfile,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached ToStrNode toStrNode,
                @Cached ToIntNode toIntNode,
                @Cached IndirectCallNode callNode,
                @Bind Node node) {
            final Object sourceCode;
            String fileName = coreStrings(node).EVAL_FILENAME_STRING.toString();
            int line = 1;

            int count = RubyArguments.getPositionalArgumentsCount(rubyArgs);

            if (count == 0) {
                wrongNumberOfArgumentsProfile.enter(node);
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError(0, 1, 2, node));
            }

            sourceCode = toStrNode.execute(node, RubyArguments.getArgument(rubyArgs, 0));

            if (count >= 2) {
                fileName = toJavaStringNode
                        .execute(node, toStrNode.execute(node, RubyArguments.getArgument(rubyArgs, 1)));
            }

            if (count >= 3) {
                line = toIntNode.execute(RubyArguments.getArgument(rubyArgs, 2));
            }

            needCallerFrame(node, callerFrame, target);
            return classEvalSource(
                    node,
                    callerFrame.materialize(),
                    self,
                    sourceCode,
                    fileName,
                    line,
                    callNode);
        }

        @TruffleBoundary
        private static Object classEvalSource(Node node, MaterializedFrame callerFrame, RubyModule module,
                Object sourceCode, String file,
                int line,
                IndirectCallNode callNode) {
            final RubySource source = EvalLoader.createEvalSource(
                    getContext(node),
                    RubyStringLibrary.getUncached().getTString(node, sourceCode),
                    RubyStringLibrary.getUncached().getEncoding(node, sourceCode),
                    "class/module_eval",
                    file,
                    line,
                    node);

            final LexicalScope lexicalScope = new LexicalScope(
                    RubyArguments.getMethod(callerFrame).getLexicalScope(),
                    module);

            final RootCallTarget callTarget = getContext(node).getCodeLoader().parse(
                    source,
                    ParserContext.MODULE,
                    callerFrame,
                    lexicalScope,
                    node);

            final CodeLoader.DeferredCall deferredCall = getContext(node).getCodeLoader().prepareExecute(
                    callTarget,
                    ParserContext.MODULE,
                    new DeclarationContext(
                            Visibility.PUBLIC,
                            new FixedDefaultDefinee(module),
                            DeclarationContext.NO_REFINEMENTS),
                    callerFrame,
                    module,
                    lexicalScope);

            return deferredCall.call(callNode);
        }

    }

    @CoreMethod(names = { "class_exec", "module_exec" }, rest = true, needsBlock = true)
    public abstract static class ClassExecNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object withBlock(VirtualFrame frame, RubyModule self, Object[] args, RubyProc block,
                @Cached ClassExecBlockNode classExecBlockNode) {
            return classExecBlockNode.execute(this, RubyArguments.getDescriptor(frame), self, args, block);
        }

        @Specialization
        Object noBlock(RubyModule self, Object[] args, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().noBlockGiven(this));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ClassExecBlockNode extends RubyBaseNode {

        public abstract Object execute(Node node, ArgumentsDescriptor descriptor, RubyModule self, Object[] args,
                RubyProc block);

        @Specialization
        static Object classExec(
                Node node, ArgumentsDescriptor descriptor, RubyModule self, Object[] args, RubyProc block,
                @Cached CallBlockNode callBlockNode) {
            final DeclarationContext declarationContext = new DeclarationContext(
                    Visibility.PUBLIC,
                    new FixedDefaultDefinee(self),
                    block.declarationContext.getRefinements());

            return callBlockNode.executeCallBlock(node, declarationContext, block, self, nil, descriptor, args);
        }
    }

    @CoreMethod(names = "class_variable_defined?", required = 1, split = Split.ALWAYS)
    public abstract static class ClassVariableDefinedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isClassVariableDefinedString(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached CheckClassVariableNameNode checkClassVariableNameNode,
                @Cached LookupClassVariableNode lookupClassVariableNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            checkClassVariableNameNode.execute(this, module, name);
            return lookupClassVariableNode.execute(module, name) != null;
        }

    }

    @CoreMethod(names = "class_variable_get", required = 1, split = Split.ALWAYS)
    public abstract static class ClassVariableGetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object getClassVariable(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached CheckClassVariableNameNode checkClassVariableNameNode,
                @Cached LookupClassVariableNode lookupClassVariableNode,
                @Cached InlinedConditionProfile undefinedProfile) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            checkClassVariableNameNode.execute(this, module, name);
            final Object value = lookupClassVariableNode.execute(module, name);

            if (undefinedProfile.profile(this, value == null)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().nameErrorUninitializedClassVariable(module, name, this));
            } else {
                return value;
            }
        }

    }

    @CoreMethod(names = "class_variable_set", required = 2, raiseIfFrozenSelf = true, split = Split.ALWAYS)
    public abstract static class ClassVariableSetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object setClassVariable(RubyModule module, Object nameObject, Object value,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached CheckClassVariableNameNode checkClassVariableNameNode,
                @Cached SetClassVariableNode setClassVariableNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            checkClassVariableNameNode.execute(this, module, name);
            setClassVariableNode.execute(module, name, value);
            return value;
        }

    }

    @CoreMethod(names = "class_variables", optional = 1)
    public abstract static class ClassVariablesNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray getClassVariables(RubyModule module, Object maybeInherit,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode) {
            final boolean inherit = booleanCastWithDefaultNode.execute(this, maybeInherit, true);
            final Set<Object> variables = new LinkedHashSet<>();

            ModuleOperations.classVariableLookup(module, inherit, m -> {
                final ClassVariableStorage classVariableStorage = m.fields.getClassVariables();
                for (Object key : classVariableStorage.getShape().getKeys()) {
                    variables.add(getSymbol((String) key));
                }
                return null;
            });

            return createArray(variables.toArray());
        }
    }

    @CoreMethod(names = "constants", optional = 1)
    public abstract static class ConstantsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray constants(RubyModule module, Object maybeInherit,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode) {
            final boolean inherit = booleanCastWithDefaultNode.execute(this, maybeInherit, true);
            final List<RubySymbol> constantsArray = new ArrayList<>();

            final Iterable<Entry<String, ConstantEntry>> constants;
            if (inherit) {
                constants = ModuleOperations.getAllConstants(module);
            } else {
                constants = module.fields.getConstants();
            }

            for (Entry<String, ConstantEntry> entry : constants) {
                final RubyConstant constant = entry.getValue().getConstant();
                if (constant != null && !constant.isPrivate() && Identifiers.isValidConstantName(constant.getName())) {
                    constantsArray.add(getSymbol(entry.getKey()));
                }
            }

            return createArray(constantsArray.toArray());
        }

    }

    @Primitive(name = "module_const_defined?")
    public abstract static class ConstDefinedNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean isConstDefined(RubyModule module, Object fullNameObject, Object inheritObject, boolean checkName,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final var fullName = nameToJavaStringNode.execute(this, fullNameObject);
            final boolean inherit = booleanCastWithDefaultNode.execute(this, inheritObject, true);
            final ConstantLookupResult constant = ModuleOperations
                    .lookupScopedConstant(getContext(), module, fullName, inherit, this, checkName);
            return constant.isFound();
        }
    }

    @Primitive(name = "module_const_get")
    public abstract static class ConstGetNodePrimitiveNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        Object getConst(RubyModule module, Object nameObject, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached ConstGetNode constGetNode,
                @Cached ToStringOrSymbolNode toStringOrSymbolNode) {
            var name = toStringOrSymbolNode.execute(this, nameObject);
            return constGetNode.execute(this, module, name, inherit, lookInObject, checkName);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @ReportPolymorphism // inline cache
    @ImportStatic(StringOperations.class)
    @SuppressWarnings("truffle-inlining") //TODO [GR-46266] - Remove it when other nodes are converted to DSL inlinable
    public abstract static class ConstGetNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyModule module, Object name, boolean inherit,
                boolean lookInObject, boolean checkName);

        // Symbol

        @Specialization(guards = "inherit")
        static Object getConstant(
                Node node, RubyModule module, RubySymbol name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared GetConstantNode getConstantNode,
                @Cached("create(true, false)") @Shared LookupConstantNode lookupConstantNode,
                @Cached("create(true, true)") @Shared LookupConstantNode lookupConstantLookInObjectNode) {
            return getConstant(module, name.getString(), checkName, lookInObject, getConstantNode, lookupConstantNode,
                    lookupConstantLookInObjectNode);
        }

        @Specialization(guards = "!inherit")
        static Object getConstantNoInherit(
                RubyModule module, RubySymbol name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared GetConstantNode getConstantNode) {
            return getConstantNoInherit(module, name.getString(), checkName, getConstantNode);
        }

        // String

        @Specialization(
                guards = {
                        "stringsName.isRubyString(node, name)",
                        "inherit",
                        "equalNode.execute(stringsName, name, cachedTString, cachedEncoding)",
                        "!scoped",
                        "checkName == cachedCheckName" },
                limit = "getLimit()")
        static Object getConstantStringCached(
                Node node, RubyModule module, Object name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared RubyStringLibrary stringsName,
                @Cached @Shared GetConstantNode getConstantNode,
                @Cached("create(true, false)") @Shared LookupConstantNode lookupConstantNode,
                @Cached("create(true, true)") @Shared LookupConstantNode lookupConstantLookInObjectNode,
                @Cached("asTruffleStringUncached(name)") TruffleString cachedTString,
                @Cached("stringsName.getEncoding(node, name)") RubyEncoding cachedEncoding,
                @Cached("getJavaString(name)") String cachedString,
                @Cached("checkName") boolean cachedCheckName,
                @Cached StringHelperNodes.EqualNode equalNode,
                @Cached("isScoped(cachedString)") boolean scoped) {
            return getConstant(module, cachedString, checkName, lookInObject, getConstantNode, lookupConstantNode,
                    lookupConstantLookInObjectNode);
        }

        @Specialization(
                guards = {
                        "stringsName.isRubyString(node, name)",
                        "inherit",
                        "!isScoped(node, stringsName, name, byteIndexOfStringNode)" },
                replaces = "getConstantStringCached")
        static Object getConstantString(
                Node node, RubyModule module, Object name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared GetConstantNode getConstantNode,
                @Cached @Shared ByteIndexOfStringNode byteIndexOfStringNode,
                @Cached("create(true, false)") @Shared LookupConstantNode lookupConstantNode,
                @Cached("create(true, true)") @Shared LookupConstantNode lookupConstantLookInObjectNode,
                @Cached @Shared RubyStringLibrary stringsName,
                @Cached @Shared ToJavaStringNode toJavaStringNode) {
            return getConstant(module, toJavaStringNode.execute(node, name), checkName, lookInObject, getConstantNode,
                    lookupConstantNode, lookupConstantLookInObjectNode);
        }

        @Specialization(
                guards = {
                        "stringsName.isRubyString(node, name)",
                        "!inherit",
                        "!isScoped(node, stringsName, name, byteIndexOfStringNode)" })
        static Object getConstantNoInheritString(
                Node node, RubyModule module, Object name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared RubyStringLibrary stringsName,
                @Cached @Shared ByteIndexOfStringNode byteIndexOfStringNode,
                @Cached @Shared GetConstantNode getConstantNode,
                @Cached @Shared ToJavaStringNode toJavaStringNode) {
            return getConstantNoInherit(module, toJavaStringNode.execute(node, name), checkName, getConstantNode);
        }

        // Scoped String
        @Specialization(
                guards = {
                        "stringsName.isRubyString(node, name)",
                        "isScoped(node, stringsName, name, byteIndexOfStringNode)" })
        static Object getConstantScoped(
                Node node, RubyModule module, Object name, boolean inherit, boolean lookInObject, boolean checkName,
                @Cached @Shared ByteIndexOfStringNode byteIndexOfStringNode,
                @Cached @Shared RubyStringLibrary stringsName) {
            return FAILURE;
        }

        private static Object getConstant(RubyModule module, String name, boolean checkName, boolean lookInObject,
                GetConstantNode getConstantNode, LookupConstantNode lookupConstantNode,
                LookupConstantNode lookupConstantLookInObjectNode) {
            CompilerAsserts.partialEvaluationConstant(lookInObject);
            if (lookInObject) {
                return getConstantNode
                        .lookupAndResolveConstant(LexicalScope.IGNORE, module, name, checkName,
                                lookupConstantLookInObjectNode, true);
            } else {
                return getConstantNode
                        .lookupAndResolveConstant(LexicalScope.IGNORE, module, name, checkName,
                                lookupConstantNode, true);
            }
        }

        private static Object getConstantNoInherit(RubyModule module, String name, boolean checkName,
                GetConstantNode getConstantNode) {
            final LookupConstantInterface lookup = ConstGetNode::lookupConstantNoInherit;
            return getConstantNode.lookupAndResolveConstant(LexicalScope.IGNORE, module, name, checkName, lookup, true);
        }

        @TruffleBoundary
        private static RubyConstant lookupConstantNoInherit(Node node, LexicalScope lexicalScope, RubyModule module,
                String name, boolean checkName) {
            return ModuleOperations
                    .lookupConstantWithInherit(getContext(node), module, name, false, node, checkName)
                    .getConstant();
        }

        static boolean isScoped(Node node, RubyStringLibrary libString, Object string,
                ByteIndexOfStringNode byteIndexOfStringNode) {
            var tstring = libString.getTString(node, string);
            var encoding = libString.getTEncoding(node, string);
            int byteLength = tstring.byteLength(encoding);
            return byteIndexOfStringNode.execute(tstring, TStringConstants.COLON_COLON, 0, byteLength, encoding) >= 0;
        }

        @TruffleBoundary
        static boolean isScoped(String name) {
            return name.contains("::");
        }

        protected int getLimit() {
            return getLanguage().options.CONSTANT_CACHE;
        }
    }

    @CoreMethod(names = "const_missing", required = 1)
    public abstract static class ConstMissingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object constMissing(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            throw new RaiseException(getContext(), coreExceptions().nameErrorUninitializedConstant(module, name, this));
        }

    }

    @CoreMethod(names = "const_source_location", required = 1, optional = 1)
    public abstract static class ModuleConstSourceLocationNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object constSourceLocation(RubyModule module, Object nameObject, Object maybeInherit,
                @Cached ConstSourceLocationNode constSourceLocationNode,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached ToStringOrSymbolNode toStringOrSymbolNode) {
            final boolean inherit = booleanCastWithDefaultNode.execute(this, maybeInherit, true);
            final var name = toStringOrSymbolNode.execute(this, nameObject);
            return constSourceLocationNode.execute(this, module, name, inherit);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class ConstSourceLocationNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubyModule module, Object name, boolean inherit);

        @Specialization(guards = "strings.isRubyString(this, name)", limit = "1")
        @TruffleBoundary
        static Object constSourceLocation(Node node, RubyModule module, Object name, boolean inherit,
                @Cached(inline = false) @Shared TruffleString.FromJavaStringNode fromJavaStringNode,
                @Cached RubyStringLibrary strings) {
            final ConstantLookupResult lookupResult = ModuleOperations
                    .lookupScopedConstant(getContext(node), module, StringOperations.getJavaString(name), inherit, node,
                            true);

            return getLocation(node, lookupResult, fromJavaStringNode);
        }

        @Specialization
        @TruffleBoundary
        static Object constSourceLocation(Node node, RubyModule module, RubySymbol name, boolean inherit,
                @Cached(inline = false) @Shared TruffleString.FromJavaStringNode fromJavaStringNode) {
            final ConstantLookupResult lookupResult = ModuleOperations
                    .lookupConstantWithInherit(getContext(node), module, name.getString(), inherit, node, true);

            return getLocation(node, lookupResult, fromJavaStringNode);
        }

        private static Object getLocation(Node node, ConstantLookupResult lookupResult,
                TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (!lookupResult.isFound()) {
                return nil;
            }

            final SourceSection sourceSection = lookupResult.getConstant().getSourceSection();
            if (!BacktraceFormatter.isAvailable(sourceSection)) {
                return createEmptyArray(node);
            } else {
                return getLanguage(node).rubySourceLocation(sourceSection, fromJavaStringNode, node);
            }
        }

    }

    @CoreMethod(names = "const_set", required = 2)
    public abstract static class ConstSetNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object setConstant(RubyModule module, Object nameObject, Object value,
                @Cached ConstSetUncheckedNode uncheckedSetNode,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            if (!Identifiers.isValidConstantName(name)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions()
                                .nameError(StringUtils.format("wrong constant name %s", name), module, name, this));
            }

            return uncheckedSetNode.execute(module, name, value);
        }
    }

    public abstract static class ConstSetUncheckedNode extends RubyBaseNode {

        @Child private WarnAlreadyInitializedNode warnAlreadyInitializedNode;

        public abstract Object execute(RubyModule module, String name, Object value);

        @TruffleBoundary
        @Specialization
        Object setConstantNoCheckName(RubyModule module, String name, Object value) {
            final RubyConstant previous = module.fields.setConstant(getContext(), this, name, value);
            if (previous != null && previous.hasValue()) {
                warnAlreadyInitializedConstant(module, name, previous.getSourceSection());
            }
            return value;
        }

        private void warnAlreadyInitializedConstant(RubyModule module, String name,
                SourceSection previousSourceSection) {
            if (warnAlreadyInitializedNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                warnAlreadyInitializedNode = insert(new WarnAlreadyInitializedNode());
            }

            if (warnAlreadyInitializedNode.shouldWarn()) {
                final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
                warnAlreadyInitializedNode.warnAlreadyInitialized(module, name, sourceSection, previousSourceSection);
            }
        }

    }

    @GenerateUncached
    @CoreMethod(
            names = "define_method",
            needsBlock = true,
            required = 1,
            optional = 1,
            argumentNames = { "name", "proc_or_method", "block" },
            alwaysInlined = true)
    @ImportStatic(RubyArguments.class)
    public abstract static class DefineMethodNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = { "isMethodParameterProvided(rubyArgs)", "isRubyMethod(getArgument(rubyArgs, 1))" })
        RubySymbol defineMethodWithMethod(
                Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String name = nameToJavaStringNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            final Object method = RubyArguments.getArgument(rubyArgs, 1);

            needCallerFrame(callerFrame, target);
            return addMethod(module, name, (RubyMethod) method, callerFrame.materialize());
        }

        @Specialization(guards = { "isMethodParameterProvided(rubyArgs)", "isRubyProc(getArgument(rubyArgs, 1))" })
        RubySymbol defineMethodWithProc(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String name = nameToJavaStringNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            final Object method = RubyArguments.getArgument(rubyArgs, 1);

            needCallerFrame(callerFrame, target);
            return addProc(module, name, (RubyProc) method, callerFrame.materialize());
        }

        @Specialization(
                guards = { "isMethodParameterProvided(rubyArgs)", "isRubyUnboundMethod(getArgument(rubyArgs, 1))" })
        RubySymbol defineMethodWithUnboundMethod(
                Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String name = nameToJavaStringNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            final Object method = RubyArguments.getArgument(rubyArgs, 1);

            needCallerFrame(callerFrame, target);
            return addUnboundMethod(module, name, (RubyUnboundMethod) method, callerFrame.materialize());
        }

        @Specialization(guards = {
                "isMethodParameterProvided(rubyArgs)",
                "!isExpectedMethodParameterType(getArgument(rubyArgs, 1))" })
        RubySymbol defineMethodWithUnexpectedMethodParameterType(
                Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target) {
            final Object method = RubyArguments.getArgument(rubyArgs, 1);
            throw new RaiseException(getContext(),
                    coreExceptions().typeErrorExpectedProcOrMethodOrUnboundMethod(method, this));
        }

        @Specialization(guards = { "!isMethodParameterProvided(rubyArgs)", "isBlockProvided(rubyArgs)" })
        RubySymbol defineMethodWithBlock(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String name = nameToJavaStringNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            final Object block = RubyArguments.getBlock(rubyArgs);

            needCallerFrame(callerFrame, target);
            return addProc(module, name, (RubyProc) block, callerFrame.materialize());
        }

        @Specialization(guards = { "!isMethodParameterProvided(rubyArgs)", "!isBlockProvided(rubyArgs)" })
        RubySymbol defineMethodWithoutMethodAndBlock(
                Frame callerFrame, RubyModule nodule, Object[] rubyArgs, RootCallTarget target) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorProcWithoutBlock(this));
        }

        @TruffleBoundary
        private RubySymbol addMethod(RubyModule module, String name, RubyMethod method,
                MaterializedFrame callerFrame) {
            final InternalMethod internalMethod = method.method;

            if (!ModuleOperations.canBindMethodTo(internalMethod, module)) {
                final RubyModule declaringModule = internalMethod.getDeclaringModule();
                if (RubyGuards.isSingletonClass(declaringModule)) {
                    throw new RaiseException(getContext(), coreExceptions().typeError(
                            "can't bind singleton method to a different class",
                            this));
                } else {
                    throw new RaiseException(getContext(), coreExceptions().typeError(
                            "class must be a subclass of " + declaringModule.fields.getName(),
                            this));
                }
            }

            return addInternalMethod(module, name, internalMethod, callerFrame);
        }

        @TruffleBoundary
        private RubySymbol addUnboundMethod(RubyModule module, String name, RubyUnboundMethod method,
                MaterializedFrame callerFrame) {
            final InternalMethod internalMethod = method.method;
            if (!ModuleOperations.canBindMethodTo(internalMethod, module)) {
                final RubyModule declaringModule = internalMethod.getDeclaringModule();
                if (RubyGuards.isSingletonClass(declaringModule)) {
                    throw new RaiseException(getContext(), coreExceptions().typeError(
                            "can't bind singleton method to a different class",
                            this));
                } else {
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().typeError(
                                    "bind argument must be a subclass of " +
                                            declaringModule.fields.getName(),
                                    this));
                }
            }

            return addInternalMethod(module, name, internalMethod, callerFrame);
        }

        @TruffleBoundary
        private RubySymbol addProc(RubyModule module, String name, RubyProc proc, MaterializedFrame callerFrame) {
            final RootCallTarget callTargetForLambda = proc.callTargets.getCallTargetForLambda();
            final RubyLambdaRootNode rootNode = RubyLambdaRootNode.of(callTargetForLambda);
            final SharedMethodInfo info = proc.getSharedMethodInfo().forDefineMethod(module, name, proc);
            final RubyNode body = rootNode.copyBody();
            final RubyNode newBody = new CallMethodWithLambdaBody(isSingleContext() ? proc : null,
                    callTargetForLambda, body);

            final RubyLambdaRootNode newRootNode = rootNode.copyRootNode(info, newBody);
            final RootCallTarget newCallTarget = newRootNode.getCallTarget();

            final InternalMethod internalMethod = InternalMethod.fromProc(
                    getContext(),
                    info,
                    proc.declarationContext,
                    name,
                    module,
                    Visibility.PUBLIC,
                    proc,
                    newCallTarget);
            return addInternalMethod(module, name, internalMethod, callerFrame);
        }

        private static final class CallMethodWithLambdaBody extends RubyContextSourceNode {

            private final RubyProc proc;
            private final RootCallTarget lambdaCallTarget;
            @Child private RubyNode lambdaBody;

            public CallMethodWithLambdaBody(RubyProc proc, RootCallTarget lambdaCallTarget, RubyNode lambdaBody) {
                this.proc = proc;
                this.lambdaCallTarget = lambdaCallTarget;
                this.lambdaBody = lambdaBody;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final RubyProc proc;
                if (this.proc == null) {
                    proc = RubyArguments.getMethod(frame).getProc();
                    assert proc.callTargets.getCallTargetForLambda() == lambdaCallTarget;
                } else {
                    assert RubyArguments.getMethod(frame).getProc() == this.proc;
                    proc = this.proc;
                }

                RubyArguments.setDeclarationFrame(frame, proc.declarationFrame);
                return lambdaBody.execute(frame);
            }

            @Override
            public RubyNode cloneUninitialized() {
                var copy = new CallMethodWithLambdaBody(
                        proc,
                        lambdaCallTarget,
                        lambdaBody.cloneUninitialized());
                return copy.copyFlags(this);
            }

        }

        @TruffleBoundary
        private RubySymbol addInternalMethod(RubyModule module, String name, InternalMethod method,
                MaterializedFrame callerFrame) {
            method = method.withName(name);

            final Visibility visibility = DeclarationContext
                    .findVisibilityCheckSelfAndDefaultDefinee(module, callerFrame);
            module.addMethodConsiderNameVisibility(getContext(), method.withOwner(module).withDeclaringModule(module),
                    visibility, this);
            return getSymbol(method.getName());
        }

        protected boolean isMethodParameterProvided(Object[] rubyArgs) {
            final int count = RubyArguments.getPositionalArgumentsCount(rubyArgs);
            return count >= 2;
        }

        protected boolean isExpectedMethodParameterType(Object method) {
            return RubyGuards.isRubyMethod(method) || RubyGuards.isRubyUnboundMethod(method) ||
                    RubyGuards.isRubyProc(method);
        }

    }

    @CoreMethod(names = "extend_object", required = 1, visibility = Visibility.PRIVATE)
    public abstract static class ExtendObjectNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule extendObject(RubyModule module, Object object,
                @Cached SingletonClassNode singletonClassNode,
                @Cached InlinedBranchProfile errorProfile) {
            if (module instanceof RubyClass) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeErrorWrongArgumentType(module, "Module", this));
            }

            singletonClassNode.execute(object).fields.include(getContext(), this, module);
            return module;
        }

    }

    @CoreMethod(names = "extended", required = 1, visibility = Visibility.PRIVATE)
    public abstract static class ExtendedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object extended(RubyModule module, Object object) {
            return nil;
        }

    }


    @CoreMethod(names = "initialize", needsBlock = true) // Ideally should not split if no block given
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        public abstract RubyModule executeInitialize(RubyModule module, Object block);

        @Specialization
        RubyModule initialize(RubyModule module, Nil block) {
            return module;
        }

        @Specialization
        RubyModule initialize(RubyModule module, RubyProc block,
                @Cached ClassExecBlockNode classExecBlockNode) {
            classExecBlockNode.execute(this, NoKeywordArgumentsDescriptor.INSTANCE, module, new Object[]{ module },
                    block);
            return module;
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = { "!isRubyClass(self)", "!isRubyClass(from)" })
        Object initializeCopyModule(RubyModule self, RubyModule from,
                @Cached @Shared SingletonClassNode singletonClassNode) {
            self.fields.initCopy(from);

            final RubyClass selfMetaClass = singletonClassNode.execute(self);
            final RubyClass fromMetaClass = singletonClassNode.execute(from);
            selfMetaClass.fields.initCopy(fromMetaClass);

            return nil;
        }

        @Specialization
        Object initializeCopyClass(RubyClass self, RubyClass from,
                @Cached @Shared SingletonClassNode singletonClassNode,
                @Cached InlinedBranchProfile errorProfile) {
            if (from == coreLibrary().basicObjectClass) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().typeError("can't copy the root class", this));
            } else if (from.isSingleton) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().typeError("can't copy singleton class", this));
            }

            self.fields.initCopy(from);

            final RubyClass selfMetaClass = singletonClassNode.execute(self);
            final RubyClass fromMetaClass = from.getMetaClass();

            assert fromMetaClass.isSingleton;
            assert self.getMetaClass().isSingleton;

            selfMetaClass.fields.initCopy(fromMetaClass); // copy class methods

            return nil;
        }

    }

    @CoreMethod(names = "included", needsSelf = false, required = 1, visibility = Visibility.PRIVATE)
    public abstract static class IncludedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object included(Object subclass) {
            return nil;
        }

    }

    @CoreMethod(names = "included_modules")
    public abstract static class IncludedModulesNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray includedModules(RubyModule module) {
            final List<RubyModule> modules = new ArrayList<>();

            for (RubyModule included : module.fields.ancestors()) {
                if (!(included instanceof RubyClass) && included != module) {
                    modules.add(included);
                }
            }

            return createArray(modules.toArray());
        }
    }

    @CoreMethod(names = "method_defined?", required = 1, optional = 1)
    public abstract static class MethodDefinedNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean isMethodDefined(RubyModule module, Object nameObject, Object maybeInherit,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            final var inherit = booleanCastWithDefaultNode.execute(this, maybeInherit, true);
            final InternalMethod method;
            if (inherit) {
                method = ModuleOperations.lookupMethodUncached(module, name, null);
            } else {
                method = module.fields.getMethod(name);
            }

            return method != null && !method.isUndefined() && !(method.getVisibility() == Visibility.PRIVATE);
        }

    }

    @GenerateUncached
    @ImportStatic(RubyArguments.class)
    @CoreMethod(names = "module_function", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class ModuleFunctionNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "names.length == 0")
        Object frame(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names,
                @Cached @Exclusive InlinedBranchProfile errorProfile) {
            checkNotClass(this, module, errorProfile);
            needCallerFrame(callerFrame, "Module#module_function with no arguments");
            DeclarationContext.setCurrentVisibility(callerFrame, Visibility.MODULE_FUNCTION);
            return nil;
        }

        @Specialization(guards = "names.length > 0")
        static Object methods(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names,
                @Cached SetMethodVisibilityNode setMethodVisibilityNode,
                @Cached @Exclusive InlinedBranchProfile errorProfile,
                @Cached InlinedLoopConditionProfile loopProfile,
                @Cached SingleValueCastNode singleValueCastNode,
                @Bind Node node) {
            checkNotClass(node, module, errorProfile);
            int i = 0;
            try {
                for (; loopProfile.inject(node, i < names.length); ++i) {
                    setMethodVisibilityNode.execute(node, module, names[i], Visibility.MODULE_FUNCTION);
                    TruffleSafepoint.poll(node);
                }
            } finally {
                profileAndReportLoopCount(node, loopProfile, i);
            }
            return singleValueCastNode.execute(node, names);
        }

        private static void checkNotClass(Node node, RubyModule module, InlinedBranchProfile errorProfile) {
            if (module instanceof RubyClass) {
                errorProfile.enter(node);
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).typeError("module_function must be called for modules", node));
            }
        }
    }

    @CoreMethod(names = "name")
    public abstract static class NameNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object name(RubyModule module) {
            return module.fields.getRubyStringName();
        }
    }

    @Primitive(name = "caller_nesting")
    public abstract static class CallerNestingNode extends PrimitiveArrayArgumentsNode {
        @Specialization
        RubyArray nesting(
                @Cached NestingNode nestingNode) {
            return nestingNode.execute();
        }
    }

    @CoreMethod(names = "nesting", onSingleton = true)
    public abstract static class NestingNode extends CoreMethodNode {

        public abstract RubyArray execute();

        @TruffleBoundary
        @Specialization
        RubyArray nesting() {
            final List<RubyModule> modules = new ArrayList<>();

            InternalMethod method = getContext().getCallStack().getCallingMethod();
            LexicalScope lexicalScope = method == null ? null : method.getLexicalScope();
            RubyClass objectClass = coreLibrary().objectClass;

            while (lexicalScope != null) {
                final RubyModule enclosing = lexicalScope.getLiveModule();
                if (enclosing == objectClass) {
                    break;
                }
                modules.add(enclosing);
                lexicalScope = lexicalScope.getParent();
            }

            return createArray(modules.toArray());
        }
    }

    @GenerateUncached
    @ImportStatic(RubyArguments.class)
    @CoreMethod(names = "public", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class PublicNode extends AlwaysInlinedMethodNode {
        @Specialization(guards = "names.length == 0")
        Object frame(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names) {
            needCallerFrame(callerFrame, "Module#public with no arguments");
            DeclarationContext.setCurrentVisibility(callerFrame, Visibility.PUBLIC);
            return nil;
        }

        @Specialization(guards = "names.length > 0")
        static Object methods(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names,
                @Cached SetMethodVisibilityNode setMethodVisibilityNode,
                @Cached SingleValueCastNode singleValueCastNode,
                @Bind Node node) {
            for (Object name : names) {
                setMethodVisibilityNode.execute(node, module, name, Visibility.PUBLIC);
            }
            return singleValueCastNode.execute(node, names);
        }
    }

    @CoreMethod(names = "public_class_method", rest = true, split = Split.NEVER)
    public abstract static class PublicClassMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        RubyModule publicClassMethod(RubyModule module, Object[] names) {
            final RubyClass singletonClass = org.truffleruby.language.objects.SingletonClassNodeGen.getUncached()
                    .execute(module);

            for (Object name : names) {
                ModuleNodesFactory.SetMethodVisibilityNodeGen.getUncached().execute(this, singletonClass, name,
                        Visibility.PUBLIC);
            }

            return module;
        }
    }

    @GenerateUncached
    @ImportStatic(RubyArguments.class)
    @CoreMethod(names = "private", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class PrivateNode extends AlwaysInlinedMethodNode {
        @Specialization(guards = "names.length == 0")
        Object frame(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names) {
            needCallerFrame(callerFrame, "Module#private with no arguments");
            DeclarationContext.setCurrentVisibility(callerFrame, Visibility.PRIVATE);
            return nil;
        }

        @Specialization(guards = "names.length > 0")
        static Object methods(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names,
                @Cached SetMethodVisibilityNode setMethodVisibilityNode,
                @Cached SingleValueCastNode singleValueCastNode,
                @Bind Node node) {
            for (Object name : names) {
                setMethodVisibilityNode.execute(node, module, name, Visibility.PRIVATE);
            }
            return singleValueCastNode.execute(node, names);
        }
    }

    @CoreMethod(names = "prepend_features", required = 1, visibility = Visibility.PRIVATE, split = Split.NEVER)
    public abstract static class PrependFeaturesNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object prependFeatures(RubyModule features, RubyModule target,
                @Cached InlinedBranchProfile errorProfile) {
            if (features instanceof RubyClass) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeError("prepend_features must be called only on modules", this));
            }
            target.fields.prepend(getContext(), this, features);
            return nil;
        }
    }

    @CoreMethod(names = "private_class_method", rest = true, split = Split.NEVER)
    public abstract static class PrivateClassMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        RubyModule privateClassMethod(RubyModule module, Object[] names) {
            final RubyClass singletonClass = org.truffleruby.language.objects.SingletonClassNodeGen.getUncached()
                    .execute(module);

            for (Object name : names) {
                ModuleNodesFactory.SetMethodVisibilityNodeGen.getUncached().execute(this, singletonClass, name,
                        Visibility.PRIVATE);
            }

            return module;
        }
    }

    @CoreMethod(names = "public_instance_method", required = 1)
    public abstract static class PublicInstanceMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyUnboundMethod publicInstanceMethod(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached InlinedBranchProfile errorProfile) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            // TODO(CS, 11-Jan-15) cache this lookup
            final InternalMethod method = ModuleOperations.lookupMethodUncached(module, name, null);

            if (method == null || method.isUndefined()) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().nameErrorUndefinedMethod(name, module, this));
            } else if (method.getVisibility() != Visibility.PUBLIC) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().nameErrorPrivateMethod(name, module, this));
            }

            final RubyUnboundMethod instance = new RubyUnboundMethod(
                    coreLibrary().unboundMethodClass,
                    getLanguage().unboundMethodShape,
                    module,
                    method);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    protected abstract static class AbstractInstanceMethodsNode extends CoreMethodArrayArgumentsNode {

        final Visibility visibility;

        public AbstractInstanceMethodsNode(Visibility visibility) {
            this.visibility = visibility;
        }

        @Specialization
        @TruffleBoundary
        RubyArray getInstanceMethods(RubyModule module, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode) {
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);
            Object[] objects = module.fields
                    .filterMethods(getLanguage(), includeAncestors, MethodFilter.by(visibility))
                    .toArray();
            return createArray(objects);
        }

    }

    @CoreMethod(names = "public_instance_methods", optional = 1)
    public abstract static class PublicInstanceMethodsNode extends AbstractInstanceMethodsNode {

        public PublicInstanceMethodsNode() {
            super(Visibility.PUBLIC);
        }

    }

    @CoreMethod(names = "protected_instance_methods", optional = 1)
    public abstract static class ProtectedInstanceMethodsNode extends AbstractInstanceMethodsNode {

        public ProtectedInstanceMethodsNode() {
            super(Visibility.PROTECTED);
        }

    }

    @CoreMethod(names = "private_instance_methods", optional = 1)
    public abstract static class PrivateInstanceMethodsNode extends AbstractInstanceMethodsNode {

        public PrivateInstanceMethodsNode() {
            super(Visibility.PRIVATE);
        }

    }


    protected abstract static class AbstractMethodDefinedNode extends CoreMethodArrayArgumentsNode {

        final Visibility visibility;

        public AbstractMethodDefinedNode(Visibility visibility) {
            this.visibility = visibility;
        }

        // NOTE(norswap): We considered caching the lookup here, but determined that the resulting complexity
        //   increase in LookupMethodNode wasn't worth it, as it would slow down the more common cases.
        @Specialization
        boolean isMethodDefined(RubyModule module, Object nameObject, Object maybeInheritObject,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached InlinedConditionProfile inheritProfile) {
            final var inherit = booleanCastWithDefaultNode.execute(this, maybeInheritObject, true);
            final var name = nameToJavaStringNode.execute(this, nameObject);
            final InternalMethod method;
            if (inheritProfile.profile(this, inherit)) {
                method = ModuleOperations.lookupMethodUncached(module, name, null);
            } else {
                method = module.fields.getMethod(name);
            }

            return method != null && !method.isUndefined() && !method.isUnimplemented() &&
                    method.getVisibility() == visibility;
        }
    }

    @CoreMethod(names = "public_method_defined?", required = 1, optional = 1)
    public abstract static class PublicMethodDefinedNode extends AbstractMethodDefinedNode {
        public PublicMethodDefinedNode() {
            super(Visibility.PUBLIC);
        }
    }

    @CoreMethod(names = "protected_method_defined?", required = 1, optional = 1)
    public abstract static class ProtectedMethodDefinedNode extends AbstractMethodDefinedNode {
        public ProtectedMethodDefinedNode() {
            super(Visibility.PROTECTED);
        }
    }

    @CoreMethod(names = "private_method_defined?", required = 1, optional = 1)
    public abstract static class PrivateMethodDefinedNode extends AbstractMethodDefinedNode {
        public PrivateMethodDefinedNode() {
            super(Visibility.PRIVATE);
        }
    }

    @CoreMethod(names = "instance_methods", optional = 1)
    public abstract static class InstanceMethodsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray instanceMethods(RubyModule module, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode) {
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);
            Object[] objects = module.fields
                    .filterMethods(getLanguage(), includeAncestors, MethodFilter.PUBLIC_PROTECTED)
                    .toArray();
            return createArray(objects);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "instance_method", required = 1, alwaysInlined = true)
    public abstract static class InstanceMethodNode extends AlwaysInlinedMethodNode {

        @Specialization
        RubyUnboundMethod instanceMethod(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached InlinedBranchProfile errorProfile) {
            needCallerFrame(callerFrame, target);
            final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(callerFrame);
            final String name = nameToJavaStringNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));

            // TODO(CS, 11-Jan-15) cache this lookup
            final InternalMethod method = ModuleOperations.lookupMethodUncached(module, name, declarationContext);

            if (method == null || method.isUndefined()) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().nameErrorUndefinedMethod(name, module, this));
            }

            final RubyUnboundMethod instance = new RubyUnboundMethod(
                    coreLibrary().unboundMethodClass,
                    getLanguage().unboundMethodShape,
                    module,
                    method);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "private_constant", rest = true)
    public abstract static class PrivateConstantNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule privateConstant(RubyModule module, Object[] args,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            for (Object arg : args) {
                String name = nameToJavaStringNode.execute(this, arg);
                module.fields.changeConstantVisibility(getContext(), this, name, true);
            }
            return module;
        }
    }

    @CoreMethod(names = "deprecate_constant", rest = true, raiseIfFrozenSelf = true)
    public abstract static class DeprecateConstantNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule deprecateConstant(RubyModule module, Object[] args,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            for (Object arg : args) {
                String name = nameToJavaStringNode.execute(this, arg);
                module.fields.deprecateConstant(getContext(), this, name);
            }
            return module;
        }
    }

    @CoreMethod(names = "public_constant", rest = true)
    public abstract static class PublicConstantNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule publicConstant(RubyModule module, Object[] args,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            for (Object arg : args) {
                String name = nameToJavaStringNode.execute(this, arg);
                module.fields.changeConstantVisibility(getContext(), this, name, false);
            }
            return module;
        }
    }

    @GenerateUncached
    @ImportStatic(RubyArguments.class)
    @CoreMethod(names = "protected", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class ProtectedNode extends AlwaysInlinedMethodNode {
        @Specialization(guards = "names.length == 0")
        Object frame(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names) {
            needCallerFrame(callerFrame, "Module#protected with no arguments");
            DeclarationContext.setCurrentVisibility(callerFrame, Visibility.PROTECTED);
            return nil;
        }

        @Specialization(guards = "names.length > 0")
        static Object methods(Frame callerFrame, RubyModule module, Object[] rubyArgs, RootCallTarget target,
                @Bind("getPositionalArguments(rubyArgs)") Object[] names,
                @Cached SetMethodVisibilityNode setMethodVisibilityNode,
                @Cached SingleValueCastNode singleValueCastNode,
                @Bind Node node) {
            for (Object name : names) {
                setMethodVisibilityNode.execute(node, module, name, Visibility.PROTECTED);
            }
            return singleValueCastNode.execute(node, names);
        }
    }

    @CoreMethod(names = "remove_class_variable", required = 1, split = Split.ALWAYS)
    public abstract static class RemoveClassVariableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object removeClassVariableString(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached CheckClassVariableNameNode checkClassVariableNameNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            checkClassVariableNameNode.execute(this, module, name);
            return ModuleOperations.removeClassVariable(module.fields, getContext(), this, name);
        }

    }

    @Primitive(name = "module_remove_const")
    public abstract static class RemoveConstNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object removeConstant(RubyModule module, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LazyWarnNode lazyWarnNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            final RubyConstant oldConstant = module.fields.removeConstant(getContext(), this, name);
            if (oldConstant == null) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().nameErrorConstantNotDefined(module, name, this));
            } else {
                if (oldConstant.isAutoload() || oldConstant.isUndefined()) {
                    return nil;
                } else {
                    if (oldConstant.isDeprecated()) {
                        warnDeprecatedConstant(module, name, lazyWarnNode);
                    }
                    return oldConstant.getValue();
                }
            }
        }

        private void warnDeprecatedConstant(RubyModule module, String name, LazyWarnNode lazyWarnNode) {
            WarnDeprecatedConstantNode.warnDeprecatedConstant(this, lazyWarnNode.get(this), module, name);
        }
    }

    @CoreMethod(names = "remove_method", rest = true)
    public abstract static class RemoveMethodNode extends CoreMethodArrayArgumentsNode {

        private final BranchProfile errorProfile = BranchProfile.create();

        @Child private DispatchNode methodRemovedNode = DispatchNode.create();

        @Specialization
        RubyModule removeMethods(RubyModule module, Object[] names,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            for (Object name : names) {
                removeMethod(module, nameToJavaStringNode.execute(this, name), raiseIfFrozenNode);
            }
            return module;
        }

        private void removeMethod(RubyModule module, String name, TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            raiseIfFrozenNode.execute(this, module);

            if (module.fields.removeMethod(getContext(), name, this)) {
                if (RubyGuards.isSingletonClass(module)) {
                    final RubyDynamicObject receiver = ((RubyClass) module).attached;
                    methodRemovedNode.call(receiver, "singleton_method_removed", getSymbol(name));
                } else {
                    methodRemovedNode.call(module, "method_removed", getSymbol(name));
                }
            } else {
                errorProfile.enter();
                throw new RaiseException(
                        getContext(),
                        coreExceptions().nameErrorMethodNotDefinedIn(module, name, this));
            }
        }

    }

    @CoreMethod(names = { "to_s", "inspect" })
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyString toS(RubyModule module,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final String moduleName;
            if (module.fields.isRefinement()) {
                moduleName = module.fields.getRefinementName();
            } else {
                moduleName = module.fields.getName();
            }
            return createString(fromJavaStringNode, moduleName, Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "undef_method", rest = true, split = Split.NEVER, argumentNames = "names")
    public abstract static class UndefMethodNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyModule undefMethods(RubyModule module, Object[] names,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            for (Object name : names) {
                module.fields.undefMethod(getLanguage(), getContext(), this, nameToJavaStringNode.execute(this, name));
            }
            return module;
        }
    }

    public static final class UndefNode extends RubyContextSourceNode {

        @Children private final RubyNode[] names;

        public UndefNode(RubyNode[] names) {
            this.names = names;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            var module = RubyArguments.getDeclarationContext(frame).getModuleToDefineMethods();
            for (var nameNode : names) {
                final Object nameObject = nameNode.execute(frame);
                final RubySymbol nameSymbol = (RubySymbol) nameObject;

                module.fields.undefMethod(getLanguage(), getContext(), this, nameSymbol.getString());
            }
            return module;
        }

        @Override
        public RubyNode cloneUninitialized() {
            return new UndefNode(cloneUninitialized(names)).copyFlags(this);
        }

    }

    @CoreMethod(names = "used_modules", onSingleton = true)
    public abstract static class UsedModulesNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray usedModules() {
            final Frame frame = getContext().getCallStack().getCallerFrame(FrameAccess.READ_ONLY);
            final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(frame);
            final Set<RubyModule> refinementNamespaces = new HashSet<>();
            for (RubyModule[] refinementModules : declarationContext.getRefinements().values()) {
                for (RubyModule refinementModule : refinementModules) {
                    refinementNamespaces.add(refinementModule.fields.getRefinementNamespace());
                }
            }
            return createArray(refinementNamespaces.toArray());
        }

    }

    @CoreMethod(names = "used_refinements", onSingleton = true)
    public abstract static class UsedRefinementsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray usedRefinements() {
            final Frame frame = getContext().getCallStack().getCallerFrame(FrameAccess.READ_ONLY);
            final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(frame);
            final List<RubyModule> refinements = new ArrayList<>();
            for (RubyModule[] refinementModules : declarationContext.getRefinements().values()) {
                Collections.addAll(refinements, refinementModules);
            }
            return createArray(refinements.toArray());
        }

    }

    @CoreMethod(names = "refinements")
    public abstract static class RefinementsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray refinements(RubyModule self) {
            return createArray(self.fields.getRefinements().values().toArray());
        }

    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    @ImportStatic(ArrayGuards.class)
    public abstract static class SetMethodVisibilityNode extends RubyBaseNode {

        public abstract void execute(Node node, RubyModule module, Object name, Visibility visibility);

        @TruffleBoundary
        @Specialization(guards = "!isRubyArray(name)")
        static void setMethodVisibility(Node node, RubyModule module, Object name, Visibility visibility,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String methodName = nameToJavaStringNode.execute(node, name);

            final InternalMethod method = module.fields.deepMethodSearch(getContext(node), methodName);

            if (method == null) {
                throw new RaiseException(
                        getContext(node),
                        coreExceptions(node).nameErrorUndefinedMethod(methodName, module, node));
            }

            // Do nothing if the method already exists with the same visibility, like MRI
            if (method.getVisibility() == visibility) {
                return;
            }

            /* If the method was already defined in this class, that's fine {@link addMethod} will overwrite it,
             * otherwise we do actually want to add a copy of the method with a different visibility to this module. */
            module.addMethodIgnoreNameVisibility(getContext(node), method, visibility, node);
        }

        @TruffleBoundary
        @Specialization
        static void setMethodVisibilityArray(Node node, RubyModule module, RubyArray array, Visibility visibility,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            for (Object name : ArrayOperations.toIterable(array)) {
                setMethodVisibility(
                        node,
                        module,
                        name,
                        visibility,
                        nameToJavaStringNode);
                TruffleSafepoint.poll(node);
            }
        }

    }

    @CoreMethod(names = "refine", needsBlock = true, required = 1, visibility = Visibility.PRIVATE, split = Split.NEVER)
    public abstract static class RefineNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule refine(RubyModule self, Object moduleToRefine, Nil block) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("no block given", this));
        }

        @Specialization(guards = "!isRubyModule(moduleToRefine)")
        RubyModule refineNotModule(RubyModule self, Object moduleToRefine, RubyProc block) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeErrorWrongArgumentType(moduleToRefine, "Class or Module", this));
        }

        @TruffleBoundary
        @Specialization
        RubyModule refine(RubyModule namespace, RubyModule moduleToRefine, RubyProc block) {
            final ConcurrentMap<RubyModule, RubyModule> refinements = namespace.fields
                    .getRefinements();
            final RubyModule refinement = ConcurrentOperations
                    .getOrCompute(refinements, moduleToRefine, klass -> newRefinementModule(namespace, moduleToRefine));

            // Apply the existing refinements in this namespace and the new refinement inside the refine block
            final Map<RubyModule, RubyModule[]> refinementsInDeclarationContext = new HashMap<>();
            for (Entry<RubyModule, RubyModule> existingRefinement : refinements.entrySet()) {
                refinementsInDeclarationContext
                        .put(existingRefinement.getKey(), new RubyModule[]{ existingRefinement.getValue() });
            }
            refinementsInDeclarationContext.put(moduleToRefine, new RubyModule[]{ refinement });
            final DeclarationContext declarationContext = new DeclarationContext(
                    Visibility.PUBLIC,
                    new FixedDefaultDefinee(refinement),
                    refinementsInDeclarationContext);

            // Update methods in existing refinements in this namespace to also see this new refine block's refinements
            for (RubyModule existingRefinement : refinements.values()) {
                final ModuleFields fields = existingRefinement.fields;
                for (InternalMethod refinedMethodInExistingRefinement : fields.getMethods()) {
                    fields.addMethod(
                            getContext(),
                            this,
                            refinedMethodInExistingRefinement.withDeclarationContext(declarationContext));
                }
            }

            CallBlockNode.executeUncached(
                    declarationContext,
                    block,
                    refinement,
                    nil,
                    NoKeywordArgumentsDescriptor.INSTANCE,
                    EMPTY_ARGUMENTS);
            return refinement;
        }

        private RubyModule newRefinementModule(RubyModule namespace, RubyModule moduleToRefine) {
            final RubyModule refinement = createModule(
                    getContext(),
                    getEncapsulatingSourceSection(),
                    coreLibrary().refinementClass,
                    null,
                    null,
                    this);
            final ModuleFields refinementFields = refinement.fields;
            refinementFields.setupRefinementModule(moduleToRefine, namespace);
            return refinement;
        }

    }

    @CoreMethod(names = "set_temporary_name", required = 1)
    public abstract static class SetTemporaryNameNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyModule setTemporaryName(RubyModule self, Object name,
                @Cached ToJavaStringNode toJavaStringNode) {
            if (name == nil) {
                self.fields.setTemporaryName(null);
                return self;
            }

            final String string = toJavaStringNode.execute(this, name);
            validateName(string, self);
            self.fields.setTemporaryName(string);

            return self;
        }

        private void validateName(String name, RubyModule self) {
            if (name.isEmpty()) {
                throw new RaiseException(
                        getContext(this),
                        coreExceptions(this).argumentError("empty class/module name", this));
            }

            if (self.fields.hasFullName()) {
                throw new RaiseException(
                        getContext(this),
                        coreExceptions(this).runtimeError("can't change permanent name", this));
            }

            if (Identifiers.isValidConstantPath(name)) {
                throw new RaiseException(
                        getContext(this),
                        coreExceptions(this).argumentError(
                                "the temporary name must not be a constant path to avoid confusion", this));
            }
        }

    }

    @GenerateUncached
    @CoreMethod(names = "using", required = 1, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class ModuleUsingNode extends UsingNode {
        @Specialization
        Object moduleUsing(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached InlinedBranchProfile errorProfile) {
            needCallerFrame(callerFrame, target);
            final Object refinementModule = RubyArguments.getArgument(rubyArgs, 0);
            if (self != RubyArguments.getSelf(callerFrame)) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().runtimeError("Module#using is not called on self", this));
            }
            final InternalMethod callerMethod = RubyArguments.getMethod(callerFrame);
            if (!callerMethod.getSharedMethodInfo().isModuleBody()) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().runtimeError("Module#using is not permitted in methods", this));
            }
            using(callerFrame, refinementModule, errorProfile);
            return self;
        }

    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule allocate(RubyClass rubyClass) {
            return createModule(getContext(), getEncapsulatingSourceSection(), rubyClass, null, null, this);
        }

    }

    @CoreMethod(names = "singleton_class?")
    public abstract static class IsSingletonClassNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "!isRubyClass(rubyModule)")
        Object doModule(RubyModule rubyModule) {
            return false;
        }

        @Specialization
        Object doClass(RubyClass rubyClass) {
            return rubyClass.isSingleton;
        }
    }

    @CoreMethod(names = "undefined_instance_methods")
    public abstract static class UndefinedInstanceMethodsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        RubyArray undefinedInstanceMethods(RubyModule module) {
            List<RubySymbol> methodNames = new ArrayList<>();

            for (InternalMethod methodEntry : module.fields.getMethods()) {
                if (methodEntry.isUndefined()) {
                    methodNames.add(getLanguage().getSymbol(methodEntry.getName()));
                }
            }

            return createArray(methodNames.toArray());
        }
    }
}
