/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.prism.Parser;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.builtins.PrimitiveManager;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.collections.SharedIndicesMap;
import org.truffleruby.collections.SharedIndicesMap.LanguageArray;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.RubyHandle;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.RubyBasicObject;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.RubyEncodingConverter;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.exception.RubyFrozenError;
import org.truffleruby.core.exception.RubyNameError;
import org.truffleruby.core.exception.RubyNoMethodError;
import org.truffleruby.core.exception.RubySyntaxError;
import org.truffleruby.core.exception.RubySystemCallError;
import org.truffleruby.core.exception.RubySystemExit;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.hash.RubyHash;
import org.graalvm.options.OptionValues;
import org.truffleruby.core.inlined.CoreMethodAssumptions;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.method.RubyUnboundMethod;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.mutex.RubyConditionVariable;
import org.truffleruby.core.mutex.RubyMutex;
import org.truffleruby.core.objectspace.ObjectSpaceManager;
import org.truffleruby.core.objectspace.RubyWeakKeyMap;
import org.truffleruby.core.objectspace.RubyWeakMap;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.queue.RubyQueue;
import org.truffleruby.core.queue.RubySizedQueue;
import org.truffleruby.core.range.RubyObjectRange;
import org.truffleruby.core.regexp.RegexpCacheKey;
import org.truffleruby.core.regexp.RegexpTable;
import org.truffleruby.core.regexp.RubyMatchData;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.ImmutableStrings;
import org.truffleruby.core.string.PathToTStringCache;
import org.truffleruby.core.string.TStringCache;
import org.truffleruby.core.string.CoreStrings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.core.support.RubyByteArray;
import org.truffleruby.core.support.RubyCustomRandomizer;
import org.truffleruby.core.support.RubyIO;
import org.truffleruby.core.support.RubyPRNGRandomizer;
import org.truffleruby.core.support.RubySecureRandomizer;
import org.truffleruby.core.symbol.CoreSymbols;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.core.thread.RubyBacktraceLocation;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.time.RubyTime;
import org.truffleruby.core.tracepoint.RubyTracePoint;
import org.truffleruby.extra.RubyAtomicReference;
import org.truffleruby.extra.RubyConcurrentMap;
import org.truffleruby.extra.ffi.RubyPointer;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.interop.RubyInnerContext;
import org.truffleruby.interop.RubySourceLocation;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyEvalInteractiveRootNode;
import org.truffleruby.language.RubyInlineParsingRequestNode;
import org.truffleruby.language.RubyParsingRequestNode;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptorManager;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.RubyObjectType;
import org.truffleruby.language.objects.classvariables.ClassVariableStorage;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.options.LanguageOptions;
import org.truffleruby.parser.FrameDescriptorInfo;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.ParsingParameters;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.shared.Platform;
import org.truffleruby.shared.Metrics;
import org.truffleruby.shared.TruffleRuby;
import org.truffleruby.shared.options.OptionsCatalog;
import org.truffleruby.signal.LibRubySignal;
import org.truffleruby.stdlib.CoverageManager;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import org.truffleruby.stdlib.digest.RubyDigest;

import static org.truffleruby.language.RubyBaseNode.createArray;
import static org.truffleruby.language.RubyBaseNode.createString;
import static org.truffleruby.language.RubyBaseNode.nil;

@TruffleLanguage.Registration(
        name = "Ruby",
        website = "https://github.com/truffleruby/truffleruby",
        contextPolicy = ContextPolicy.SHARED,
        id = TruffleRuby.LANGUAGE_ID,
        implementationName = TruffleRuby.FORMAL_NAME,
        version = TruffleRuby.LANGUAGE_VERSION,
        characterMimeTypes = RubyLanguage.MIME_TYPE,
        defaultMimeType = RubyLanguage.MIME_TYPE,
        dependentLanguages = { "nfi", "llvm", "regex" },
        fileTypeDetectors = RubyFileTypeDetector.class)
@ProvidedTags({
        CoverageManager.LineTag.class,
        TraceManager.CallTag.class,
        TraceManager.ClassTag.class,
        TraceManager.LineTag.class,
        TraceManager.NeverTag.class,
        StandardTags.RootTag.class,
        StandardTags.StatementTag.class,
        StandardTags.ReadVariableTag.class,
        StandardTags.WriteVariableTag.class,
})
public final class RubyLanguage extends TruffleLanguage<RubyContext> {

    @Option.Group(TruffleRuby.LANGUAGE_ID)
    public abstract static class RubySourceOptions {
        // @formatter:off
        @Option(help = "Whether Ruby coverage is enabled for this source", category = OptionCategory.INTERNAL)
        public static final OptionKey<Boolean> Coverage = new OptionKey<>(false);

        @Option(help = "Mark this source as the main Ruby script ($0)", category = OptionCategory.INTERNAL)
        public static final OptionKey<Boolean> MainScript = new OptionKey<>(false);

