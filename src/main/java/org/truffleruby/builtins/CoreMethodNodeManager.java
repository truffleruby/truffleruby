/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.builtins;

import java.util.List;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.language.methods.CachedLazyCallTargetSupplier;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ConstantLookupResult;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.FixnumLowerNodeGen.FixnumLowerASTNodeGen;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyCoreMethodRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.annotations.Split;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.MissingArgumentBehavior;
import org.truffleruby.language.arguments.ReadBlockFromCurrentFrameArgumentsNode;
import org.truffleruby.language.arguments.ReadPreArgumentNode;
import org.truffleruby.language.arguments.ReadRemainingArgumentsNode;
import org.truffleruby.language.arguments.ReadSelfNode;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.SingletonClassNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.NodeFactory;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.parser.YARPTranslator;

public final class CoreMethodNodeManager {

    private final RubyContext context;
    private final RubyLanguage language;

    public CoreMethodNodeManager(RubyContext context) {
        this.context = context;
        this.language = context.getLanguageSlow();
    }

    public void loadCoreMethodNodes() {
        if (!TruffleOptions.AOT && language.options.LAZY_BUILTINS) {
            BuiltinsClasses.setupBuiltinsLazy(this);
        } else {
            for (List<? extends NodeFactory<? extends RubyBaseNode>> factory : BuiltinsClasses.getCoreNodeFactories()) {
                addCoreMethodNodes(factory);
            }
        }
    }

    public void addCoreMethodNodes(List<? extends NodeFactory<? extends RubyBaseNode>> nodeFactories) {
        String moduleName = null;
        RubyModule module = null;

        for (NodeFactory<? extends RubyBaseNode> nodeFactory : nodeFactories) {
            final Class<?> nodeClass = nodeFactory.getNodeClass();
            final CoreMethod methodAnnotation = nodeClass.getAnnotation(CoreMethod.class);
            if (methodAnnotation != null) {
                if (module == null) {
                    CoreModule coreModule = nodeClass.getEnclosingClass().getAnnotation(CoreModule.class);
                    if (coreModule == null) {
                        throw new Error(nodeClass.getEnclosingClass() + " needs a @CoreModule annotation");
                    }
                    moduleName = coreModule.value();
                    module = getModule(moduleName, coreModule.isClass());
                }
                addCoreMethod(module, new MethodDetails(moduleName, methodAnnotation, nodeFactory));
            }
        }
    }

    private RubyModule getModule(String fullName, boolean isClass) {
        RubyModule module;

        if (fullName.equals("main")) {
            module = getSingletonClass(context.getCoreLibrary().mainObject);
        } else {
            module = context.getCoreLibrary().objectClass;

            for (String moduleName : fullName.split("::")) {
                final ConstantLookupResult constant = ModuleOperations.lookupConstant(context, module, moduleName);

                if (!constant.isFound()) {
                    throw new RuntimeException(
                            StringUtils.format("Module %s not found when adding core library", moduleName));
                }

                module = (RubyModule) constant.getConstant().getValue();
            }
        }

        assert isClass == (module instanceof RubyClass) : fullName;
        return module;
    }

    private RubyClass getSingletonClass(Object object) {
        return SingletonClassNode.getUncached().execute(object);
    }

    private Split effectiveSplit(Split split, boolean needsBlock) {
        if (context.getOptions().CORE_ALWAYS_CLONE) {
            return Split.ALWAYS;
        } else if (split == Split.DEFAULT) {
            return needsBlock ? Split.ALWAYS : Split.HEURISTIC;
        } else {
            return split;
        }
    }

    private void addCoreMethod(RubyModule module, MethodDetails methodDetails) {
        final CoreMethod annotation = methodDetails.getMethodAnnotation();

        final String[] names = annotation.names();
        assert names.length >= 1;

        final Visibility visibility = annotation.visibility();
        verifyUsage(module, methodDetails, annotation, visibility);

        final Arity arity = new Arity(annotation.required(), annotation.optional(), annotation.rest());
        final NodeFactory<? extends RubyBaseNode> nodeFactory = methodDetails.getNodeFactory();
        final boolean onSingleton = annotation.onSingleton() || annotation.constructor();
        final boolean isModuleFunc = annotation.isModuleFunction();
        final Split split = effectiveSplit(annotation.split(), annotation.needsBlock());

        final Function<SharedMethodInfo, RootCallTarget> callTargetFactory = sharedMethodInfo -> {
            return createCoreMethodCallTarget(nodeFactory, language, sharedMethodInfo, split, annotation);
        };

        addMethods(
                module,
                methodDetails.moduleName,
                isModuleFunc,
                onSingleton,
                annotation.alwaysInlined(),
                names,
                arity,
                visibility,
                callTargetFactory);
    }

