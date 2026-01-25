/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.AssumedValue;
import org.graalvm.collections.Pair;
import org.graalvm.options.OptionDescriptor;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.collections.SharedIndicesMap.ContextArray;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.DataObjectFinalizationService;
import org.truffleruby.core.FinalizationService;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.MarkingService;
import org.truffleruby.core.ReferenceProcessingService.ReferenceProcessor;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.EncodingManager;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.exception.CoreExceptions;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.core.hash.PreInitializationManager;
import org.truffleruby.core.hash.ReHashable;
import org.truffleruby.core.inlined.CoreMethods;
import org.truffleruby.core.kernel.AtExitManager;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.objectspace.ObjectSpaceManager;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.core.time.GetTimeZoneNode;
import org.truffleruby.debug.MetricsProfiler;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.SafepointManager;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.globals.GlobalVariableStorage;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.loader.FeatureLoader;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.options.LanguageOptions;
import org.truffleruby.options.Options;
import org.truffleruby.parser.YARPTranslatorDriver;
import org.truffleruby.platform.NativeConfiguration;
import org.truffleruby.platform.Signals;
import org.truffleruby.platform.TruffleNFIPlatform;
import org.truffleruby.shared.Metrics;
import org.truffleruby.shared.TruffleRuby;
import org.truffleruby.shared.options.OptionsCatalog;
import org.truffleruby.shared.options.RubyOptionTypes;
import org.truffleruby.stdlib.readline.ConsoleHolder;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import sun.misc.SignalHandler;

public final class RubyContext {

    private final RubyLanguage language;
    @CompilationFinal private TruffleLanguage.Env env;
    @CompilationFinal private PrintStream outStream;
    @CompilationFinal private PrintStream errStream;

    @CompilationFinal public TruffleLogger logger;
    @CompilationFinal private Options options;

    private final SafepointManager safepointManager;
    private final CodeLoader codeLoader;
    private final FeatureLoader featureLoader;
    private final TraceManager traceManager;
    private final ReferenceProcessor referenceProcessor;
    private final FinalizationService finalizationService;
    private final DataObjectFinalizationService dataObjectFinalizationService;
    private final MarkingService markingService;
    private final ObjectSpaceManager objectSpaceManager = new ObjectSpaceManager();
    private final SharedObjects sharedObjects = new SharedObjects(this);
    private final AtExitManager atExitManager;
    private final CallStackManager callStack;
    private final CoreExceptions coreExceptions;
    private final EncodingManager encodingManager;
    private final MetricsProfiler metricsProfiler;
    private final PreInitializationManager preInitializationManager;
    private final NativeConfiguration nativeConfiguration;
    private final ValueWrapperManager valueWrapperManager;
    /** (Symbol, refinements) -> Proc for Symbol#to_proc */
    public final Map<Pair<RubySymbol, Map<RubyModule, RubyModule[]>>, RootCallTarget> cachedSymbolToProcTargetsWithRefinements = new ConcurrentHashMap<>();
    /** Default signal handlers for Ruby, only SIGINT and SIGALRM, see {@code core/main.rb} */
    public final ConcurrentMap<String, SignalHandler> defaultRubySignalHandlers = new ConcurrentHashMap<>();

    @CompilationFinal private SecureRandom random;
    private final Hashing hashing;
    @CompilationFinal private BacktraceFormatter defaultBacktraceFormatter;
    private final BacktraceFormatter userBacktraceFormatter;
    @CompilationFinal private TruffleNFIPlatform truffleNFIPlatform;
    private final CoreLibrary coreLibrary;
    @CompilationFinal private CoreMethods coreMethods;
    private final ThreadManager threadManager;
    public final FiberManager fiberManager;
    private final LexicalScope rootLexicalScope;
    private volatile ConsoleHolder consoleHolder;

    public final ContextArray<GlobalVariableStorage> globalVariablesArray;

    private final Object classVariableDefinitionLock = new Object();
    private final ReentrantLock cExtensionsLock = new ReentrantLock();