        @Option(help = "Record the line offset when this source was eval'ed", category = OptionCategory.INTERNAL)
        public static final OptionKey<Integer> LineOffset = new OptionKey<>(0);
        // @formatter:on
    }

    public static final String MIME_TYPE = "application/x-ruby";
    /** To avoid some String[] allocations */
    public static final String[] MIME_TYPES = { MIME_TYPE };

    public static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";

    public static final String CEXT_EXTENSION = Platform.CEXT_SUFFIX;

    public static final String RESOURCE_SCHEME = "resource:";

    public static final TruffleLogger LOGGER = TruffleLogger.getLogger(TruffleRuby.LANGUAGE_ID);

    public static final String INTERNAL_CORE_PREFIX = "<internal:core> ";

    /** Global cache of call targets that {@code RBSprintfCompiler.compile} returns */
    public static final Map<TStringWithEncoding, RootCallTarget> sprintfCompilerCallTargets = new ConcurrentHashMap<>();

    /** Global cache of type lists that {@code RBSprintfCompiler.typeList} returns */
    public static final Map<TStringWithEncoding, int[]> sprintfCompilerTypeLists = new ConcurrentHashMap<>();

    private RubyThread getOrCreateForeignThread(RubyContext context, Thread thread) {
        RubyThread foreignThread = rubyThreadInitMap.remove(thread);
        if (foreignThread == null) {
            foreignThread = context.getThreadManager().createForeignThread();
            rubyThreadInitMap.put(thread, foreignThread);
        }
        return foreignThread;
    }

    public final Map<Thread, RubyThread> rubyThreadInitMap = new ConcurrentHashMap<>();
    private final ContextThreadLocal<RubyThread> rubyThread = locals.createContextThreadLocal(
            (context, thread) -> {
                if (thread == context.getThreadManager().getOrInitializeRootJavaThread()) {
                    // Already initialized when creating the context
                    return context.getThreadManager().getRootThread();
                }

                if (context.getThreadManager().isRubyManagedThread(thread)) {
                    return Objects.requireNonNull(rubyThreadInitMap.remove(thread));
                }

                return getOrCreateForeignThread(context, thread);
            });

    public final Map<Thread, RubyFiber> rubyFiberInitMap = new ConcurrentHashMap<>();
    private final ContextThreadLocal<RubyFiber> rubyFiber = locals.createContextThreadLocal(
            (context, thread) -> {
                if (thread == context.getThreadManager().getOrInitializeRootJavaThread()) {
                    // Already initialized when creating the context
                    return context.getThreadManager().getRootThread().getRootFiber();
                }

                if (context.getThreadManager().isRubyManagedThread(thread)) {
                    return Objects.requireNonNull(rubyFiberInitMap.remove(thread));
                }

                return getOrCreateForeignThread(context, thread).getRootFiber();
            });

    private final CyclicAssumption tracingCyclicAssumption = new CyclicAssumption("object-space-tracing");
    @CompilationFinal private volatile Assumption tracingAssumption = tracingCyclicAssumption.getAssumption();

    @CompilationFinal public boolean singleContext = true;
    @CompilationFinal public Optional<RubyContext> contextIfSingleContext;
    private int numberOfContexts = 0;

    public final CyclicAssumption traceFuncUnusedAssumption = new CyclicAssumption("set_trace_func is not used");

    @CompilationFinal public String coreLoadPath;
    @CompilationFinal public String corePath;
    public final CoreMethodAssumptions coreMethodAssumptions;
    public final CoreStrings coreStrings;
    public final CoreSymbols coreSymbols;
    public final PrimitiveManager primitiveManager;
    public final TStringCache tstringCache;
    public final RegexpTable regexpTable;
    public final SymbolTable symbolTable;
    public final KeywordArgumentsDescriptorManager keywordArgumentsDescriptorManager = new KeywordArgumentsDescriptorManager();
    public final ImmutableStrings immutableStrings;

    // GR-44025: We store the cleanerThread explicitly here to make it a clear image building failure if it would still be set.
    public Thread cleanerThread = null;
    @CompilationFinal public Cleaner cleaner = null;

    @SuppressFBWarnings("VO_VOLATILE_REFERENCE_TO_ARRAY") public volatile ValueWrapperManager.HandleBlockWeakReference[] handleBlockSharedMap = new ValueWrapperManager.HandleBlockWeakReference[0];
    public final ValueWrapperManager.HandleBlockAllocator handleBlockAllocator = new ValueWrapperManager.HandleBlockAllocator();

    @CompilationFinal public LanguageOptions options;
    @CompilationFinal private String rubyHome;
    @CompilationFinal public String cextPath;
    public String[] allowPrivatePrimitivesPrefixes;

    private TruffleFile rubyHomeTruffleFile;

    @CompilationFinal private AllocationReporter allocationReporter;
    @CompilationFinal public CoverageManager coverageManager;

    private final AtomicLong nextObjectID = new AtomicLong(ObjectSpaceManager.INITIAL_LANGUAGE_OBJECT_ID);
    private final PathToTStringCache pathToTStringCache = new PathToTStringCache(this);