    public void addLazyCoreMethod(
            String nodeFactoryName,
            String moduleName,
            boolean isClass,
            Visibility visibility,
            boolean isModuleFunc,
            boolean onSingleton,
            boolean alwaysInlined,
            Split split,
            int required,
            int optional,
            boolean rest,
            boolean needsBlock,
            String... names) {

        final RubyModule module = getModule(moduleName, isClass);
        final Arity arity = new Arity(required, optional, rest);
        final Split finalSplit;
        if (alwaysInlined) {
            finalSplit = Split.NEVER;
        } else {
            finalSplit = effectiveSplit(split, needsBlock);
        }

        final Function<SharedMethodInfo, RootCallTarget> callTargetFactory = sharedMethodInfo -> {
            final NodeFactory<? extends RubyBaseNode> nodeFactory = loadNodeFactory(nodeFactoryName);
            final CoreMethod annotation = nodeFactory.getNodeClass().getAnnotation(CoreMethod.class);
            return createCoreMethodCallTarget(nodeFactory, language, sharedMethodInfo, finalSplit, annotation);
        };

        addMethods(
                module,
                moduleName,
                isModuleFunc,
                onSingleton,
                alwaysInlined,
                names,
                arity,
                visibility,
                callTargetFactory);
    }

    private void addMethods(
            RubyModule module,
            String moduleName,
            boolean isModuleFunction,
            boolean onSingleton,
            boolean alwaysInlined,
            String[] names,
            Arity arity,
            Visibility visibility,
            Function<SharedMethodInfo, RootCallTarget> callTargetFactory) {
        if (isModuleFunction) {
            addMethod(
                    context,
                    module,
                    moduleName,
                    false,
                    alwaysInlined,
                    callTargetFactory,
                    names,
                    arity,
                    Visibility.PRIVATE);
            final RubyClass sclass = getSingletonClass(module);
            addMethod(
                    context,
                    sclass,
                    moduleName,
                    true,
                    alwaysInlined,
                    callTargetFactory,
                    names,
                    arity,
                    Visibility.PUBLIC);
        } else if (onSingleton) {
            final RubyClass sclass = getSingletonClass(module);
            addMethod(context, sclass, moduleName, true, alwaysInlined, callTargetFactory, names, arity, visibility);
        } else {
            addMethod(context, module, moduleName, false, alwaysInlined, callTargetFactory, names, arity, visibility);
        }
    }

    private static void addMethod(
            RubyContext context,
            RubyModule module,
            String moduleName,
            boolean onSingleton,
            boolean alwaysInlined,
            Function<SharedMethodInfo, RootCallTarget> callTargetFactory,
            String[] names,
            Arity arity,
            Visibility visibility) {
        TruffleSafepoint.poll(null);

        for (String name : names) {
            final SharedMethodInfo sharedMethodInfo = makeSharedMethodInfo(
                    moduleName,
                    onSingleton,
                    name,
                    arity);

            final RootCallTarget callTarget;
            final CachedLazyCallTargetSupplier callTargetSupplier;
            final NodeFactory<? extends RubyBaseNode> alwaysInlinedNodeFactory;
            if (alwaysInlined) {
                callTarget = callTargetFactory.apply(sharedMethodInfo);
                callTargetSupplier = null;
                alwaysInlinedNodeFactory = RubyRootNode.of(callTarget).getAlwaysInlinedNodeFactory();
            } else {
                if (context.getLanguageSlow().options.LAZY_CALLTARGETS) {
                    callTarget = null;
                    callTargetSupplier = new CachedLazyCallTargetSupplier(
                            () -> callTargetFactory.apply(sharedMethodInfo));
                } else {
                    callTarget = callTargetFactory.apply(sharedMethodInfo);
                    callTargetSupplier = null;
                }

                alwaysInlinedNodeFactory = null;
            }

            module.fields.addMethod(context, null, new InternalMethod(
                    context,
                    sharedMethodInfo,
                    LexicalScope.IGNORE,
                    DeclarationContext.NONE,
                    name,
                    module,
                    ModuleOperations.isMethodPrivateFromName(name) ? Visibility.PRIVATE : visibility,
                    false,
                    alwaysInlinedNodeFactory,
                    null,
                    callTarget,
                    callTargetSupplier));
        }
    }

    private static SharedMethodInfo makeSharedMethodInfo(String moduleName, boolean onSingleton, String name,
            Arity arity) {
        final String parseName;
        if (onSingleton || moduleName.equals("main")) {
            parseName = moduleName + "." + name;
        } else {
            parseName = moduleName + "#" + name;
        }

        return SharedMethodInfo.forMethod(
                CoreLibrary.JAVA_CORE_SOURCE_SECTION,
                LexicalScope.IGNORE,
                arity,
                name,
                parseName,
                "builtin",
                null);
    }

