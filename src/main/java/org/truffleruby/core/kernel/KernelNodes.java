/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.kernel;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.AssumedValue;
import org.truffleruby.RubyContext;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.BasicObjectNodes.ObjectIDNode;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.BooleanCastWithDefaultNode;
import org.truffleruby.core.cast.DurationToNanoSecondsNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToStrNode;
import org.truffleruby.core.cast.ToStringOrSymbolNode;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.GetBacktraceException;
import org.truffleruby.core.format.BytesResult;
import org.truffleruby.core.format.FormatEncoding;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.FormatRootNode;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.printf.PrintfSimpleParser;
import org.truffleruby.core.format.printf.PrintfSimpleTreeBuilder;
import org.truffleruby.core.format.printf.SprintfConfig;
import org.truffleruby.core.hash.HashOperations;
import org.truffleruby.core.hash.HashingNodes;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.PackedHashStoreLibrary;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.MethodFilter;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.ProcNodes.ProcNewNode;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.range.RangeNodes;
import org.truffleruby.core.range.RubyIntOrLongRange;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringSupport;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.core.support.TypeNodes.CheckFrozenNode;
import org.truffleruby.core.support.TypeNodes.ObjectInstanceVariablesNode;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolNodes;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.WarnNode;
import org.truffleruby.language.WarningNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import org.truffleruby.language.dispatch.LazyDispatchNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.globals.ReadGlobalVariableNode;
import org.truffleruby.language.loader.EvalLoader;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.RequireNode;
import org.truffleruby.language.locals.FindDeclarationVariableNodes.FindAndReadDeclarationVariableNode;
import org.truffleruby.language.methods.GetMethodObjectNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.CheckIVarNameNode;
import org.truffleruby.language.objects.FreezeNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsCopyableObjectNode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.LazySingletonClassNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.parser.FrameDescriptorInfo;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;

@CoreModule("Kernel")
public abstract class KernelNodes {

    /** Check if operands are the same object or call #==. Known as rb_equal() in MRI. The fact Kernel#=== uses this is
     * pure coincidence. */
    @Primitive(name = "same_or_equal?")
    public abstract static class SameOrEqualPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean doSameOrEqual(Object a, Object b,
                @Cached SameOrEqualNode sameOrEqualNode) {
            return sameOrEqualNode.execute(this, a, b);

        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class SameOrEqualNode extends RubyBaseNode {

        public abstract boolean execute(Node node, Object a, Object b);

        @Specialization
        static boolean sameOrEqual(Node node, Object a, Object b,
                @Cached LazyDispatchNode lazyEqualNode,
                @Cached BooleanCastNode booleanCastNode,
                @Cached InlinedConditionProfile sameProfile,
                @Cached ReferenceEqualNode referenceEqualNode) {
            if (sameProfile.profile(node, referenceEqualNode.execute(node, a, b))) {
                return true;
            } else {
                final var equalNode = lazyEqualNode.get(node);
                return booleanCastNode.execute(node, equalNode.call(a, "==", b));
            }
        }
    }

    @CoreMethod(names = "===", required = 1)
    public abstract static class CaseCompareNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean caseCmp(Object a, Object b,
                @Cached SameOrEqualNode sameOrEqualNode) {
            return sameOrEqualNode.execute(this, a, b);
        }

    }

    /** Check if operands are the same object or call #eql? */
    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    public abstract static class SameOrEqlNode extends RubyBaseNode {

        public static boolean executeUncached(Object a, Object b) {
            return KernelNodesFactory.SameOrEqlNodeGen.getUncached().execute(null, a, b);
        }

        public abstract boolean execute(Node node, Object a, Object b);

        @Specialization
        static boolean refEqualOrEql(Node node, Object a, Object b,
                @Cached ReferenceEqualNode referenceEqual,
                @Cached(inline = false) DispatchNode eql,
                @Cached BooleanCastNode booleanCast) {
            return referenceEqual.execute(node, a, b) || booleanCast.execute(node, eql.call(a, "eql?", b));
        }
    }