    public final SharedIndicesMap globalVariablesMap = new SharedIndicesMap();
    private final LanguageArray<Assumption> globalVariableNeverAliasedAssumptions = new LanguageArray<>(
            globalVariablesMap,
            Assumption[]::new,
            () -> Assumption.create("global variable was never aliased: "));

    private static final RubyObjectType objectType = new RubyObjectType();

    public final Shape basicObjectShape = createShape(RubyBasicObject.class, RubyBasicObject.LOOKUP);
    public final Shape moduleShape = createShape(RubyModule.class, RubyModule.LOOKUP);
    public final Shape classShape = createShape(RubyClass.class, RubyModule.LOOKUP);

    public final Shape arrayShape = createShape(RubyArray.class);
    public final Shape atomicReferenceShape = createShape(RubyAtomicReference.class);
    public final Shape bindingShape = createShape(RubyBinding.class);
    public final Shape byteArrayShape = createShape(RubyByteArray.class);
    public final Shape concurrentMapShape = createShape(RubyConcurrentMap.class);
    public final Shape conditionVariableShape = createShape(RubyConditionVariable.class);
    public final Shape customRandomizerShape = createShape(RubyCustomRandomizer.class);
    public final Shape digestShape = createShape(RubyDigest.class);
    public final Shape encodingConverterShape = createShape(RubyEncodingConverter.class);
    public final Shape exceptionShape = createShape(RubyException.class);
    public final Shape fiberShape = createShape(RubyFiber.class);
    public final Shape frozenErrorShape = createShape(RubyFrozenError.class);
    public final Shape handleShape = createShape(RubyHandle.class);
    public final Shape hashShape = createShape(RubyHash.class);
    public final Shape sharedHashShape = hashShape.makeSharedShape();
    public final Shape innerContextShape = createShape(RubyInnerContext.class);
    public final Shape ioShape = createShape(RubyIO.class);
    public final Shape matchDataShape = createShape(RubyMatchData.class);
    public final Shape methodShape = createShape(RubyMethod.class);
    public final Shape mutexShape = createShape(RubyMutex.class);
    public final Shape nameErrorShape = createShape(RubyNameError.class);
    public final Shape noMethodErrorShape = createShape(RubyNoMethodError.class);
    public final Shape objectRangeShape = createShape(RubyObjectRange.class);
    public final Shape procShape = createShape(RubyProc.class);
    public final Shape queueShape = createShape(RubyQueue.class);
    public final Shape prngRandomizerShape = createShape(RubyPRNGRandomizer.class);
    public final Shape secureRandomizerShape = createShape(RubySecureRandomizer.class);
    public final Shape sizedQueueShape = createShape(RubySizedQueue.class);
    public final Shape sourceLocationShape = createShape(RubySourceLocation.class);
    public final Shape stringShape = createShape(RubyString.class);
    public final Shape syntaxErrorShape = createShape(RubySyntaxError.class);
    public final Shape systemCallErrorShape = createShape(RubySystemCallError.class);
    public final Shape systemExitShape = createShape(RubySystemExit.class);
    public final Shape threadBacktraceLocationShape = createShape(RubyBacktraceLocation.class);
    public final Shape threadShape = createShape(RubyThread.class);
    public final Shape timeShape = createShape(RubyTime.class);
    public final Shape tracePointShape = createShape(RubyTracePoint.class);
    public final Shape truffleFFIPointerShape = createShape(RubyPointer.class);
    public final Shape unboundMethodShape = createShape(RubyUnboundMethod.class);
    public final Shape weakMapShape = createShape(RubyWeakMap.class);
    public final Shape weakKeyMapShape = createShape(RubyWeakKeyMap.class);

    public final Shape classVariableShape = Shape
            .newBuilder()
            .allowImplicitCastIntToLong(true)
            .layout(ClassVariableStorage.class, ClassVariableStorage.LOOKUP)
            .build();

    public final ThreadLocal<ParsingParameters> parsingRequestParams = new ThreadLocal<>();

    private static final SharedMethodInfo EMPTY_DECLARATION_SHARED_METHOD_INFO = SharedMethodInfo.forMethod(
            CoreLibrary.JAVA_CORE_SOURCE_SECTION,
            null,
            Arity.NO_ARGUMENTS,
            "<empty-declaration-descriptor>",
            "<empty-declaration-descriptor>",
            null,
            null);

    private static final SharedMethodInfo EMPTY_BINDING_SHARED_METHOD_INFO = SharedMethodInfo.forMethod(
            CoreLibrary.JAVA_CORE_SOURCE_SECTION,
            null,
            Arity.MODULE_BODY, // Primitive.create_empty_binding is only used in module-body methods
            "<empty-binding>",
            "<empty-binding>",
            null,
            null);

    public final FrameDescriptor EMPTY_BINDING_DESCRIPTOR = TranslatorEnvironment
            .newFrameDescriptorBuilderForMethod(EMPTY_BINDING_SHARED_METHOD_INFO).build();