    public static RootCallTarget createCoreMethodCallTarget(NodeFactory<? extends RubyBaseNode> nodeFactory,
            RubyLanguage language, SharedMethodInfo sharedMethodInfo, Split split, CoreMethod method) {
        // It's fine to load the node class here, this is only called when resolving the lazy CallTarget
        var nodeClass = nodeFactory.getNodeClass();

        boolean alwaysInlinedSubclass = AlwaysInlinedMethodNode.class.isAssignableFrom(nodeClass);
        if (method.alwaysInlined() != alwaysInlinedSubclass) {
            throw new Error(
                    nodeClass + " subclasses AlwaysInlinedMethodNode != using @CoreMethod(alwaysInlined = true)");
        }

        if (method.alwaysInlined()) {
            // See CallInternalMethodNode#getUncachedAlwaysInlinedMethodNode()
            assert nodeFactory.getUncachedInstance() != null : nodeClass +
                    " must use @GenerateUncached";
            assert nodeFactory.getUncachedInstance() instanceof AlwaysInlinedMethodNode : nodeClass +
                    " must subclass AlwaysInlinedMethodNode";

            if (method.lowerFixnum().length > 0 || method.raiseIfFrozenSelf() || method.raiseIfNotMutableSelf() ||
                    method.returnsEnumeratorIfNoBlock() || !method.enumeratorSize().isEmpty() ||
                    method.split() != Split.DEFAULT) {
                throw new Error("Always-inlined methods do not support all @CoreMethod attributes for " + nodeClass);
            }

            RubyRootNode reRaiseRootNode = new RubyRootNode(
                    language,
                    sharedMethodInfo.getSourceSection(),
                    null,
                    sharedMethodInfo,
                    new ReRaiseInlinedExceptionNode(nodeFactory),
                    Split.NEVER,
                    ReturnID.INVALID);
            return reRaiseRootNode.getCallTarget();
        }

        return createCoreMethodRootNode(nodeFactory, language, sharedMethodInfo, split, method).getCallTarget();
    }

    public static RubyCoreMethodRootNode createCoreMethodRootNode(NodeFactory<? extends RubyBaseNode> nodeFactory,
            RubyLanguage language, SharedMethodInfo sharedMethodInfo, Split split, CoreMethod method) {
        assert !method.alwaysInlined();

        final RubyNode[] argumentsNodes = new RubyNode[nodeFactory.getExecutionSignature().size()];
        int i = 0;

        final boolean needsSelf = needsSelf(method);

        if (needsSelf) {
            RubyNode readSelfNode = YARPTranslator.profileArgument(language, new ReadSelfNode());
            argumentsNodes[i++] = transformArgument(method, readSelfNode, 0);
        }

        final int required = method.required();
        final int optional = method.optional();
        final int nArgs = required + optional;

        for (int n = 0; n < nArgs; n++) {
            RubyNode readArgumentNode = YARPTranslator
                    .profileArgument(language, new ReadPreArgumentNode(n, false, MissingArgumentBehavior.NOT_PROVIDED));
            argumentsNodes[i++] = transformArgument(method, readArgumentNode, n + 1);
        }

        if (method.rest()) {
            argumentsNodes[i++] = new ReadRemainingArgumentsNode(nArgs);
        }

        if (method.needsBlock()) {
            argumentsNodes[i++] = new ReadBlockFromCurrentFrameArgumentsNode();
        }

        RubyNode node = (RubyNode) createNodeFromFactory(nodeFactory, argumentsNodes);
        RubyNode methodNode = transformResult(language, method, node);

        var frameDescriptor = TranslatorEnvironment.newFrameDescriptorBuilderForMethod(sharedMethodInfo).build();

        return new RubyCoreMethodRootNode(
                language,
                sharedMethodInfo.getSourceSection(),
                frameDescriptor,
                sharedMethodInfo,
                methodNode,
                split,
                ReturnID.INVALID,
                sharedMethodInfo.getArity(),
                nodeFactory,
                method);
    }

    public static RubyBaseNode createNodeFromFactory(NodeFactory<? extends RubyBaseNode> nodeFactory,
            RubyNode[] argumentsNodes) {
        final List<List<Class<?>>> signatures = nodeFactory.getNodeSignatures();

        assert signatures.size() == 1;
        final List<Class<?>> signature = signatures.get(0);

        if (signature.size() == 0) {
            return nodeFactory.createNode();
        } else {
            if (signature.size() == 1 && signature.get(0) == RubyNode[].class) {
                Object args = argumentsNodes;
                return nodeFactory.createNode(args);
            } else {
                Object[] args = argumentsNodes;
                return nodeFactory.createNode(args);
            }
        }
    }