    private final boolean preInitialized;
    @CompilationFinal private boolean preInitializing;
    private boolean initialized;
    private volatile boolean finalizing;

    public int nativeArgc = 0;
    public long nativeArgv = 0L;
    public long nativeArgvLength = -1L;

    // Whether or not a custom Module.const_added callback was defined.
    // Optimization to avoid calling a default (empty) callback if there
    // are no user-defined callbacks.
    private volatile boolean constAddedDefined = false;

    private final AssumedValue<Boolean> warningCategoryDeprecated;
    private final AssumedValue<Boolean> warningCategoryExperimental;
    private final AssumedValue<Boolean> warningCategoryPerformance;

    private ImmutableRubyString mainScriptName;

    private static final ContextReference<RubyContext> REFERENCE = ContextReference.create(RubyLanguage.class);

    public static RubyContext get(Node node) {
        return REFERENCE.get(node);
    }

    public RubyContext(RubyLanguage language, TruffleLanguage.Env env) {
        Metrics.printTime("before-context-constructor");

        this.language = language;
        this.callStack = new CallStackManager(language, this);
        setEnv(env);
        this.preInitialized = preInitializing;

        preInitializationManager = preInitializing ? new PreInitializationManager() : null;

        options = createOptions(env, language.options);

        warningCategoryDeprecated = new AssumedValue<>(options.WARN_DEPRECATED);
        warningCategoryExperimental = new AssumedValue<>(options.WARN_EXPERIMENTAL);
        warningCategoryPerformance = new AssumedValue<>(options.WARN_PERFORMANCE);

        safepointManager = new SafepointManager(this);
        coreExceptions = new CoreExceptions(this, language);
        encodingManager = new EncodingManager(this, language);

        metricsProfiler = new MetricsProfiler(language, this);
        codeLoader = new CodeLoader(language, this);
        featureLoader = new FeatureLoader(this, language);
        referenceProcessor = new ReferenceProcessor(this);
        finalizationService = new FinalizationService(referenceProcessor);
        markingService = new MarkingService();
        dataObjectFinalizationService = new DataObjectFinalizationService(language, referenceProcessor);
        atExitManager = new AtExitManager(this, language);

        // We need to construct this at runtime
        random = createRandomInstance();

        hashing = new Hashing(generateHashingSeed());

        defaultBacktraceFormatter = BacktraceFormatter.createDefaultFormatter(this, language);
        userBacktraceFormatter = new BacktraceFormatter(this, language, BacktraceFormatter.USER_BACKTRACE_FLAGS);

        // Load the core library classes

        Metrics.printTime("before-create-core-library");
        globalVariablesArray = new ContextArray<>(
                language.globalVariablesMap,
                GlobalVariableStorage[]::new,
                () -> new GlobalVariableStorage(null, null, null));
        coreLibrary = new CoreLibrary(this, language);
        nativeConfiguration = NativeConfiguration.loadNativeConfiguration(this);
        coreLibrary.initialize();
        language.coreMethodAssumptions.registerAssumptions(coreLibrary);
        valueWrapperManager = new ValueWrapperManager();
        Metrics.printTime("after-create-core-library");

        rootLexicalScope = new LexicalScope(null, coreLibrary.objectClass);

        // Create objects that need core classes

        truffleNFIPlatform = isPreInitializing() ? null : createNativePlatform();

        // The encoding manager relies on POSIX having been initialized, so we can't process it during
        // normal core library initialization.
        Metrics.printTime("before-initialize-encodings");
        encodingManager.defineEncodings();
        encodingManager.initializeDefaultEncodings(truffleNFIPlatform, nativeConfiguration);
        Metrics.printTime("after-initialize-encodings");

        Metrics.printTime("before-thread-manager");
        threadManager = new ThreadManager(this, language);
        fiberManager = new FiberManager(language, this);
        threadManager.initialize();
        threadManager.initializeMainThread(Thread.currentThread());
        Metrics.printTime("after-thread-manager");

        Metrics.printTime("before-instruments");
        final Instrumenter instrumenter = env.lookup(Instrumenter.class);
        traceManager = new TraceManager(language, this, instrumenter);
        Metrics.printTime("after-instruments");

        Metrics.printTime("after-context-constructor");
    }