    public static FrameDescriptor newEmptyDeclarationFrameDescriptor() {
        return TranslatorEnvironment.newFrameDescriptorBuilderForMethod(EMPTY_DECLARATION_SHARED_METHOD_INFO).build();
    }

    /** Some things require a declaration frame, and this should include a slot for special variable storage but nothing
     * else. This frame descriptor should be used for those frames to provide a constant frame descriptor in those
     * cases. Any other root nodes should use either
     * {@link TranslatorEnvironment#newFrameDescriptorBuilderForMethod(SharedMethodInfo)} or
     * {@link TranslatorEnvironment#newFrameDescriptorBuilderForBlock(FrameDescriptorInfo)}. */
    public final FrameDescriptor EMPTY_DECLARATION_DESCRIPTOR = newEmptyDeclarationFrameDescriptor();

    public MaterializedFrame createEmptyDeclarationFrame(Object[] packedArgs, SpecialVariableStorage variables) {
        // createVirtualFrame().materialize() optimizes better than createMaterializedFrame()
        final MaterializedFrame declarationFrame = Truffle
                .getRuntime()
                .createVirtualFrame(packedArgs, EMPTY_DECLARATION_DESCRIPTOR)
                .materialize();

        SpecialVariableStorage.set(declarationFrame, variables);
        return declarationFrame;
    }

    private static final LanguageReference<RubyLanguage> REFERENCE = LanguageReference.create(RubyLanguage.class);

    public static RubyLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    public RubyLanguage() {
        coreMethodAssumptions = new CoreMethodAssumptions(this);
        coreStrings = new CoreStrings(this);
        coreSymbols = new CoreSymbols();
        primitiveManager = new PrimitiveManager();
        tstringCache = new TStringCache(coreSymbols);
        symbolTable = new SymbolTable(tstringCache, coreSymbols);
        regexpTable = new RegexpTable();
        immutableStrings = new ImmutableStrings(tstringCache);
    }

    public RubyThread getCurrentThread() {
        return rubyThread.get();
    }

    public RubyFiber getCurrentFiber() {
        return rubyFiber.get();
    }

    @TruffleBoundary
    public RubyRegexp getRegexp(RegexpCacheKey regexp) {
        return regexpTable.getRegexpIfExists(regexp);
    }

    @TruffleBoundary
    public void addRegexp(RegexpCacheKey key, RubyRegexp regexp) {
        regexpTable.addRegexp(key, regexp);
    }

    @TruffleBoundary
    public RubySymbol getSymbol(String string) {
        return symbolTable.getSymbol(string);
    }

    @TruffleBoundary
    public RubySymbol getSymbol(AbstractTruffleString name, RubyEncoding encoding) {
        return symbolTable.getSymbol(name, encoding, false);
    }

    @TruffleBoundary
    public RubySymbol getSymbol(AbstractTruffleString name, RubyEncoding encoding, boolean preserveSymbol) {
        return symbolTable.getSymbol(name, encoding, preserveSymbol);
    }

    public Assumption getTracingAssumption() {
        return tracingAssumption;
    }

    public void invalidateTracingAssumption() {
        tracingCyclicAssumption.invalidate();
        tracingAssumption = tracingCyclicAssumption.getAssumption();
    }

    private boolean multiThreading = false;

    public boolean isMultiThreaded() {
        return multiThreading;
    }

    @Override
    protected void initializeMultiThreading(RubyContext context) {
        this.multiThreading = true;
    }

    @Override
    protected void initializeMultipleContexts() {
        LOGGER.fine("initializeMultipleContexts()");

        // TODO Make Symbol.all_symbols per context, by having a SymbolTable per context and creating new symbols with
        //  the per-language SymbolTable.

        if (contextIfSingleContext == null) { // before first context created
            this.singleContext = false;
        } else {
            throw CompilerDirectives
                    .shouldNotReachHere("#initializeMultipleContexts() called after a context was created");
        }
    }

    @Override
    public RubyContext createContext(Env env) {
        // We need to initialize the Metrics class of the language classloader
        Metrics.initializeOption();
        boolean firstContext;

        synchronized (this) {
            numberOfContexts++;
            setupCleaner();
            firstContext = this.options == null;

            if (firstContext) { // First context
                this.allocationReporter = env.lookup(AllocationReporter.class);
                this.options = new LanguageOptions(env, env.getOptions(), singleContext);
                setRubyHome(findRubyHome(env));
                setupLocale(env, rubyHome);
                loadLibYARPBindings();
                this.coreLoadPath = buildCoreLoadPath(this.options.CORE_LOAD_PATH);
                this.corePath = coreLoadPath + File.separator + "core" + File.separator;
                Instrumenter instrumenter = Objects.requireNonNull(env.lookup(Instrumenter.class));
                this.coverageManager = new CoverageManager(this, instrumenter);
                if (options.INSTRUMENT_ALL_NODES) {
                    instrumentAllNodes(instrumenter);
                }
                primitiveManager.loadCoreMethodNodes(this.options);
            }
        }


        // Set rubyHomeTruffleFile every time, as pre-initialized contexts use a different FileSystem
        if (!firstContext) {
            final String oldHome = this.rubyHome;
            final TruffleFile newHome = findRubyHome(env);
            if (!Objects.equals(newHome.getPath(), oldHome)) {
                throw CompilerDirectives.shouldNotReachHere(
                        "home changed for the same RubyLanguage instance: " + oldHome + " vs " + newHome);
            }
            rubyHomeTruffleFile = newHome;
        }

        LOGGER.fine("createContext() on " + Thread.currentThread());
        Metrics.printTime("before-create-context");
        final RubyContext context = new RubyContext(this, env);
        Metrics.printTime("after-create-context");
        if (singleContext) {
            contextIfSingleContext = Optional.of(context);
        } else {
            contextIfSingleContext = Optional.empty();
        }
        return context;
    }