    public static boolean needsSelf(CoreMethod method) {
        // Do not use needsSelf=true in module functions, it is either the module/class or the instance.
        // Usage of needsSelf is quite rare for singleton methods (except constructors).
        return method.constructor() || (!method.isModuleFunction() && !method.onSingleton() && method.needsSelf());
    }

    private static RubyNode transformArgument(CoreMethod method, RubyNode argument, int n) {
        if (ArrayUtils.contains(method.lowerFixnum(), n)) {
            argument = FixnumLowerASTNodeGen.create(argument);
        }

        if (n == 0 && method.raiseIfFrozenSelf()) {
            argument = TypeNodes.TypeCheckFrozenNode.create(argument);
        } else if (n == 0 && method.raiseIfNotMutableSelf()) {
            argument = TypeNodes.CheckMutableStringNode.create(argument);
        }

        return argument;
    }

    private static RubyNode transformResult(RubyLanguage language, CoreMethod method, RubyNode node) {
        if (!method.enumeratorSize().isEmpty()) {
            assert !method
                    .returnsEnumeratorIfNoBlock() : "Only one of enumeratorSize or returnsEnumeratorIfNoBlock can be specified";
            // TODO BF 6-27-2015 Handle multiple method names correctly
            node = new EnumeratorSizeNode(
                    language.getSymbol(method.enumeratorSize()),
                    language.getSymbol(method.names()[0]),
                    node);
        } else if (method.returnsEnumeratorIfNoBlock()) {
            // TODO BF 3-18-2015 Handle multiple method names correctly
            node = new ReturnEnumeratorIfNoBlockNode(method.names()[0], node);
        }

        return node;
    }

    private void verifyUsage(RubyModule module, MethodDetails methodDetails, CoreMethod method,
            Visibility visibility) {
        if (method.isModuleFunction()) {
            if (visibility != Visibility.PUBLIC) {
                RubyLanguage.LOGGER
                        .warning("visibility ignored when isModuleFunction in " + methodDetails.getIndicativeName());
            }
            if (method.onSingleton()) {
                RubyLanguage.LOGGER
                        .warning("either onSingleton or isModuleFunction for " + methodDetails.getIndicativeName());
            }
            if (method.constructor()) {
                RubyLanguage.LOGGER
                        .warning("either constructor or isModuleFunction for " + methodDetails.getIndicativeName());
            }
            if (module instanceof RubyClass) {
                RubyLanguage.LOGGER
                        .warning("using isModuleFunction on a Class for " + methodDetails.getIndicativeName());
            }
        }
        if (method.onSingleton() && method.constructor()) {
            RubyLanguage.LOGGER.warning("either onSingleton or constructor for " + methodDetails.getIndicativeName());
        }

        if (methodDetails.getPrimaryName().equals("allocate") && !methodDetails.getModuleName().equals("Class")) {
            RubyLanguage.LOGGER
                    .warning("do not define #allocate but #__allocate__ for " + methodDetails.getIndicativeName());
        }
        if (methodDetails.getPrimaryName().equals("__allocate__") && method.visibility() != Visibility.PRIVATE) {
            RubyLanguage.LOGGER.warning(methodDetails.getIndicativeName() + " should be private");
        }
    }

    @SuppressWarnings("unchecked")
    public static NodeFactory<? extends RubyBaseNode> loadNodeFactory(String nodeFactoryName) {
        final Object instance;
        try {
            Class<?> nodeFactoryClass = Class.forName(nodeFactoryName);
            instance = nodeFactoryClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return (NodeFactory<? extends RubyBaseNode>) instance;
    }

    public static final class MethodDetails {

        private final String moduleName;
        private final CoreMethod methodAnnotation;
        private final NodeFactory<? extends RubyBaseNode> nodeFactory;

        public MethodDetails(
                String moduleName,
                CoreMethod methodAnnotation,
                NodeFactory<? extends RubyBaseNode> nodeFactory) {
            this.moduleName = moduleName;
            this.methodAnnotation = methodAnnotation;
            this.nodeFactory = nodeFactory;
        }

        public CoreMethod getMethodAnnotation() {
            return methodAnnotation;
        }

        public NodeFactory<? extends RubyBaseNode> getNodeFactory() {
            return nodeFactory;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getPrimaryName() {
            return methodAnnotation.names()[0];
        }

        public String getIndicativeName() {
            return moduleName + "#" + getPrimaryName();
        }
    }

}