    public void initialize() {
        assert !initialized : "Already initialized";

        // Load the nodes
        Metrics.printTime("before-load-nodes");
        coreLibrary.loadCoreNodes();
        Metrics.printTime("after-load-nodes");

        // Capture known builtin methods
        coreMethods = new CoreMethods(language, this);

        // Load the part of the core library defined in Ruby
        Metrics.printTime("before-load-core");
        coreLibrary.loadRubyCoreLibraryAndPostBoot();
        Metrics.printTime("after-load-core");

        // Share once everything is loaded
        if (language.options.SHARED_OBJECTS_ENABLED && language.options.SHARED_OBJECTS_FORCE) {
            sharedObjects.startSharing(language, OptionsCatalog.SHARED_OBJECTS_FORCE.getName() + " being true");
        }

        if (isPreInitializing()) {
            // Cannot save the file descriptor in this SecureRandom in the image
            random = null;
            // Do not save image generator paths in the image heap
            featureLoader.setWorkingDirectory(null);
        } else {
            initialized = true;
        }
    }

    /** Re-initialize parts of the RubyContext depending on the running process. This is a small subset of the full
     * initialization which needs to be performed to adapt to the new process and external environment. Calls are kept
     * in the same order as during normal initialization. */
    protected boolean patch(Env newEnv) {
        setEnv(newEnv);
        if (preInitializing) {
            throw CompilerDirectives.shouldNotReachHere("Expected patch Env#isPreInitialization() to be false");
        }

        final Options oldOptions = this.options;
        final Options newOptions = createOptions(newEnv, language.options);
        if (!compatibleOptions(oldOptions, newOptions)) {
            return false;
        }
        this.options = newOptions;
        if (newOptions.WARN_DEPRECATED != oldOptions.WARN_DEPRECATED) {
            warningCategoryDeprecated.set(newOptions.WARN_DEPRECATED);
        }
        if (newOptions.WARN_EXPERIMENTAL != oldOptions.WARN_EXPERIMENTAL) {
            warningCategoryExperimental.set(newOptions.WARN_EXPERIMENTAL);
        }
        if (newOptions.WARN_PERFORMANCE != oldOptions.WARN_PERFORMANCE) {
            warningCategoryPerformance.set(newOptions.WARN_PERFORMANCE);
        }

        // Re-read the value of $TZ as it can be different in the new process
        GetTimeZoneNode.invalidateTZ();

        random = createRandomInstance();
        hashing.patchSeed(generateHashingSeed());

        this.defaultBacktraceFormatter = BacktraceFormatter.createDefaultFormatter(this, language);

        this.truffleNFIPlatform = createNativePlatform();
        encodingManager.initializeDefaultEncodings(truffleNFIPlatform, nativeConfiguration);

        threadManager.initialize();
        threadManager.restartMainThread(Thread.currentThread());

        Metrics.printTime("before-rehash");
        preInitializationManager.rehash();
        Metrics.printTime("after-rehash");

        Metrics.printTime("before-run-delayed-initialization");
        final Object toRunAtInit = coreLibrary.truffleBootModule.fields
                .getConstant("TO_RUN_AT_INIT")
                .getValue();
        for (Object proc : ArrayOperations.toIterable((RubyArray) toRunAtInit)) {
            final Source source = ((RubyProc) proc).declaringMethod
                    .getSharedMethodInfo()
                    .getSourceSection()
                    .getSource();
            YARPTranslatorDriver.printParseTranslateExecuteMetric("before-run-delayed-initialization", this, source);
            ProcOperations.rootCall((RubyProc) proc, NoKeywordArgumentsDescriptor.INSTANCE,
                    RubyBaseNode.EMPTY_ARGUMENTS);
            YARPTranslatorDriver.printParseTranslateExecuteMetric("after-run-delayed-initialization", this, source);
        }
        Metrics.printTime("after-run-delayed-initialization");

        initialized = true;
        return true;
    }