    @Override
    protected void initializeContext(RubyContext context) {
        LOGGER.fine("initializeContext() on " + Thread.currentThread());

        try {
            Metrics.printTime("before-initialize-context");
            context.initialize();

            if (context.isPreInitializing()) {
                synchronized (this) {
                    resetRubyHome();
                    resetCleaner();
                }
            }
            Metrics.printTime("after-initialize-context");
        } catch (Throwable e) {
            if (context.getOptions().EXCEPTIONS_PRINT_JAVA || context.getOptions().EXCEPTIONS_PRINT_UNCAUGHT_JAVA) {
                e.printStackTrace();
            }
            throw e;
        }

        applicationStarts();
    }

    private void applicationStarts() {
        // Set breakpoints on this line to break when user code is about to be loaded
        return;
    }

    @Override
    protected boolean patchContext(RubyContext context, Env newEnv) {
        // We need to initialize the Metrics class of the language classloader
        Metrics.initializeOption();

        LOGGER.fine("patchContext() on " + Thread.currentThread() + ")");
        Metrics.printTime("before-patch-context");
        final LanguageOptions oldOptions = Objects.requireNonNull(this.options);
        final LanguageOptions newOptions = new LanguageOptions(newEnv, newEnv.getOptions(), singleContext);
        if (!LanguageOptions.areOptionsCompatibleOrLog(LOGGER, oldOptions, newOptions)) {
            return false;
        }

        synchronized (this) {
            setRubyHome(findRubyHome(newEnv));
            setupLocale(newEnv, rubyHome);
            loadLibYARPBindings();
            setupCleaner();
        }

        boolean patched = context.patchContext(newEnv);
        Metrics.printTime("after-patch-context");
        return patched;
    }

    @Override
    protected void finalizeContext(RubyContext context) {
        LOGGER.fine("finalizeContext() on " + Thread.currentThread());
        context.finalizeContext();
    }

    @Override
    protected void disposeContext(RubyContext context) {
        LOGGER.fine("disposeContext() on " + Thread.currentThread());
        context.disposeContext();

        if (options.COVERAGE_GLOBAL) {
            coverageManager.print(this, System.out);
        }

        synchronized (this) {
            // GR-28354: Workaround for no "before CacheStore hook"
            numberOfContexts--;
            if (numberOfContexts == 0 && !singleContext) {
                resetCleaner();
            }
        }
    }

    public static RubyContext getCurrentContext() {
        CompilerAsserts.neverPartOfCompilation("Use getContext() or RubyContext.get(Node) instead in PE code");
        return RubyContext.get(null);
    }

    public static RubyLanguage getCurrentLanguage() {
        CompilerAsserts.neverPartOfCompilation("Use getLanguage() or RubyLanguage.get(Node) instead in PE code");
        return RubyLanguage.get(null);
    }

    @Override
    protected RootCallTarget parse(ParsingRequest request) {
        final Source source = request.getSource();

        final ParsingParameters parsingParameters = parsingRequestParams.get();
        if (parsingParameters != null) { // from #require or core library
            assert parsingParameters.rubySource.getSource().equals(source);
            final ParserContext parserContext = request.getOptionValues().get(RubySourceOptions.MainScript)
                    ? ParserContext.TOP_LEVEL_FIRST
                    : ParserContext.TOP_LEVEL;
            final LexicalScope lexicalScope = contextIfSingleContext.map(RubyContext::getRootLexicalScope).orElse(null);
            return getCurrentContext().getCodeLoader().parse(
                    parsingParameters.rubySource,
                    parserContext,
                    null,
                    lexicalScope,
                    parsingParameters.currentNode);
        }

        RootNode root;
        if (source.isInteractive()) {
            root = new RubyEvalInteractiveRootNode(this, source);
        } else {
            final RubyContext context = Objects.requireNonNull(getCurrentContext());
            root = new RubyParsingRequestNode(
                    this,
                    context,
                    source,
                    request.getArgumentNames().toArray(StringUtils.EMPTY_STRING_ARRAY));
        }
        return root.getCallTarget();
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) {
        final RubyContext context = Objects.requireNonNull(getCurrentContext());
        return new RubyInlineParsingRequestNode(this, context, request.getSource(), request.getFrame());
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.create(Arrays.asList(OptionsCatalog.allDescriptors()));
    }