    @Primitive(name = "find_file")
    public abstract static class FindFileNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "libFeatureString.isRubyString(this, featureString)", limit = "1")
        static Object findFile(Object featureString,
                @Cached InlinedBranchProfile notFoundProfile,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                @Cached RubyStringLibrary libFeatureString,
                @Cached ToJavaStringNode toJavaStringNode,
                @Bind Node node) {
            String feature = toJavaStringNode.execute(node, featureString);
            final String expandedPath = getContext(node).getFeatureLoader().findFeature(feature);
            if (expandedPath == null) {
                notFoundProfile.enter(node);
                return nil;
            }
            return createString(node, fromJavaStringNode, expandedPath, Encodings.UTF_8);
        }

    }

    @Primitive(name = "get_caller_path")
    public abstract static class GetCallerPathNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "libFeature.isRubyString(node, feature)", limit = "1")
        @TruffleBoundary
        static RubyString getCallerPath(Object feature,
                @Bind Node node,
                @Cached RubyStringLibrary libFeature,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final String featureString = StringOperations.getJavaString(feature);
            final String featurePath;
            if (new File(featureString).isAbsolute()) {
                featurePath = featureString;
            } else {
                final SourceSection sourceSection = getContext(node)
                        .getCallStack()
                        .getCallerNode()
                        .getEncapsulatingSourceSection();
                if (!BacktraceFormatter.isAvailable(sourceSection)) {
                    throw new RaiseException(
                            getContext(node),
                            coreExceptions(node).loadError("cannot infer basepath", featureString, node));
                }

                Source source = sourceSection.getSource();
                String sourcePath = getLanguage(node).getSourcePath(source);
                sourcePath = getContext(node).getFeatureLoader().canonicalize(sourcePath, source);

                featurePath = getContext(node).getFeatureLoader().dirname(sourcePath) + "/" + featureString;
            }

            // Normalize the path like File.expand_path() (e.g., remove "../"), but do not resolve
            // symlinks. MRI does this for #require_relative always, but not for #require, so we
            // need to do it to be compatible in the case the path does not exist, so the
            // LoadError's #path is the same as MRI's.
            return createString(node, fromJavaStringNode, Paths.get(featurePath).normalize().toString(),
                    Encodings.UTF_8);
        }

    }

    @Primitive(name = "load_feature")
    public abstract static class LoadFeatureNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "libFeatureString.isRubyString(this, featureString)", limit = "1")
        static boolean loadFeature(Object featureString, Object expandedPathString,
                @Cached RubyStringLibrary libFeatureString,
                @Cached ToJavaStringNode toJavaStringNode,
                @Cached RequireNode requireNode,
                @Bind Node node) {
            return requireNode.executeRequire(
                    toJavaStringNode.execute(node, featureString),
                    expandedPathString);
        }

    }

    @CoreMethod(names = { "<=>" }, required = 1)
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object compare(Object self, Object other,
                @Cached SameOrEqualNode sameOrEqualNode) {
            if (sameOrEqualNode.execute(this, self, other)) {
                return 0;
            } else {
                return nil;
            }
        }

    }

    @GenerateUncached
    @CoreMethod(names = "binding", isModuleFunction = true, alwaysInlined = true)
    public abstract static class BindingNode extends AlwaysInlinedMethodNode {
        @Specialization
        RubyBinding binding(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached(
                        value = "getAdoptedNode($node).getEncapsulatingSourceSection()",
                        allowUncached = true, neverDefault = false) SourceSection sourceSection) {
            needCallerFrame(callerFrame, target);
            return BindingNodes.createBinding(getContext(), getLanguage(), callerFrame.materialize(), sourceSection);
        }
    }

    @GenerateUncached
    @CoreMethod(names = { "block_given?", "iterator?" }, isModuleFunction = true, alwaysInlined = true)
    public abstract static class BlockGivenNode extends AlwaysInlinedMethodNode {
        @Specialization
        boolean blockGiven(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached FindAndReadDeclarationVariableNode readNode,
                @Cached InlinedConditionProfile blockProfile) {
            needCallerFrame(callerFrame, target);
            return blockProfile.profile(this,
                    readNode.execute(callerFrame, this, TranslatorEnvironment.METHOD_BLOCK_NAME, nil) != nil);
        }
    }

    @CoreMethod(names = "__callee__", isModuleFunction = true)
    public abstract static class CalleeNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol calleeName() {
            // the "called name" of a method.
            return getSymbol(getContext().getCallStack().getCallingMethod().getName());
        }
    }

    @Primitive(name = "canonicalize_path")
    public abstract static class CanonicalizePathNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "strings.isRubyString(node, string)", limit = "1")
        @TruffleBoundary
        static RubyString canonicalPath(Object string,
                @Bind Node node,
                @Cached RubyStringLibrary strings,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            final String expandedPath = getContext(node)
                    .getFeatureLoader()
                    .canonicalize(StringOperations.getJavaString(string), null);
            return createString(node, fromJavaStringNode, expandedPath, Encodings.UTF_8);
        }

    }

    @Primitive(name = "kernel_caller_locations", lowerFixnum = { 0, 1 })
    public abstract static class CallerLocationsNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object callerLocations(int omit, NotProvided length) {
            return innerCallerLocations(omit, GetBacktraceException.UNLIMITED);
        }

        @Specialization
        Object callerLocations(int omit, int length) {
            return innerCallerLocations(omit, length);
        }

        private Object innerCallerLocations(int omit, int length) {
            // Always skip #caller_locations.
            final int omitted = omit + 1;
            final Backtrace backtrace = getContext().getCallStack().getBacktrace(this, omitted);
            return backtrace.getBacktraceLocations(getContext(), getLanguage(), length, this);
        }
    }

    @CoreMethod(names = "class")
    public abstract static class KernelClassNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNode.create();

        @Specialization
        RubyClass getClass(Object self) {
            return classNode.execute(self);
        }

    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ReportPolymorphism // inline cache
    public abstract static class CopyInstanceVariablesNode extends RubyBaseNode {

        public static final DynamicObjectLibrary[] EMPTY_DYNAMIC_OBJECT_LIBRARY_ARRAY = new DynamicObjectLibrary[0];
        public static final PropertyGetter[] EMPTY_PROPERTY_GETTER_ARRAY = new PropertyGetter[0];

        public abstract RubyDynamicObject execute(Node node, RubyDynamicObject newObject, RubyDynamicObject from);

        @ExplodeLoop
        @Specialization(
                guards = { "from.getShape() == cachedShape", "propertyGetters.length <= MAX_EXPLODE_SIZE" },
                limit = "getDynamicObjectCacheLimit()")
        static RubyDynamicObject copyCached(RubyDynamicObject newObject, RubyDynamicObject from,
                @Cached("from.getShape()") Shape cachedShape,
                @Cached(value = "getCopiedProperties(cachedShape)", dimensions = 1) PropertyGetter[] propertyGetters,
                @Cached("createWriteFieldNodes(propertyGetters)") DynamicObjectLibrary[] writeFieldNodes) {
            for (int i = 0; i < propertyGetters.length; i++) {
                final PropertyGetter propertyGetter = propertyGetters[i];
                final Object value = propertyGetter.get(from);
                writeFieldNodes[i].putWithFlags(newObject, propertyGetter.getKey(), value, propertyGetter.getFlags());
            }

            return newObject;
        }

        @Specialization(replaces = "copyCached")
        static RubyDynamicObject copyUncached(RubyDynamicObject newObject, RubyDynamicObject from) {
            copyInstanceVariables(from, newObject);
            return newObject;
        }

        protected static PropertyGetter[] getCopiedProperties(Shape shape) {
            final List<PropertyGetter> copiedProperties = new ArrayList<>();

            for (Property property : shape.getProperties()) {
                if (property.getKey() instanceof String) {
                    copiedProperties.add(Objects.requireNonNull(shape.makePropertyGetter(property.getKey())));
                }
            }

            return copiedProperties.toArray(EMPTY_PROPERTY_GETTER_ARRAY);
        }

        protected DynamicObjectLibrary[] createWriteFieldNodes(PropertyGetter[] propertyGetters) {
            if (propertyGetters.length == 0) {
                return EMPTY_DYNAMIC_OBJECT_LIBRARY_ARRAY;
            }

            final DynamicObjectLibrary[] nodes = new DynamicObjectLibrary[propertyGetters.length];
            for (int i = 0; i < propertyGetters.length; i++) {
                nodes[i] = DynamicObjectLibrary.getFactory().createDispatched(1);
            }
            return nodes;
        }

        @TruffleBoundary
        private static void copyInstanceVariables(RubyDynamicObject from, RubyDynamicObject to) {
            // Concurrency: OK if callers create the object and publish it after copy
            // Only copy user-level instance variables, hidden ones are initialized later with #initialize_copy.
            Shape shape = from.getShape();
            for (PropertyGetter propertyGetter : getCopiedProperties(shape)) {
                DynamicObjectLibrary.getUncached().putWithFlags(
                        to,
                        propertyGetter.getKey(),
                        propertyGetter.get(from),
                        propertyGetter.getFlags());
            }
        }
    }

    @GenerateUncached
    public abstract static class CopyNode extends RubyBaseNode {

        public abstract RubyDynamicObject executeCopy(Object self);

        @Specialization(guards = "!isRubyClass(self)")
        RubyDynamicObject copyRubyDynamicObject(RubyDynamicObject self,
                @Cached DispatchNode allocateNode,
                @Cached @Exclusive CopyInstanceVariablesNode copyInstanceVariablesNode) {
            var newObject = (RubyDynamicObject) allocateNode.call(self.getLogicalClass(), "__allocate__");
            copyInstanceVariablesNode.execute(this, newObject, self);
            return newObject;
        }

        @Specialization
        RubyDynamicObject copyRubyClass(RubyClass self,
                @Cached @Exclusive CopyInstanceVariablesNode copyInstanceVariablesNode,
                @Cached InlinedBranchProfile rootClassProfile) {
            if (self == coreLibrary().basicObjectClass) {
                rootClassProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().typeError("can't copy the root class", this));
            }
            var newClass = new RubyClass(coreLibrary().classClass, getLanguage(), getEncapsulatingSourceSection(),
                    null, null, false, null, self.superclass);
            copyInstanceVariablesNode.execute(this, newClass, self);
            return newClass;
        }

        @Specialization
        RubyDynamicObject copy(ImmutableRubyString string,
                @Cached StringNodes.AllocateNode allocateStringNode) {
            return allocateStringNode.execute(this, coreLibrary().stringClass);
        }

        @Specialization
        RubyDynamicObject copy(RubyIntOrLongRange range,
                @Cached RangeNodes.AllocateNode allocateRangeNode) {
            return allocateRangeNode.execute(this, coreLibrary().rangeClass);
        }
    }

    @Primitive(name = "kernel_clone") // "clone"
    public abstract static class CloneNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isCopyableObjectNode.execute(object)")
        static RubyDynamicObject copyable(Object object, Object freeze,
                @Cached @Shared IsCopyableObjectNode isCopyableObjectNode,
                @Cached MetaClassNode metaClassNode,
                @Cached CopyNode copyNode,
                @Cached DispatchNode initializeCloneNode,
                @Cached InlinedConditionProfile isSingletonProfile,
                @Cached HashingNodes.ToHashByHashCode hashNode,
                @Cached IsFrozenNode isFrozenNode,
                @Cached FreezeNode freezeNode,
                @Cached LazySingletonClassNode lazySingletonClassNode,
                @Bind Node node) {
            final RubyDynamicObject newObject = copyNode.executeCopy(object);

            // Copy the singleton class if any.
            final RubyClass selfMetaClass = metaClassNode.execute(node, object);
            if (isSingletonProfile.profile(node, selfMetaClass.isSingleton)) {
                final RubyClass newObjectMetaClass = lazySingletonClassNode.get(node).execute(newObject);
                newObjectMetaClass.fields.initCopy(selfMetaClass);
            }

            final boolean copyFrozen = freeze instanceof Nil;

            if (copyFrozen) {
                initializeCloneNode.call(newObject, "initialize_clone", object);
            } else {
                // pass :freeze keyword argument to #initialize_clone
                final RubyHash keywordArguments = createFreezeBooleanHash(node, (boolean) freeze, hashNode);
                initializeCloneNode.callWithKeywords(newObject, "initialize_clone", object, keywordArguments);
            }

            // Default behavior - is just to copy the frozen state of the original object
            if (forceFrozen(freeze) || (copyFrozen && isFrozenNode.execute(object))) { // Profiled through lazy usage of rubyLibraryFreeze
                freezeNode.execute(node, newObject);
            }

            return newObject;
        }

        @Specialization(guards = "!isCopyableObjectNode.execute(object)")
        Object notCopyable(Object object, Object freeze,
                @Cached @Shared IsCopyableObjectNode isCopyableObjectNode,
                @Cached InlinedBranchProfile cantUnfreezeErrorProfile) {
            if (forceNotFrozen(freeze)) {
                raiseCantUnfreezeError(cantUnfreezeErrorProfile, object);
            }
            return object;
        }

        private static RubyHash createFreezeBooleanHash(Node node, boolean freeze,
                HashingNodes.ToHashByHashCode hashNode) {
            final RubySymbol key = coreSymbols(node).FREEZE;

            final Object[] newStore = PackedHashStoreLibrary.createStore();
            final int hashed = hashNode.execute(node, key);
            PackedHashStoreLibrary.setHashedKeyValue(newStore, 0, hashed, key, freeze);

            return new RubyHash(coreLibrary(node).hashClass, getLanguage(node).hashShape, getContext(node), newStore, 1,
                    false);
        }

        private static boolean forceFrozen(Object freeze) {
            return freeze instanceof Boolean && (boolean) freeze;

        }

        private boolean forceNotFrozen(Object freeze) {
            return freeze instanceof Boolean && !(boolean) freeze;
        }

        private void raiseCantUnfreezeError(InlinedBranchProfile cantUnfreezeErrorProfile, Object object) {
            cantUnfreezeErrorProfile.enter(this);
            throw new RaiseException(getContext(), coreExceptions().argumentErrorCantUnfreeze(object, this));
        }
    }

    // Worth always splitting to have monomorphic #__allocate__, Shape, #initialize_dup and #initialize_copy.
    // OK to always inline as the graph is likely to be simplified a lot as the allocation is visible,
    // and a non-inlined call to #__allocate__ would allocate the arguments Object[] which is about the same number of
    // nodes as the object allocation. Also avoids many frame and Object[] allocations when dup'ing a new object.
    @GenerateUncached
    @CoreMethod(names = "dup", alwaysInlined = true)
    public abstract static class DupNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object dup(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached IsCopyableObjectNode isCopyableObjectNode,
                @Cached InlinedConditionProfile isCopyableProfile,
                @Cached CopyNode copyNode,
                @Cached DispatchNode initializeDupNode) {
            if (isCopyableProfile.profile(this, isCopyableObjectNode.execute(self))) {
                final RubyDynamicObject copy = copyNode.executeCopy(self);

                initializeDupNode.call(copy, "initialize_dup", self);

                return copy;
            } else {
                return self;
            }
        }
    }

    @NodeChild(value = "selfNode", type = RubyNode.class)
    @GenerateNodeFactory
    public abstract static class DupASTNode extends RubyContextSourceNode {

        public static DupASTNode create(RubyNode selfNode) {
            return KernelNodesFactory.DupASTNodeFactory.create(selfNode);
        }

        @Specialization
        Object dupAST(VirtualFrame frame, Object self,
                @Cached DupNode dupNode) {
            return dupNode.execute(frame, self, ArrayUtils.EMPTY_ARRAY, null);
        }

        abstract RubyNode getSelfNode();

        @Override
        public RubyNode cloneUninitialized() {
            var copy = create(getSelfNode().cloneUninitialized());
            return copy.copyFlags(this);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "eval", isModuleFunction = true, required = 1, optional = 3, alwaysInlined = true)
    public abstract static class EvalPrepareArgsNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object eval(Frame callerFrame, Object callerSelf, Object[] rubyArgs, RootCallTarget target,
                @Cached ToStrNode toStrNode,
                @Cached ToIntNode toIntNode,
                @Cached InlinedBranchProfile errorProfile,
                @Cached InlinedConditionProfile hasBindingArgument,
                @Cached EvalInternalNode evalInternalNode,
                @Cached InlinedConditionProfile fileAndLineProfile,
                @Cached InlinedConditionProfile fileNoLineProfile) {

            final Object[] args = RubyArguments.getPositionalArguments(rubyArgs);
            final Object source = toStrNode.execute(this, args[0]);

            final RubyBinding binding;
            final Object self;
            if (hasBindingArgument.profile(this, args.length > 1 && args[1] != nil)) {
                final Object bindingArg = args[1];
                if (!(bindingArg instanceof RubyBinding)) {
                    errorProfile.enter(this);
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().typeErrorWrongArgumentType(bindingArg, "binding", this));
                }
                binding = (RubyBinding) bindingArg;
                self = RubyArguments.getSelf(binding.getFrame());
            } else {
                needCallerFrame(callerFrame, "Kernel#eval with no Binding argument");
                binding = BindingNodes.createBinding(getContext(), getLanguage(), callerFrame.materialize());
                self = callerSelf;
            }

            final Object file;
            final int line;

            if (fileAndLineProfile.profile(this, args.length > 3 && args[3] != nil)) {
                line = toIntNode.execute(args[3]);
                file = toStrNode.execute(this, args[2]);
            } else if (fileNoLineProfile.profile(this, args.length > 2 && args[2] != nil)) {
                file = toStrNode.execute(this, args[2]);
                line = 1;
            } else {
                file = coreStrings().EVAL_FILENAME_STRING.createInstance(getContext());
                line = 1;
            }

            return evalInternalNode.execute(self, source, binding, file, line);
        }
    }

    @ReportPolymorphism // inline cache
    @GenerateUncached
    @ImportStatic(StringOperations.class)
    public abstract static class EvalInternalNode extends RubyBaseNode {

        public abstract Object execute(Object self, Object source, RubyBinding binding, Object file, int line);

        @Specialization(
                guards = {
                        "codeEqualNode.execute(node, libSource, source, cachedSource, cachedSourceEnc)",
                        "fileEqualNode.execute(libFile, file, cachedFile, cachedFileEnc)",
                        "line == cachedLine",
                        "bindingDescriptor == getBindingDescriptor(binding)" },
                limit = "getCacheLimit()")
        static Object evalCached(Object self, Object source, RubyBinding binding, Object file, int line,
                @Bind Node node,
                @Cached @Exclusive RubyStringLibrary libSource,
                @Cached @Exclusive RubyStringLibrary libFile,
                @Cached("asTruffleStringUncached(source)") TruffleString cachedSource,
                @Cached("libSource.getEncoding(node, source)") RubyEncoding cachedSourceEnc,
                @Cached("asTruffleStringUncached(file)") TruffleString cachedFile,
                @Cached("libFile.getEncoding(node, file)") RubyEncoding cachedFileEnc,
                @Cached("line") int cachedLine,
                @Cached("getBindingDescriptor(binding)") FrameDescriptor bindingDescriptor,
                @Cached("parse(node, cachedSource, cachedSourceEnc, bindingDescriptor, getJavaString(file), cachedLine)") RootCallTarget callTarget,
                @Cached("assignsNewUserVariables(getDescriptor(callTarget))") boolean assignsNewUserVariables,
                @Cached("create(callTarget)") DirectCallNode callNode,
                @Cached StringHelperNodes.EqualSameEncodingNode codeEqualNode,
                @Cached StringHelperNodes.EqualNode fileEqualNode) {
            Object[] rubyArgs = prepareEvalArgs(node, callTarget, assignsNewUserVariables, self, binding);
            return callNode.call(rubyArgs);
        }

        @Specialization(replaces = "evalCached")
        static Object evalBindingUncached(Object self, Object source, RubyBinding binding, Object file, int line,
                @Bind Node node,
                @Cached IndirectCallNode callNode,
                @Cached @Exclusive RubyStringLibrary libSource,
                @Cached ToJavaStringNode toJavaStringNode) {

            var callTarget = parse(node, libSource.getTString(node, source), libSource.getEncoding(node, source),
                    binding.getFrame().getFrameDescriptor(),
                    toJavaStringNode.execute(node, file), line);
            boolean assignsNewUserVariables = assignsNewUserVariables(getDescriptor(callTarget));

            Object[] rubyArgs = prepareEvalArgs(node, callTarget, assignsNewUserVariables, self, binding);
            return callNode.call(callTarget, rubyArgs);
        }

        private static Object[] prepareEvalArgs(Node node, RootCallTarget callTarget, boolean assignsNewUserVariables,
                Object self,
                RubyBinding binding) {
            final MaterializedFrame parentFrame = Objects.requireNonNull(binding.getFrame());

            Object[] args = assignsNewUserVariables ? new Object[]{ binding } : RubyNode.EMPTY_ARGUMENTS;

            return getContext(node).getCodeLoader().prepareArgs(callTarget,
                    ParserContext.EVAL,
                    RubyArguments.getDeclarationContext(parentFrame),
                    parentFrame,
                    self,
                    RubyArguments.getMethod(parentFrame).getLexicalScope(),
                    args);
        }

        @TruffleBoundary
        protected static RootCallTarget parse(Node node, AbstractTruffleString sourceText, RubyEncoding encoding,
                FrameDescriptor parentDescriptor, String file, int line) {
            //intern() to improve footprint
            final String sourceFile = file.intern();
            final RubySource source = EvalLoader.createEvalSource(getContext(node), sourceText, encoding, "eval",
                    sourceFile, line, node);
            final LexicalScope lexicalScope = FrameDescriptorInfo.of(parentDescriptor).getSharedMethodInfo()
                    .getStaticLexicalScopeOrNull();
            return getContext(node)
                    .getCodeLoader()
                    .parse(source, ParserContext.EVAL, parentDescriptor, lexicalScope, node);
        }

        protected FrameDescriptor getBindingDescriptor(RubyBinding binding) {
            return BindingNodes.getFrameDescriptor(binding);
        }

        protected static FrameDescriptor getDescriptor(RootCallTarget callTarget) {
            return RubyRootNode.of(callTarget).getFrameDescriptor();
        }

        public static boolean assignsNewUserVariables(FrameDescriptor descriptor) {
            return BindingNodes.assignsNewUserVariables(descriptor);
        }

        protected int getCacheLimit() {
            return getLanguage().options.EVAL_CACHE;
        }
    }

    @CoreMethod(names = "freeze")
    public abstract static class KernelFreezeNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object freeze(Object self,
                @Cached TypeNodes.ObjectFreezeNode objectFreezeNode) {
            return objectFreezeNode.execute(self);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "frozen?", alwaysInlined = true)
    public abstract static class KernelFrozenNode extends AlwaysInlinedMethodNode {
        @Specialization
        boolean isFrozen(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached IsFrozenNode isFrozenNode) {
            return isFrozenNode.execute(self);
        }
    }

    /** Keep consistent with {@link org.truffleruby.core.hash.HashingNodes.ToHashByHashCode} */
    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        public static HashNode create() {
            return KernelNodesFactory.HashNodeFactory.create(null);
        }

        public abstract Object execute(Object value);

        @Specialization
        long hashBoolean(boolean value) {
            return HashOperations.hashBoolean(value, getContext(), this);
        }

        @Specialization
        long hashInt(int value) {
            return HashOperations.hashLong(value, getContext(), this);
        }

        @Specialization
        long hashLong(long value) {
            return HashOperations.hashLong(value, getContext(), this);
        }

        @Specialization
        long hashDouble(double value) {
            return HashOperations.hashDouble(value, getContext(), this);
        }

        @Specialization
        long hashBignum(RubyBignum value) {
            return HashOperations.hashBignum(value, getContext(), this);
        }

        @Specialization
        static long hashString(RubyString value,
                @Cached @Exclusive StringHelperNodes.HashStringNode stringHashNode,
                @Bind Node node) {
            return stringHashNode.execute(node, value);
        }

        @Specialization
        static long hashImmutableString(ImmutableRubyString value,
                @Cached @Exclusive StringHelperNodes.HashStringNode stringHashNode,
                @Bind Node node) {
            return stringHashNode.execute(node, value);
        }

        @Specialization
        long hashSymbol(RubySymbol value,
                @Cached SymbolNodes.HashSymbolNode symbolHashNode) {
            return symbolHashNode.execute(this, value);
        }

        // Default hash for Kernel#hash, can be overwritten by defining a #hash method

        @Specialization(guards = { "!isRubyBignum(value)", "!isImmutableRubyString(value)", "!isRubySymbol(value)" })
        int hashImmutableRubyObject(ImmutableRubyObject value) {
            return System.identityHashCode(value);
        }

        @Specialization(guards = "isNotRubyString(value)")
        int hashRubyDynamicObject(RubyDynamicObject value) {
            return System.identityHashCode(value);
        }

        @Specialization(guards = "isForeignObject(value)", limit = "getInteropCacheLimit()")
        static int hashForeign(Object value,
                @CachedLibrary("value") InteropLibrary interop,
                @Cached TranslateInteropExceptionNode translateInteropException,
                @Bind Node node) {
            if (interop.hasIdentity(value)) {
                try {
                    return interop.identityHashCode(value);
                } catch (UnsupportedMessageException e) {
                    throw translateInteropException.execute(node, e);
                }
            } else {
                return System.identityHashCode(value);
            }
        }
    }

    @ImportStatic(RubyArguments.class)
    @GenerateUncached
    @CoreMethod(names = "initialize_copy", required = 1, alwaysInlined = true)
    public abstract static class InitializeCopyNode extends AlwaysInlinedMethodNode {

        @Specialization(guards = "equalNode.execute(this, self, from)")
        Object initializeCopySame(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Bind("getArgument(rubyArgs, 0)") Object from,
                @Cached @Shared ReferenceEqualNode equalNode) {
            return self;
        }

        @Specialization(guards = "!equalNode.execute(this, self, from)")
        Object initializeCopy(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Bind("getArgument(rubyArgs, 0)") Object from,
                @Cached @Shared ReferenceEqualNode equalNode,
                @Cached CheckFrozenNode checkFrozenNode,
                @Cached LogicalClassNode lhsClassNode,
                @Cached LogicalClassNode rhsClassNode,
                @Cached InlinedBranchProfile errorProfile) {
            checkFrozenNode.execute(this, self);

            if (lhsClassNode.execute(self) != rhsClassNode.execute(from)) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeError("initialize_copy should take same class object", this));
            }

            return self;
        }
    }

    @GenerateUncached
    @CoreMethod(names = "initialize_dup", required = 1, alwaysInlined = true)
    public abstract static class InitializeDupNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object initializeDup(Frame callerFrame, RubyDynamicObject self, Object[] rubyArgs, RootCallTarget target,
                @Cached DispatchNode initializeCopyNode) {
            Object from = RubyArguments.getArgument(rubyArgs, 0);
            return initializeCopyNode.call(self, "initialize_copy", from);
        }
    }

    @CoreMethod(names = "instance_of?", required = 1)
    public abstract static class InstanceOfNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNode.create();

        @Specialization
        boolean instanceOf(Object self, RubyModule module) {
            return classNode.execute(self) == module;
        }

    }

    @CoreMethod(names = "instance_variable_defined?", required = 1, split = Split.ALWAYS)
    public abstract static class InstanceVariableDefinedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isInstanceVariableDefined(RubyDynamicObject object, Object name,
                @Cached CheckIVarNameNode checkIVarNameNode,
                @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary,
                @Cached NameToJavaStringNode nameToJavaStringNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            return objectLibrary.containsKey(object, nameString);
        }

        @Fallback
        boolean immutable(Object object, Object name) {
            return false;
        }
    }

    @CoreMethod(names = "instance_variable_get", required = 1, split = Split.ALWAYS)
    public abstract static class InstanceVariableGetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object instanceVariableGetSymbol(RubyDynamicObject object, Object name,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            return objectLibrary.getOrDefault(object, nameString, nil);
        }

        @Fallback
        Object immutable(Object object, Object name,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            return nil;
        }
    }

    @CoreMethod(names = "instance_variable_set", required = 2, split = Split.ALWAYS)
    public abstract static class InstanceVariableSetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object instanceVariableSet(RubyDynamicObject object, Object name, Object value,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @Cached WriteObjectFieldNode writeNode,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            raiseIfFrozenNode.execute(this, object);
            writeNode.execute(this, object, nameString, value);
            return value;
        }

        @Fallback
        Object immutable(Object object, Object name, Object value,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            throw new RaiseException(getContext(), coreExceptions().frozenError(object, this));
        }
    }

    @CoreMethod(names = "remove_instance_variable", required = 1, split = Split.ALWAYS)
    public abstract static class RemoveInstanceVariableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object removeInstanceVariable(RubyDynamicObject object, Object name,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            raiseIfFrozenNode.execute(this, object);
            return removeIVar(object, nameString);
        }

        @Fallback
        Object immutable(Object object, Object name,
                @Cached @Shared CheckIVarNameNode checkIVarNameNode,
                @Cached @Shared NameToJavaStringNode nameToJavaStringNode) {
            final String nameString = nameToJavaStringNode.execute(this, name);
            checkIVarNameNode.execute(this, object, nameString, name);
            throw new RaiseException(getContext(), coreExceptions().frozenError(object, this));
        }

        @TruffleBoundary
        private Object removeIVar(RubyDynamicObject object, String name) {
            final Object value = DynamicObjectLibrary.getUncached().getOrDefault(object, name, nil);

            if (SharedObjects.isShared(object)) {
                synchronized (object) {
                    removeField(object, name);
                }
            } else {
                removeField(object, name);
            }
            return value;
        }

        private void removeField(RubyDynamicObject object, String name) {
            if (!DynamicObjectLibrary.getUncached().removeKey(object, name)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().nameErrorInstanceVariableNotDefined(name, object, this));
            }
        }
    }

    @CoreMethod(names = "instance_variables")
    public abstract static class InstanceVariablesNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray instanceVariables(Object self,
                @Cached ObjectInstanceVariablesNode instanceVariablesNode) {
            return instanceVariablesNode.executeGetIVars(self);
        }
    }

    @Primitive(name = "any_instance_variable?")
    public abstract static class AnyInstanceVariableNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        static boolean any(RubyDynamicObject self,
                @CachedLibrary("self") DynamicObjectLibrary objectLibrary,
                @Cached InlinedConditionProfile noPropertiesProfile,
                @Bind Node node) {
            var shape = objectLibrary.getShape(self);

            if (noPropertiesProfile.profile(node, shape.getPropertyCount() == 0)) {
                return false;
            }

            Object[] keys = objectLibrary.getKeyArray(self);

            for (Object key : keys) {
                if (key instanceof String) {
                    return true;
                }
            }

            return false;
        }

        @Specialization(guards = "!isRubyDynamicObject(self)")
        boolean noVariablesInImmutableObject(Object self) {
            return false;
        }
    }

    @CoreMethod(names = { "is_a?", "kind_of?" }, required = 1)
    public abstract static class KernelIsANode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isA(Object self, RubyModule module,
                @Cached IsANode isANode) {
            return isANode.executeIsA(self, module);
        }

        @Specialization(guards = "!isRubyModule(module)")
        boolean isATypeError(Object self, Object module) {
            throw new RaiseException(getContext(), coreExceptions().typeError("class or module required", this));
        }

    }

    // See InlinedLambdaNode for the fast path
    @CoreMethod(names = "lambda", isModuleFunction = true, needsBlock = true, split = Split.HEURISTIC)
    public abstract static class LambdaNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc lambda(Nil block) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorProcWithoutBlock(this));
        }

        @Specialization(guards = { "isLiteralBlock(block)", "block.isLambda()" })
        RubyProc lambdaFromLambdaBlock(RubyProc block) {
            return block;
        }

        @Specialization(guards = { "isLiteralBlock(block)", "block.isProc()" })
        RubyProc lambdaFromProcBlock(RubyProc block) {
            return ProcOperations.createLambdaFromBlock(getContext(), getLanguage(), block);
        }

        @Specialization(guards = { "!isLiteralBlock(block)", "block.isLambda()" })
        RubyProc lambdaFromExistingLambda(RubyProc block) {
            // If the argument isn't a literal, its original behaviour is preserved only if its a lambda.
            return block;
        }

        @Specialization(guards = { "!isLiteralBlock(block)", "block.isProc()" })
        RubyProc lambdaFromExistingProc(RubyProc block) {
            throw new RaiseException(
                    getContext(),
                    coreExceptions().argumentError("the lambda method requires a literal block", this));
        }

        @TruffleBoundary
        protected boolean isLiteralBlock(RubyProc block) {
            Node callNode = getContext().getCallStack().getCallerNode();
            RubyCallNode rubyCallNode = NodeUtil.findParent(callNode, RubyCallNode.class);
            return rubyCallNode != null && rubyCallNode.hasLiteralBlock();
        }
    }

    @GenerateUncached
    @CoreMethod(names = "local_variables", isModuleFunction = true, alwaysInlined = true)
    public abstract static class KernelLocalVariablesNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object localVariables(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached DispatchNode callLocalVariables) {
            needCallerFrame(callerFrame, target);
            final RubyBinding binding = BindingNodes
                    .createBinding(getContext(), getLanguage(), callerFrame.materialize());
            return callLocalVariables.call(binding, "local_variables");
        }
    }

    @CoreMethod(names = "__method__", isModuleFunction = true)
    public abstract static class MethodNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySymbol methodName() {
            // the "original/definition name" of the method.
            InternalMethod internalMethod = getContext().getCallStack().getCallingMethod();
            return getSymbol(internalMethod.getSharedMethodInfo().getMethodName());
        }

    }

    @GenerateUncached
    @CoreMethod(names = "method", required = 1, alwaysInlined = true)
    public abstract static class MethodNode extends AlwaysInlinedMethodNode {

        @Specialization
        RubyMethod method(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached ToStringOrSymbolNode toStringOrSymbolNode,
                @Cached GetMethodObjectNode getMethodObjectNode) {
            Object name = toStringOrSymbolNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            return getMethodObjectNode.execute(callerFrame, self, name, PRIVATE);
        }

    }

    @CoreMethod(names = "methods", optional = 1)
    public abstract static class KernelMethodsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray doMethods(Object self, Object maybeRegular,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached MethodsNode methodsNode) {
            final boolean regular = booleanCastWithDefaultNode.execute(this, maybeRegular, true);
            return methodsNode.execute(this, self, regular);

        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MethodsNode extends RubyBaseNode {

        public abstract RubyArray execute(Node node, Object self, boolean regular);

        @TruffleBoundary
        @Specialization(guards = "regular")
        static RubyArray methodsRegular(Node node, Object self, boolean regular,
                @Cached MetaClassNode metaClassNode) {
            final RubyModule metaClass = metaClassNode.execute(node, self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getLanguage(node), regular, MethodFilter.PUBLIC_PROTECTED)
                    .toArray();
            return createArray(node, objects);
        }

        @Specialization(guards = "!regular")
        static RubyArray methodsSingleton(Node node, Object self, boolean regular,
                @Cached SingletonMethodsNode singletonMethodsNode) {
            return singletonMethodsNode.execute(node, self, false);
        }

    }

    @CoreMethod(names = "nil?", needsSelf = false)
    public abstract static class IsNilNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isNil() {
            return false;
        }
    }

    // A basic Kernel#p for debugging core, overridden later in kernel.rb
    @NonStandard
    @CoreMethod(names = "p", isModuleFunction = true, required = 1)
    public abstract static class DebugPrintNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object p(VirtualFrame frame, Object value,
                @Cached DispatchNode callInspectNode) {
            Object inspected = callInspectNode.call(value, "inspect");
            print(inspected);
            return value;
        }

        @TruffleBoundary
        private void print(Object inspected) {
            getContext().getEnvOutStream().println(inspected.toString());
        }
    }

    @CoreMethod(names = "private_methods", optional = 1)
    public abstract static class PrivateMethodsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray privateMethods(Object self, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached MetaClassNode metaClassNode) {
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);
            RubyClass metaClass = metaClassNode.execute(this, self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getLanguage(), includeAncestors, MethodFilter.PRIVATE)
                    .toArray();
            return createArray(objects);
        }

    }

    @CoreMethod(names = "proc", isModuleFunction = true, needsBlock = true, split = Split.HEURISTIC)
    public abstract static class ProcNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyProc proc(VirtualFrame frame, Object maybeBlock,
                @Cached ProcNewNode procNewNode) {
            return procNewNode.executeProcNew(frame, coreLibrary().procClass, ArrayUtils.EMPTY_ARRAY, maybeBlock);
        }

    }

    @CoreMethod(names = "protected_methods", optional = 1)
    public abstract static class ProtectedMethodsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray protectedMethods(Object self, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached MetaClassNode metaClassNode) {
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);
            final RubyClass metaClass = metaClassNode.execute(this, self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getLanguage(), includeAncestors, MethodFilter.PROTECTED)
                    .toArray();
            return createArray(objects);
        }

    }

    @GenerateUncached
    @CoreMethod(names = "public_method", required = 1, alwaysInlined = true)
    public abstract static class PublicMethodNode extends AlwaysInlinedMethodNode {

        @Specialization
        RubyMethod method(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached ToStringOrSymbolNode toStringOrSymbolNode,
                @Cached GetMethodObjectNode getMethodObjectNode) {
            Object name = toStringOrSymbolNode.execute(this, RubyArguments.getArgument(rubyArgs, 0));
            return getMethodObjectNode.execute(callerFrame, self, name, PUBLIC);
        }

    }

    @CoreMethod(names = "public_methods", optional = 1)
    public abstract static class PublicMethodsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray publicMethods(Object self, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached MetaClassNode metaClassNode) {
            final RubyModule metaClass = metaClassNode.execute(this, self);
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getLanguage(), includeAncestors, MethodFilter.PUBLIC)
                    .toArray();
            return createArray(objects);
        }

    }

    @GenerateUncached
    @CoreMethod(names = "public_send", needsBlock = true, required = 1, rest = true, alwaysInlined = true)
    public abstract static class PublicSendNode extends AlwaysInlinedMethodNode {

        @Specialization
        Object send(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached DispatchNode dispatchNode,
                @Cached NameToJavaStringNode nameToJavaString) {
            Object name = RubyArguments.getArgument(rubyArgs, 0);
            Object[] newArgs = RubyArguments.repack(rubyArgs, self, 1);
            return dispatchNode.execute(callerFrame, self, nameToJavaString.execute(this, name), newArgs, PUBLIC);
        }

    }

    @ImportStatic(DispatchConfiguration.class)
    @GenerateUncached
    @CoreMethod(names = "respond_to?", required = 1, optional = 1, alwaysInlined = true)
    public abstract static class RespondToNode extends AlwaysInlinedMethodNode {

        public final boolean executeDoesRespondTo(Object self, RubySymbol name, boolean includeProtectedAndPrivate) {
            final Object[] rubyArgs = RubyArguments.allocate(2);
            RubyArguments.setArgument(rubyArgs, 0, name);
            RubyArguments.setArgument(rubyArgs, 1, includeProtectedAndPrivate);
            return (boolean) execute(null, self, rubyArgs, null);
        }

        @Specialization
        boolean doesRespondTo(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached InlinedBranchProfile notSymbolOrStringProfile,
                @Cached ToJavaStringNode toJavaString,
                @Cached ToSymbolNode toSymbolNode,
                @Cached BooleanCastNode castArgumentNode,
                @Cached InlinedConditionProfile ignoreVisibilityProfile,
                @Cached InlinedConditionProfile isTrueProfile,
                @Cached InlinedConditionProfile respondToMissingProfile,
                @Cached(parameters = "PUBLIC") InternalRespondToNode dispatchPublic,
                @Cached InternalRespondToNode dispatchPrivate,
                @Cached InternalRespondToNode dispatchRespondToMissing,
                @Cached DispatchNode respondToMissingNode,
                @Cached BooleanCastNode castMissingResultNode) {
            final Object name = RubyArguments.getArgument(rubyArgs, 0);
            final int nArgs = RubyArguments.getPositionalArgumentsCount(rubyArgs);
            final boolean includeProtectedAndPrivate = nArgs >= 2 &&
                    castArgumentNode.execute(this, RubyArguments.getArgument(rubyArgs, 1));

            if (!RubyGuards.isRubySymbolOrString(name)) {
                notSymbolOrStringProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeErrorIsNotAOrB(self, "symbol", "string", this));
            }

            final String methodName = toJavaString.execute(this, name);
            final boolean found;
            if (ignoreVisibilityProfile.profile(this, includeProtectedAndPrivate)) {
                found = dispatchPrivate.execute(callerFrame, self, methodName);
            } else {
                found = dispatchPublic.execute(callerFrame, self, methodName);
            }

            if (isTrueProfile.profile(this, found)) {
                return true;
            } else if (respondToMissingProfile
                    .profile(this, dispatchRespondToMissing.execute(callerFrame, self, "respond_to_missing?"))) {
                return castMissingResultNode.execute(this, respondToMissingNode.call(self, "respond_to_missing?",
                        toSymbolNode.execute(this, name), includeProtectedAndPrivate));
            } else {
                return false;
            }
        }
    }

    @GenerateUncached
    @CoreMethod(names = "respond_to_missing?", required = 2, alwaysInlined = true)
    public abstract static class RespondToMissingNode extends AlwaysInlinedMethodNode {
        @Specialization
        boolean respondToMissing(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target) {
            return false;
        }
    }

    @CoreMethod(names = "set_trace_func", isModuleFunction = true, required = 1)
    public abstract static class SetTraceFuncNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object setTraceFunc(Nil traceFunc) {
            getContext().getTraceManager().setTraceFunc(null);
            return nil;
        }

        @Specialization
        RubyProc setTraceFunc(RubyProc traceFunc) {
            getContext().getTraceManager().setTraceFunc(traceFunc);
            return traceFunc;
        }
    }

    @CoreMethod(names = "singleton_class")
    public abstract static class SingletonClassMethodNode extends CoreMethodArrayArgumentsNode {

        public abstract RubyClass executeSingletonClass(Object self);

        @Specialization
        RubyClass singletonClass(Object self,
                @Cached SingletonClassNode singletonClassNode) {
            return singletonClassNode.execute(self);
        }

    }

    @CoreMethod(names = "singleton_method", required = 1)
    public abstract static class SingletonMethodNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyMethod singletonMethod(Object self, Object nameObject,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached InlinedBranchProfile errorProfile,
                @Cached InlinedConditionProfile singletonProfile,
                @Cached InlinedConditionProfile methodProfile,
                @Cached MetaClassNode metaClassNode) {
            final var name = nameToJavaStringNode.execute(this, nameObject);
            final RubyClass metaClass = metaClassNode.execute(this, self);

            if (singletonProfile.profile(this, metaClass.isSingleton)) {
                final InternalMethod method = metaClass.fields.getMethod(name);
                if (methodProfile.profile(this, method != null && !method.isUndefined())) {
                    final RubyMethod instance = new RubyMethod(
                            coreLibrary().methodClass,
                            getLanguage().methodShape,
                            self,
                            method);
                    AllocationTracing.trace(instance, this);
                    return instance;
                }
            }

            errorProfile.enter(this);
            throw new RaiseException(
                    getContext(),
                    coreExceptions().nameErrorUndefinedSingletonMethod(name, self, this));
        }

    }

    @CoreMethod(names = "singleton_methods", optional = 1)
    public abstract static class KernelSingletonMethodsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray singletonMethods(Object self, Object maybeIncludeAncestors,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached SingletonMethodsNode singletonMethodsNode) {
            final boolean includeAncestors = booleanCastWithDefaultNode.execute(this, maybeIncludeAncestors, true);
            return singletonMethodsNode.execute(this, self, includeAncestors);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    public abstract static class SingletonMethodsNode extends RubyBaseNode {

        public abstract RubyArray execute(Node node, Object self, boolean includeAncestors);

        @TruffleBoundary
        @Specialization
        static RubyArray singletonMethods(Node node, Object self, boolean includeAncestors,
                @Cached MetaClassNode metaClassNode) {
            final RubyClass metaClass = metaClassNode.execute(node, self);

            if (!metaClass.isSingleton) {
                return createEmptyArray(node);
            }

            Object[] objects = metaClass.fields
                    .filterSingletonMethods(getLanguage(node), includeAncestors, MethodFilter.PUBLIC_PROTECTED)
                    .toArray();
            return createArray(node, objects);
        }

    }

    @Primitive(name = "singleton_methods?")
    public abstract static class HasSingletonMethodsNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean hasSingletonMethods(Object self,
                @Cached MetaClassNode metaClassNode) {
            final RubyClass metaClass = metaClassNode.execute(this, self);

            if (!metaClass.isSingleton) {
                return false;
            }

            return metaClass.fields.anyMethodDefined();
        }

    }

    @CoreMethod(names = "sleep", isModuleFunction = true, optional = 1)
    public abstract static class SleepNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        long sleep(Object maybeDuration,
                @Cached DurationToNanoSecondsNode durationToNanoSecondsNode) {
            long durationInNanos = durationToNanoSecondsNode.execute(this, maybeDuration);
            assert durationInNanos >= 0;

            final RubyThread thread = getLanguage().getCurrentThread();

            // Clear the wakeUp flag, following Ruby semantics:
            // it should only be considered if we are inside the sleep when Thread#{run,wakeup} is called.
            thread.wakeUp.set(false);

            return sleepFor(getContext(), thread, durationInNanos, this);
        }

        @TruffleBoundary
        public static long sleepFor(RubyContext context, RubyThread thread, long durationInNanos,
                Node currentNode) {
            assert durationInNanos >= 0;

            // We want a monotonic clock to measure sleep duration
            final long startInNanos = System.nanoTime();

            context.getThreadManager().runUntilResult(currentNode, () -> {
                final long nowInNanos = System.nanoTime();
                final long sleptInNanos = nowInNanos - startInNanos;

                if (sleptInNanos >= durationInNanos || thread.wakeUp.getAndSet(false)) {
                    return BlockingAction.SUCCESS;
                }

                TimeUnit.NANOSECONDS.sleep(durationInNanos - sleptInNanos);
                return BlockingAction.SUCCESS;
            });

            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startInNanos);
        }

    }

    @CoreMethod(names = { "format", "sprintf" }, isModuleFunction = true, rest = true, required = 1,
            split = Split.ALWAYS)
    public abstract static class SprintfNode extends CoreMethodArrayArgumentsNode {

        static final String GVAR_DEBUG = "$DEBUG";

        @Specialization(guards = "libFormat.isRubyString(this, formatAsString)", limit = "1")
        static RubyString sprintf(VirtualFrame frame, Object format, Object[] arguments,
                @Cached ToStrNode toStrNode,
                @Bind("toStrNode.execute($node, format)") Object formatAsString,
                @Cached(parameters = "GVAR_DEBUG") ReadGlobalVariableNode readDebugGlobalNode,
                @Cached BooleanCastNode booleanCastNode,
                @Cached RubyStringLibrary libFormat,
                @Cached InlinedBranchProfile exceptionProfile,
                @Cached InlinedConditionProfile resizeProfile,
                @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                @Cached SprintfInnerNode sprintfInnerNode,
                @Bind Node node) {
            var tstring = libFormat.getTString(node, formatAsString);
            var encoding = libFormat.getEncoding(node, formatAsString);

            boolean isDebug = booleanCastNode.execute(node, readDebugGlobalNode.execute(frame));

            final BytesResult result;
            try {
                result = sprintfInnerNode.execute(node, tstring, encoding, arguments, isDebug);
            } catch (FormatException e) {
                exceptionProfile.enter(node);
                throw FormatExceptionTranslator.translate(getContext(node), node, e);
            }

            return finishFormat(node, tstring.byteLength(encoding.tencoding), result, resizeProfile, fromByteArrayNode);
        }

        private static RubyString finishFormat(Node node, int formatLength, BytesResult result,
                InlinedConditionProfile resizeProfile, TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] bytes = result.getOutput();

            if (resizeProfile.profile(node, bytes.length != result.getOutputLength())) {
                bytes = Arrays.copyOf(bytes, result.getOutputLength());
            }

            return createString(node, fromByteArrayNode, bytes,
                    result.getEncoding().getEncodingForLength(formatLength));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ReportPolymorphism // inline cache, CallTarget cache
    public abstract static class SprintfInnerNode extends RubyBaseNode {

        public abstract BytesResult execute(Node node, AbstractTruffleString format, RubyEncoding encoding,
                Object[] arguments, boolean isDebug);

        @Specialization(
                guards = {
                        "equalNode.execute(node, format, encoding, cachedFormat, cachedEncoding)",
                        "isDebug == cachedIsDebug",
                        "arguments.length == cachedArgumentsLength" },
                limit = "3")
        static BytesResult formatCached(
                Node node, AbstractTruffleString format, RubyEncoding encoding, Object[] arguments, boolean isDebug,
                @Cached(inline = false) @Shared WarningNode warnNode,
                @Cached("isDebug") boolean cachedIsDebug,
                @Cached("format.asTruffleStringUncached(encoding.tencoding)") TruffleString cachedFormat,
                @Cached("encoding") RubyEncoding cachedEncoding,
                @Cached("arguments.length") int cachedArgumentsLength,
                @Cached(value = "create(compileFormat(cachedFormat, cachedEncoding, arguments, isDebug, warnNode, node))",
                        inline = false) DirectCallNode callPackNode,
                @Cached StringHelperNodes.EqualSameEncodingNode equalNode) {
            return (BytesResult) callPackNode.call(new Object[]{ arguments, arguments.length, null });
        }

        @Specialization(replaces = "formatCached")
        static BytesResult formatUncached(
                Node node, AbstractTruffleString format, RubyEncoding encoding, Object[] arguments, boolean isDebug,
                @Cached(inline = false) @Shared WarningNode warnNode,
                @Cached(inline = false) IndirectCallNode callPackNode) {
            return (BytesResult) callPackNode.call(
                    compileFormat(format, encoding, arguments, isDebug, warnNode, node),
                    new Object[]{ arguments, arguments.length, null });
        }

        @TruffleBoundary
        static RootCallTarget compileFormat(AbstractTruffleString tstring, RubyEncoding encoding, Object[] arguments,
                boolean isDebug, WarningNode warnNode, Node node) {
            try {
                var byteArray = tstring.getInternalByteArrayUncached(encoding.tencoding);

                final PrintfSimpleParser parser = new PrintfSimpleParser(StringSupport.bytesToChars(byteArray),
                        arguments,
                        isDebug);
                final List<SprintfConfig> configs = parser.parse();
                final PrintfSimpleTreeBuilder builder = new PrintfSimpleTreeBuilder(getLanguage(node), configs,
                        encoding);

                if (warnNode.shouldWarn()) {
                    warn(configs, arguments, warnNode, node);
                }

                return new FormatRootNode(
                        getLanguage(node),
                        node.getEncapsulatingSourceSection(),
                        new FormatEncoding(encoding),
                        builder.getNode(),
                        false,
                        false).getCallTarget();
            } catch (InvalidFormatException e) {
                throw new RaiseException(getContext(node), coreExceptions(node).argumentError(e.getMessage(), node));
            }
        }

        private static void warn(List<SprintfConfig> configs, Object[] arguments, WarningNode warnNode, Node node) {
            // don't warn if arguments are passed as a Hash:
            //   format("%<foo>d : %<bar>f", { :foo => 1, :bar => 2 })
            if (arguments.length == 1 && arguments[0] instanceof RubyHash) {
                return;
            }

            int modifiersCount = 0;
            for (var config : configs) {
                if (config.getAbsoluteArgumentIndex() != null) {
                    return; // no warnings if there is an absolute argument index
                }
                if (!config.isLiteral()) {
                    modifiersCount++;
                    if (config.isWidthStar() || config.isPrecisionStar()) {
                        modifiersCount++;
                    }
                }
            }

            if (modifiersCount < arguments.length) {
                warnNode.warningMessage(
                        RubyContext.get(node).getCallStack().getTopMostUserSourceSection(),
                        "too many arguments for format string");
            }
        }
    }

    @CoreMethod(names = "global_variables", isModuleFunction = true)
    public abstract static class KernelGlobalVariablesNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray globalVariables() {
            final String[] keys = coreLibrary().globalVariables.keys();
            final Object[] store = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                store[i] = getSymbol(keys[i]);
            }
            return createArray(store);
        }

    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToHexStringNode extends RubyBaseNode {

        public abstract String execute(Node node, Object value);

        public static String executeUncached(Object value) {
            return KernelNodesFactory.ToHexStringNodeGen.getUncached().execute(null, value);
        }

        @Specialization
        static String toHexString(int value) {
            return toHexString((long) value);
        }

        @TruffleBoundary
        @Specialization
        static String toHexString(long value) {
            return Long.toHexString(value);
        }

        @Specialization
        static String toHexString(RubyBignum value) {
            return BigIntegerOps.toString(value.value, 16);
        }

    }

    @CoreMethod(names = { "to_s", "inspect" }) // Basic #inspect, refined later in core
    public abstract static class KernelToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString toS(Object self,
                @Cached ToSNode toSNode) {
            return toSNode.execute(self);

        }
    }

    // MRI: rb_any_to_s
    @GenerateUncached
    public abstract static class ToSNode extends RubyBaseNode {

        @NeverDefault
        public static ToSNode create() {
            return KernelNodesFactory.ToSNodeGen.create();
        }

        public abstract RubyString execute(Object self);

        @Specialization
        RubyString toS(Object self,
                @Cached LogicalClassNode classNode,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                @Cached ObjectIDNode objectIDNode,
                @Cached ToHexStringNode toHexStringNode) {
            String className = classNode.execute(self).fields.getName();
            Object id = objectIDNode.execute(self);
            String hexID = toHexStringNode.execute(this, id);

            String javaString = Utils.concat("#<", className, ":0x", hexID, ">");

            return createString(
                    fromJavaStringNode,
                    javaString,
                    Encodings.UTF_8);
        }

        @TruffleBoundary
        public static String uncachedBasicToS(Object self) {
            String className = LogicalClassNode.getUncached().execute(self).fields.getName();
            Object id = ObjectIDNode.getUncached().execute(self);
            String hexID = ToHexStringNode.executeUncached(id);

            return "#<" + className + ":0x" + hexID + ">";
        }

        @TruffleBoundary
        public static String uncachedBasicToS(RubyDynamicObject self) {
            String className = self.getLogicalClass().fields.getName();
            long id = ObjectIDNode.getUncached().execute(self);
            String hexID = Long.toHexString(id);

            return "#<" + className + ":0x" + hexID + ">";
        }
    }

    @Primitive(name = "warning_get_category")
    public abstract static class WarningGetCategoryNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "category == coreSymbols().DEPRECATED")
        boolean getCategoryDeprecated(RubySymbol category) {
            return getContext().getWarningCategoryDeprecated().get();
        }

        @Specialization(guards = "category == coreSymbols().EXPERIMENTAL")
        boolean getCategoryExperimental(RubySymbol category) {
            return getContext().getWarningCategoryExperimental().get();
        }

        @Specialization(guards = "category == coreSymbols().PERFORMANCE")
        boolean getCategoryPerformance(RubySymbol category) {
            return getContext().getWarningCategoryPerformance().get();
        }

    }

    @Primitive(name = "warning_set_category")
    public abstract static class WarningSetCategoryNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        boolean setCategory(RubySymbol category, boolean newValue) {
            final AssumedValue<Boolean> existingValue;
            if (category == coreSymbols().DEPRECATED) {
                existingValue = getContext().getWarningCategoryDeprecated();
            } else if (category == coreSymbols().EXPERIMENTAL) {
                existingValue = getContext().getWarningCategoryExperimental();
            } else if (category == coreSymbols().PERFORMANCE) {
                existingValue = getContext().getWarningCategoryPerformance();
            } else {
                throw CompilerDirectives.shouldNotReachHere("unexpected warning category");
            }

            if (existingValue.get() != newValue) {
                existingValue.set(newValue);
            }
            return newValue;
        }

    }

    @Primitive(name = "warn_given_block_not_used")
    public abstract static class WarnGivenBlockNotUsedNode extends PrimitiveNode {
        @Specialization
        Object warn(
                @Cached WarnNode warnNode) {
            if (warnNode.shouldWarn()) {
                warnNode.warningMessage(
                        getContext().getCallStack().getTopMostUserSourceSection(),
                        "given block not used");
            }

            return Nil.INSTANCE;
        }

    }

    @Primitive(name = "warn_block_supersedes_default_value_argument")
    public abstract static class WarnBlockSupersedesDefaultValueArgumentNode extends PrimitiveNode {
        @Specialization
        Object warn(
                @Cached WarnNode warnNode) {
            if (warnNode.shouldWarn()) {
                warnNode.warningMessage(
                        getContext().getCallStack().getTopMostUserSourceSection(),
                        "block supersedes default value argument");
            }

            return Nil.INSTANCE;
        }

    }

}