    protected boolean patchContext(Env newEnv) {
        try {
            return patch(newEnv);
        } catch (AbstractTruffleException e) {
            getDefaultBacktraceFormatter()
                    .printRubyExceptionOnEnvStderr("Exception during RubyContext.patch():\n", e);
            throw e;
        } catch (Throwable e) {
            System.err.println("Exception during RubyContext.patch():");
            e.printStackTrace();
            throw e;
        }
    }

    private boolean compatibleOptions(Options oldOptions, Options newOptions) {
        final String notReusingContext = "not reusing pre-initialized context: ";

        // Libraries loaded during pre-initialization

        if (newOptions.PATCHING != oldOptions.PATCHING) {
            RubyLanguage.LOGGER.fine(notReusingContext + "loading patching is " + newOptions.PATCHING);
            return false;
        }

        if (newOptions.DID_YOU_MEAN != oldOptions.DID_YOU_MEAN) {
            RubyLanguage.LOGGER.fine(notReusingContext + "loading did_you_mean is " + newOptions.DID_YOU_MEAN);
            return false;
        }

        return true;
    }

    private Options createOptions(TruffleLanguage.Env env, LanguageOptions languageOptions) {
        Metrics.printTime("before-options");
        final Options options = new Options(env, env.getOptions(), languageOptions);
        if (options.OPTIONS_LOG && RubyLanguage.LOGGER.isLoggable(Level.CONFIG)) {
            for (OptionDescriptor descriptor : OptionsCatalog.allDescriptors()) {
                assert descriptor.getName().startsWith(TruffleRuby.LANGUAGE_ID);
                final String optionName = descriptor.getName().substring(TruffleRuby.LANGUAGE_ID.length() + 1);
                RubyLanguage.LOGGER.config(
                        "option " + optionName + "=" +
                                RubyOptionTypes.valueToString(options.fromDescriptor(descriptor)));
            }
        }

        Metrics.printTime("after-options");
        return options;
    }

    private void setEnv(Env env) {
        this.env = env;
        this.outStream = printStreamFor(env.out());
        this.errStream = printStreamFor(env.err());
        this.logger = env.getLogger("");
        this.preInitializing = env.isPreInitialization();
    }