    @Override
    protected OptionDescriptors getSourceOptionDescriptors() {
        return new RubySourceOptionsOptionDescriptors();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    public void initializeThread(RubyContext context, Thread thread) {
        LOGGER.fine(() -> "initializeThread(" + showThread(thread) + ") on " + Thread.currentThread());

        if (thread == context.getThreadManager().getOrInitializeRootJavaThread()) {
            // Already initialized when creating the context
            return;
        }

        if (context.getThreadManager().isRubyManagedThread(thread)) {
            final RubyThread rubyThread = this.rubyThread.get(thread);
            if (rubyThread.thread == thread) { // new Ruby Thread
                if (thread != Thread.currentThread()) {
                    throw CompilerDirectives
                            .shouldNotReachHere("Ruby threads should be initialized on their Java thread");
                }
                context.getThreadManager().start(rubyThread, thread);
            } else { // (non-root) Fiber
                var fiber = this.rubyFiber.get(thread);
                rubyThread.setCurrentFiber(fiber);
            }
            return;
        }

        final RubyThread foreignThread = this.rubyThread.get(thread);
        context.getThreadManager().startForeignThread(foreignThread, thread);
    }

    @Override
    public void disposeThread(RubyContext context, Thread thread) {
        LOGGER.fine(() -> "disposeThread(" + showThread(thread) + ") on " + Thread.currentThread());

        if (thread == context.getThreadManager().getRootJavaThread()) {
            if (context.getEnv().isPreInitialization()) {
                // Cannot save the root Java Thread instance in the image
                context.getThreadManager().resetMainThread();
                context.getThreadManager().dispose();
                return;
            } else if (!context.isInitialized()) {
                // Context patching failed, we cannot cleanup the main thread as it was not initialized
                return;
            } else {
                // Cleanup the main thread, this is done between finalizeContext() and disposeContext()
                context.getThreadManager().cleanupThreadState(context.getThreadManager().getRootThread(), thread);
                return;
            }
        }

        if (context.getThreadManager().isRubyManagedThread(thread)) {
            final RubyThread rubyThread = this.rubyThread.get(thread);
            if (rubyThread.thread == thread) { // Thread
                if (thread != Thread.currentThread()) {
                    throw CompilerDirectives.shouldNotReachHere("Ruby threads should be disposed on their Java thread");
                }
                context.getThreadManager().cleanupThreadState(rubyThread, thread);
            } else { // (non-root) Fiber
                var fiber = this.rubyFiber.get(thread);
                context.fiberManager.cleanup(fiber, thread);
            }
            return;
        }

        // A foreign Thread, its Fibers are considered isRubyManagedThread()
        final RubyThread foreignThread = this.rubyThread.get(thread);
        context.getThreadManager().cleanup(foreignThread, thread);
    }

    private String showThread(Thread thread) {
        return "#" + getThreadId(thread) + " " + thread + " = " + this.rubyThread.get(thread);
    }

    @Override
    protected Object getScope(RubyContext context) {
        return context.getTopScopeObject();
    }

    private static void instrumentAllNodes(Instrumenter instrumenter) {
        instrumenter.attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext context, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        });
    }

    private void setupCleaner() {
        assert Thread.holdsLock(this);
        if (cleaner == null) {
            cleaner = Cleaner.create(runnable -> this.cleanerThread = new Thread(runnable, "Ruby-Cleaner"));
        }
    }

    private void resetCleaner() {
        assert Thread.holdsLock(this);
        cleanerThread = null;
        cleaner = null;
    }

    public String getRubyHome() {
        return rubyHome;
    }

    public TruffleFile getRubyHomeTruffleFile() {
        return rubyHomeTruffleFile;
    }

    public String getPathRelativeToHome(String path) {
        if (path.startsWith(rubyHome) && path.length() > rubyHome.length()) {
            return path.substring(rubyHome.length() + 1);
        } else {
            return path;
        }
    }

    private void setRubyHome(TruffleFile home) {
        assert Thread.holdsLock(this);
        rubyHomeTruffleFile = home;
        rubyHome = home.getPath();
        cextPath = rubyHome + "/lib/truffle/truffle/cext_ruby.rb";

        String allowIn = System.getenv("TRUFFLERUBY_ALLOW_PRIVATE_PRIMITIVES_IN");
        if (allowIn != null) {
            if (!allowIn.endsWith("/spec/truffle/") && !allowIn.endsWith("/test/truffle/compiler/") &&
                    !allowIn.endsWith("/bench/metrics/")) {
                throw CompilerDirectives
                        .shouldNotReachHere("Invalid value for TRUFFLERUBY_ALLOW_PRIMITIVES_IN: " + allowIn);
            }

            allowPrivatePrimitivesPrefixes = new String[]{
                    rubyHome + "/lib/truffle/",
                    rubyHome + "/lib/patches/",
                    rubyHome + "/lib/mri/",
                    allowIn,
            };
        } else {
            allowPrivatePrimitivesPrefixes = new String[]{
                    rubyHome + "/lib/truffle/",
                    rubyHome + "/lib/patches/",
                    rubyHome + "/lib/mri/",
            };
        }
    }

    private void resetRubyHome() {
        assert Thread.holdsLock(this);
        rubyHomeTruffleFile = null;
        rubyHome = null;
        cextPath = null;
        allowPrivatePrimitivesPrefixes = null;
    }

    private TruffleFile findRubyHome(Env env) {
        final TruffleFile home = searchRubyHome(env);
        if (LOGGER.isLoggable(Level.CONFIG)) {
            LOGGER.config("home: " + home);
        }
        return home;
    }

    // Returns a canonical path to the home
    private TruffleFile searchRubyHome(Env env) {
        final String truffleReported = getLanguageHome();
        if (truffleReported != null) {
            var truffleReportedFile = env.getInternalTruffleFile(truffleReported);
            try {
                if (truffleReportedFile.exists()) {
                    truffleReportedFile = truffleReportedFile.getCanonicalFile();
                }
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (isRubyHome(truffleReportedFile)) {
                LOGGER.config(() -> String.format("Using Truffle-reported home %s as the Ruby home", truffleReported));
                return truffleReportedFile;
            }
        }

        final TruffleFile homeResource;
        TruffleFile homeResourceRelative;
        try {
            homeResourceRelative = env.getInternalResource("ruby-home");
            if (homeResourceRelative != null && homeResourceRelative.exists()) {
                homeResource = homeResourceRelative.getCanonicalFile();
            } else {
                homeResource = null;
            }
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }

        if (homeResource != null) {
            if (isRubyHome(homeResource)) {
                LOGGER.config(() -> String.format("Using the internal resource %s as the Ruby home", homeResource));
                return homeResource;
            }
        }

        throw new Error("Could not find TruffleRuby's home - not possible to parse Ruby code" + String.format(
                " (Truffle-reported home %s and internal resource %s (%s) do not look like TruffleRuby's home).",
                truffleReported, homeResourceRelative, homeResource));
    }

    private boolean isRubyHome(TruffleFile path) {
        var lib = path.resolve("lib");
        return lib.resolve("truffle").isDirectory() &&
                (options.BUILDING_CORE_CEXTS || lib.resolve("gems").isDirectory()) &&
                lib.resolve("patches").isDirectory();
    }

    private void setupLocale(Env env, String rubyHome) {
        // CRuby does setlocale(LC_CTYPE, "") because this is needed to get the locale encoding with nl_langinfo(CODESET).
        // This means every locale category except LC_CTYPE remains the initial "C".
        // LC_CTYPE is set according to environment variables (LC_ALL, LC_CTYPE, LANG).
        // HotSpot and Native Image with UseSystemLocale=true (the default) do setlocale(LC_ALL, "").
        // We match CRuby by doing setlocale(LC_ALL, "C") and setlocale(LC_CTYPE, "").
        // This also affects C functions that depend on the locale in C extensions, so best to follow CRuby here.
        // Change the strict minimum if embedded because setlocale() is process-wide.
        if (env.getOptions().get(OptionsCatalog.EMBEDDED_KEY)) {
            if (ImageInfo.inImageRuntimeCode()) {
                // Only do this on Native Image, to handle the case of the embedder setting UseSystemLocale=false.
                LibRubySignal.loadLibrary(rubyHome, Platform.LIB_SUFFIX);
                LibRubySignal.setupLocaleOnlyCTYPE();
            } else {
                // Nothing to do, JVM and Native Image UseSystemLocale=true already did setlocale(LC_ALL, "")
                // so there is no need to setlocale(LC_CTYPE, "").
            }
        } else {
            LibRubySignal.loadLibrary(rubyHome, Platform.LIB_SUFFIX);
            LibRubySignal.setupLocale();
        }
    }

    private void loadLibYARPBindings() {
        String libyarpbindings = getRubyHome() + "/lib/libyarpbindings" + Platform.LIB_SUFFIX;
        Parser.loadLibrary(libyarpbindings);
    }

    @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
    public AllocationReporter getAllocationReporter() {
        return allocationReporter;
    }

    public ImmutableRubyString getImmutableString(TruffleString tstring, RubyEncoding encoding) {
        return immutableStrings.get(tstring, encoding);
    }

    public ImmutableRubyString getImmutableString(InternalByteArray byteArray, boolean isImmutable,
            RubyEncoding encoding) {
        return immutableStrings.get(byteArray, isImmutable, encoding);
    }

    public long getNextObjectID() {
        final long id = nextObjectID.getAndAdd(ObjectSpaceManager.OBJECT_ID_INCREMENT_BY);

        if (id == ObjectSpaceManager.INITIAL_LANGUAGE_OBJECT_ID - ObjectSpaceManager.OBJECT_ID_INCREMENT_BY) {
            throw CompilerDirectives.shouldNotReachHere("Language Object IDs exhausted");
        }

        return id;
    }

    public PathToTStringCache getPathToTStringCache() {
        return pathToTStringCache;
    }

    private static Shape createShape(Class<? extends RubyDynamicObject> layoutClass) {
        return createShape(layoutClass, RubyDynamicObject.LOOKUP);
    }

    private static Shape createShape(Class<? extends RubyDynamicObject> layoutClass, MethodHandles.Lookup lookup) {
        assert lookup.lookupClass().isAssignableFrom(layoutClass) : layoutClass;
        return Shape
                .newBuilder()
                .allowImplicitCastIntToLong(true)
                .layout(layoutClass, lookup)
                .dynamicType(RubyLanguage.objectType)
                .build();
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        final boolean compatible = checkAreOptionsCompatible(firstOptions, newOptions);
        LOGGER.fine(compatible ? "areOptionsCompatible() -> true" : "areOptionsCompatible() -> false");
        return compatible;
    }

    private boolean checkAreOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        if (firstOptions.get(OptionsCatalog.RUN_TWICE_KEY) ||
                firstOptions.get(OptionsCatalog.EXPERIMENTAL_ENGINE_CACHING_KEY)) {
            return LanguageOptions.areOptionsCompatible(firstOptions, newOptions);
        } else {
            return false;
        }
    }

    /** {@link RubyLanguage#getSourcePath(Source)} should be used instead whenever possible (i.e., when we can access
     * the language).
     *
     * Returns the path of a Source. Returns the short, potentially relative, path for the main script. Note however
     * that the path of {@code eval(code, nil, filename)} is just {@code filename} and might not be absolute. */
    public static String getPath(Source source) {
        final String path = source.getPath();
        if (path != null) {
            return path;
        } else {
            // non-file sources: eval(), main_boot_source, etc
            final String name = source.getName();
            assert name != null;
            return name;
        }
    }

    /** {@link RubyLanguage#getPath(Source)} but also handles core library sources. Ideally this method would be static
     * but for now the core load path is an option and it also depends on the current working directory. Once we have
     * Source metadata in Truffle we could use that to identify core library sources without needing the language. */
    @TruffleBoundary
    public String getSourcePath(Source source) {
        final String path = getPath(source);
        if (path.startsWith(coreLoadPath)) {
            return INTERNAL_CORE_PREFIX + path.substring(coreLoadPath.length() + 1);
        } else {
            return path;
        }
    }

    @TruffleBoundary
    public int getStartLineAdjusted(SourceSection sourceSection) {
        int lineOffset = sourceSection.getSource().getOptions(this).get(RubySourceOptions.LineOffset);
        return sourceSection.getStartLine() + lineOffset;
    }

    /** Only use when no language/context is available (e.g. Node#toString). Prefer
     * {@link RubyLanguage#fileLine(SourceSection)} as it accounts for coreLoadPath and line offsets. */
    @TruffleBoundary
    public static String fileLineRange(SourceSection section) {
        if (section == null) {
            return "no source section";
        } else {
            final String path = getPath(section.getSource());

            if (section.isAvailable()) {
                if (section.getStartLine() != section.getEndLine()) {
                    return path + ":" + section.getStartLine() + "-" + section.getEndLine();
                } else {
                    return path + ":" + section.getStartLine();
                }
            } else {
                return path;
            }
        }
    }

    @TruffleBoundary
    public String fileLine(SourceSection section) {
        if (section == null) {
            return "no source section";
        } else {
            final String path = getSourcePath(section.getSource());

            if (section.isAvailable()) {
                return path + ":" + getStartLineAdjusted(section);
            } else {
                return path;
            }
        }
    }

    public Object rubySourceLocation(SourceSection section,
            TruffleString.FromJavaStringNode fromJavaStringNode,
            Node node) {
        if (!BacktraceFormatter.isAvailable(section)) {
            return nil;
        } else {
            var file = createString(node, fromJavaStringNode, getSourcePath(section.getSource()), Encodings.UTF_8);
            Object[] objects = new Object[]{ file, getStartLineAdjusted(section) };
            return createArray(node, objects);
        }
    }

    public int getGlobalVariableIndex(String name) {
        return globalVariablesMap.lookup(name);
    }

    @TruffleBoundary
    public Assumption getGlobalVariableNeverAliasedAssumption(int index) {
        return globalVariableNeverAliasedAssumptions.get(index);
    }

    private static String buildCoreLoadPath(String coreLoadPath) {
        while (coreLoadPath.endsWith("/")) {
            coreLoadPath = coreLoadPath.substring(0, coreLoadPath.length() - 1);
        }

        if (coreLoadPath.startsWith(RubyLanguage.RESOURCE_SCHEME)) {
            return coreLoadPath;
        }

        try {
            return new File(coreLoadPath).getCanonicalPath();
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("deprecation") // deprecated on JDK19 by Thread#threadId, but that's added in JDK19
    public static long getThreadId(Thread thread) {
        return thread.getId();
    }
}