    private static PrintStream printStreamFor(OutputStream outputStream) {
        try {
            return new PrintStream(outputStream, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private long generateHashingSeed() {
        if (options.HASHING_DETERMINISTIC) {
            RubyLanguage.LOGGER.severe(
                    "deterministic hashing is enabled - this may make you vulnerable to denial of service attacks");
            return 7114160726623585955L;
        } else {
            final byte[] bytes = getRandomSeedBytes(Long.BYTES);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return buffer.getLong();
        }
    }

    private TruffleNFIPlatform createNativePlatform() {
        Metrics.printTime("before-create-native-platform");
        final TruffleNFIPlatform truffleNFIPlatform = options.NATIVE_PLATFORM ? new TruffleNFIPlatform(this) : null;
        featureLoader.initialize(nativeConfiguration, truffleNFIPlatform);
        Metrics.printTime("after-create-native-platform");
        return truffleNFIPlatform;
    }

    @TruffleBoundary
    public static Object send(Node currentNode, Object receiver, String methodName, Object... arguments) {
        if (currentNode != null && currentNode.isAdoptable()) {
            final EncapsulatingNodeReference callNodeRef = EncapsulatingNodeReference.getCurrent();
            final Node prev = callNodeRef.set(currentNode);
            try {
                return DispatchNode.getUncached().call(receiver, methodName, arguments);
            } finally {
                callNodeRef.set(prev);
            }
        } else {
            return DispatchNode.getUncached().call(receiver, methodName, arguments);
        }
    }

    @TruffleBoundary
    public static Object indirectCallWithCallNode(Node currentNode, RootCallTarget callTarget,
            Object... frameArguments) {
        if (currentNode.isAdoptable()) {
            final EncapsulatingNodeReference callNodeRef = EncapsulatingNodeReference.getCurrent();
            final Node prev = callNodeRef.set(currentNode);
            try {
                return IndirectCallNode.getUncached().call(callTarget, frameArguments);
            } finally {
                callNodeRef.set(prev);
            }
        } else {
            return IndirectCallNode.getUncached().call(callTarget, frameArguments);
        }
    }

    public void finalizeContext() {
        if (!initialized) {
            // The RubyContext will be finalized and disposed if patching fails (potentially for
            // another language). In that case, there is nothing to clean or execute.
            return;
        }

        finalizing = true;

        atExitManager.runSystemExitHooks();
        threadManager.killAndWaitOtherThreads();
        atExitManager.runNativeExitHooks();
    }

    private final ReentrantLock disposeLock = new ReentrantLock();
    private boolean disposed = false;

    public void disposeContext() {
        disposeLock.lock();
        try {
            if (!disposed) {
                dispose();
                disposed = true;
            }
        } finally {
            disposeLock.unlock();
        }
    }

    private void dispose() {
        if (!initialized) {
            // The RubyContext will be finalized and disposed if patching fails (potentially for
            // another language). In that case, there is nothing to clean or execute.
            return;
        }

        if (consoleHolder != null) {
            consoleHolder.close();
        }

        threadManager.dispose();
        threadManager.checkNoRunningThreads();

        Signals.restoreDefaultHandlers();

        if (options.PRINT_INTERNED_TSTRING_STATS) {
            RubyLanguage.LOGGER.info("tstrings re-used: " + language.tstringCache.getTStringsReusedCount());
            RubyLanguage.LOGGER.info("tstring byte arrays re-used: " + language.tstringCache.getByteArrayReusedCount());
            RubyLanguage.LOGGER.info("tstring bytes saved: " + language.tstringCache.getTStringBytesSaved());
            RubyLanguage.LOGGER.info("total tstrings interned: " + language.tstringCache.totalTStrings());
        }

        if (options.CEXTS_TO_NATIVE_STATS) {
            RubyLanguage.LOGGER.info(
                    "Total VALUE object to native conversions: " + getValueWrapperManager().totalHandleAllocations());
        }
        valueWrapperManager.freeAllBlocksInMap();
    }

    public boolean isPreInitializing() {
        return preInitializing;
    }

    public boolean wasPreInitialized() {
        return preInitialized;
    }

    public Hashing getHashing() {
        return hashing;
    }

    public RubyLanguage getLanguageSlow() {
        CompilerAsserts.neverPartOfCompilation(
                "Use getLanguage() or RubyLanguage.get(Node) instead, so the RubyLanguage instance is constant in PE code");
        return language;
    }

    public Options getOptions() {
        return options;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    /** Hashing for a RubyNode, the seed should only be used for a Ruby-level #hash method */
    public Hashing getHashing(Node node) {
        return hashing;
    }

    /** With context pre-initialization, the random seed must be reset at runtime. So every use of the random seed
     * through Hashing should provide a way to rehash to take the new random seed in account. */
    public Hashing getHashing(ReHashable reHashable) {
        if (isPreInitializing()) {
            preInitializationManager.addReHashable(reHashable);
        }
        return hashing;
    }

    public BacktraceFormatter getDefaultBacktraceFormatter() {
        return defaultBacktraceFormatter;
    }

    public BacktraceFormatter getUserBacktraceFormatter() {
        return userBacktraceFormatter;
    }

    public CoreLibrary getCoreLibrary() {
        return coreLibrary;
    }

    public CoreMethods getCoreMethods() {
        return coreMethods;
    }

    public FeatureLoader getFeatureLoader() {
        return featureLoader;
    }

    public ReferenceProcessor getReferenceProcessor() {
        return referenceProcessor;
    }

    public FinalizationService getFinalizationService() {
        return finalizationService;
    }

    public DataObjectFinalizationService getDataObjectFinalizationService() {
        return dataObjectFinalizationService;
    }

    public MarkingService getMarkingService() {
        return markingService;
    }

    public ObjectSpaceManager getObjectSpaceManager() {
        return objectSpaceManager;
    }

    public SharedObjects getSharedObjects() {
        return sharedObjects;
    }

    public ThreadManager getThreadManager() {
        return threadManager;
    }

    public AtExitManager getAtExitManager() {
        return atExitManager;
    }

    public TraceManager getTraceManager() {
        return traceManager;
    }

    public SafepointManager getSafepointManager() {
        return safepointManager;
    }

    public LexicalScope getRootLexicalScope() {
        return rootLexicalScope;
    }

    public CodeLoader getCodeLoader() {
        return codeLoader;
    }

    public CallStackManager getCallStack() {
        return callStack;
    }

    public Object getClassVariableDefinitionLock() {
        return classVariableDefinitionLock;
    }

    public ReentrantLock getCExtensionsLock() {
        return cExtensionsLock;
    }

    public Instrumenter getInstrumenter() {
        return env.lookup(Instrumenter.class);
    }

    public CoreExceptions getCoreExceptions() {
        return coreExceptions;
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public MetricsProfiler getMetricsProfiler() {
        return metricsProfiler;
    }

    public PreInitializationManager getPreInitializationManager() {
        return preInitializationManager;
    }

    public TruffleLogger getLogger() {
        return logger;
    }

    @TruffleBoundary
    public ConsoleHolder getConsoleHolder() {
        if (consoleHolder == null) {
            synchronized (this) {
                if (consoleHolder == null) {
                    consoleHolder = ConsoleHolder.create(this, language);
                }
            }
        }

        return consoleHolder;
    }

    public void setConsoleHolder(ConsoleHolder consoleHolder) {
        synchronized (this) {
            final ConsoleHolder previous = Objects.requireNonNull(this.consoleHolder);
            previous.close();
            this.consoleHolder = consoleHolder;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    public TruffleNFIPlatform getTruffleNFI() {
        return truffleNFIPlatform;
    }

    public NativeConfiguration getNativeConfiguration() {
        return nativeConfiguration;
    }

    public ValueWrapperManager getValueWrapperManager() {
        return valueWrapperManager;
    }

    private static SecureRandom createRandomInstance() {
        try {
            /* We want to use a non-blocking source because this is what MRI does (via /dev/urandom) and it's been found
             * in practice that blocking sources are a problem for deploying JRuby. */
            return SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public byte[] getRandomSeedBytes(int numBytes) {
        final byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return bytes;
    }

    public Object getTopScopeObject() {
        return coreLibrary.topScopeObject;
    }

    public AssumedValue<Boolean> getWarningCategoryDeprecated() {
        return warningCategoryDeprecated;
    }

    public AssumedValue<Boolean> getWarningCategoryExperimental() {
        return warningCategoryExperimental;
    }

    public AssumedValue<Boolean> getWarningCategoryPerformance() {
        return warningCategoryPerformance;
    }

    public PrintStream getEnvOutStream() {
        return outStream;
    }

    public PrintStream getEnvErrStream() {
        return errStream;
    }

    @NeverDefault
    public GlobalVariableStorage getGlobalVariableStorage(int index) {
        return globalVariablesArray.get(index);
    }

    public void initializeMainScriptName(String mainScriptName) {
        ImmutableRubyString mainScriptString = language.getImmutableString(TStringUtils.utf8TString(mainScriptName),
                Encodings.UTF_8);

        int index = language.getGlobalVariableIndex("$0");
        getGlobalVariableStorage(index).setValueInternal(mainScriptString);

        this.mainScriptName = mainScriptString;
    }

    public ImmutableRubyString getMainScriptName() {
        return this.mainScriptName;
    }

    public boolean isConstAddedEverDefined() {
        return constAddedDefined;
    }

    public void constAddedIsDefined() {
        constAddedDefined = true;
    }
}
