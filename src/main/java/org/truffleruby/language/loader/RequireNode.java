/*
 * Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.WarningNode;
import org.truffleruby.language.constants.GetConstantNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.TranslateExceptionNode;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.Metrics;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public abstract class RequireNode extends RubyBaseNode {

    @Child private IndirectCallNode callNode = IndirectCallNode.create();
    @Child private DispatchNode isInLoadedFeatures = DispatchNode.create();
    @Child private DispatchNode addToLoadedFeatures = DispatchNode.create();
    @Child private DispatchNode relativeFeatureNode = DispatchNode.create();

    @Child private WarningNode warningNode;

    public abstract boolean executeRequire(String feature, Object expandedPath);

    @Specialization(guards = "libExpandedPathString.isRubyString(this, expandedPathString)", limit = "1")
    boolean require(String feature, Object expandedPathString,
            @Cached RubyStringLibrary libExpandedPathString) {
        return requireWithMetrics(feature, expandedPathString);
    }

    @TruffleBoundary
    private boolean requireWithMetrics(String feature, Object pathString) {
        String internedExpandedPath = StringOperations.getJavaString(pathString).intern();

        requireMetric("before-require-" + feature);
        try {
            //intern() to improve footprint
            return getContext().getMetricsProfiler().callWithMetrics(
                    "require",
                    feature,
                    () -> requireConsideringAutoload(feature, internedExpandedPath, pathString));
        } finally {
            requireMetric("after-require-" + feature);
        }
    }

    /** During the require operation we need to load constants marked as autoloaded for the expandedPath (see
     * {@code FeatureLoader#registeredAutoloads}) and mark them as started loading (via locks). After require we
     * re-select autoload constants (because their list can be supplemented with constants that are loaded themselves
     * (i.e. Object.autoload(:C, __FILE__))) and remove them from autoload registry. More details here:
     * https://github.com/truffleruby/truffleruby/pull/2060#issuecomment-668627142 **/
    private boolean requireConsideringAutoload(String feature, String expandedPath, Object pathString) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();
        final List<RubyConstant> constantsUnfiltered = featureLoader.getAutoloadConstants(expandedPath);
        final List<RubyConstant> alreadyAutoloading = new ArrayList<>();
        if (!constantsUnfiltered.isEmpty()) {
            var toAutoload = new ArrayList<RubyConstant>();
            for (RubyConstant constant : constantsUnfiltered) {
                // Do not autoload recursively from the #require call in GetConstantNode
                if (constant.getAutoloadConstant().isAutoloading()) {
                    alreadyAutoloading.add(constant);
                } else {
                    toAutoload.add(constant);
                }
            }


            if (getContext().getOptions().LOG_AUTOLOAD && !toAutoload.isEmpty()) {
                var toAutoloadWithPath = new String[toAutoload.size()];
                for (int i = 0; i < toAutoload.size(); i++) {
                    var constant = toAutoload.get(i);
                    toAutoloadWithPath[i] = constant + " with " + constant.getAutoloadConstant().getAutoloadPath();
                }
                String info = StringUtils.join(toAutoloadWithPath, " and ");
                RubyLanguage.LOGGER
                        .info(() -> String.format(
                                "%s: requiring %s which is registered as an autoload for %s",
                                getLanguage().fileLine(getContext().getCallStack().getTopMostUserSourceSection()),
                                feature,
                                info));
            }

            for (RubyConstant constant : toAutoload) {
                GetConstantNode.autoloadConstantStart(getContext(), constant, this);
            }
        }

        try {
            return doRequire(feature, expandedPath, pathString);
        } finally {
            final List<RubyConstant> releasedConstants = featureLoader.getAutoloadConstants(expandedPath);
            for (RubyConstant constant : releasedConstants) {
                if (constant.getAutoloadConstant().isAutoloadingThread() && !alreadyAutoloading.contains(constant)) {
                    final boolean undefined = GetConstantNode
                            .autoloadUndefineConstantIfStillAutoload(constant);
                    GetConstantNode.logAutoloadResult(getLanguage(), getContext(), constant, undefined);
                    GetConstantNode.autoloadConstantStop(constant);
                    featureLoader.removeAutoload(constant);
                }
            }
        }
    }

    private boolean doRequire(String originalFeature, String expandedPath, Object pathString) {
        final ReentrantLockFreeingMap<String> fileLocks = getContext().getFeatureLoader().getFileLocks();
        final ConcurrentMap<String, Boolean> patchFiles = getContext().getCoreLibrary().getPatchFiles();
        final ConcurrentMap<String, String> originalRequires = getContext().getCoreLibrary().getOriginalRequires();

        String relativeFeature = originalFeature;
        if (new File(originalFeature).isAbsolute()) {
            Object relativeFeatureString = relativeFeatureNode
                    .call(coreLibrary().truffleFeatureLoaderModule, "relative_feature", pathString);
            if (RubyStringLibrary.getUncached().isRubyString(this, relativeFeatureString)) {
                relativeFeature = StringOperations.getJavaString(relativeFeatureString);
            }
        }
        Boolean patchLoaded = patchFiles.get(relativeFeature);
        final boolean isPatched = patchLoaded != null;

        while (true) {
            final ReentrantLock lock = fileLocks.get(expandedPath);

            if (lock.isHeldByCurrentThread()) {
                if (isPatched && !patchLoaded) {
                    // it is loading the original of the patched file for the first time
                    // it has to allow this one case of circular require where the first require was the patch
                    patchLoaded = true;
                    patchFiles.put(relativeFeature, true);
                } else {
                    warnCircularRequire(expandedPath);
                    return false;
                }
            }

            if (!fileLocks.lock(getContext(), expandedPath, lock, this)) {
                continue;
            }

            try {
                if (isPatched && !patchLoaded) {
                    String expandedPatchPath = getLanguage().getRubyHome() + "/lib/patches/" + relativeFeature + ".rb";
                    RubyLanguage.LOGGER.config("patch file used: " + expandedPatchPath);
                    originalRequires.put(expandedPatchPath, expandedPath);
                    try {
                        final boolean loaded = parseAndCall(expandedPatchPath, expandedPatchPath);
                        assert loaded;
                    } finally {
                        originalRequires.remove(expandedPatchPath);
                    }

                    final boolean originalLoaded = patchFiles.get(relativeFeature);
                    if (!originalLoaded) {
                        addToLoadedFeatures(pathString);
                        // if original is not loaded make sure we set the patch to loaded
                        patchFiles.put(relativeFeature, true);
                    }

                    return true;
                }

                if (isFeatureLoaded(pathString)) {
                    return false;
                }

                if (!parseAndCall(originalFeature, expandedPath)) {
                    return false;
                }

                addToLoadedFeatures(pathString);

                return true;
            } finally {
                fileLocks.unlock(expandedPath, lock);
            }
        }
    }

    private boolean parseAndCall(String feature, String expandedPath) {
        if (isCExtension(expandedPath)) {
            requireCExtension(feature, expandedPath, this);
        } else {
            // All other files are assumed to be Ruby, the file type detection is not enough
            final RubySource rubySource;
            try {
                final FileLoader fileLoader = new FileLoader(getContext(), getLanguage());
                rubySource = fileLoader.loadFile(expandedPath);
            } catch (IOException e) {
                return false;
            }

            final RootCallTarget callTarget = getContext().getCodeLoader().parseTopLevelWithCache(rubySource,
                    this);

            final CodeLoader.DeferredCall deferredCall = getContext().getCodeLoader().prepareExecute(
                    callTarget,
                    ParserContext.TOP_LEVEL,
                    DeclarationContext.topLevel(getContext()),
                    null,
                    coreLibrary().mainObject,
                    getContext().getRootLexicalScope());

            requireMetric("before-execute-" + feature);
            try {
                getContext().getMetricsProfiler().callWithMetrics(
                        "execute",
                        feature,
                        () -> deferredCall.call(callNode));
            } finally {
                requireMetric("after-execute-" + feature);
            }
        }
        return true;
    }

    private boolean isCExtension(String path) {
        return path.toLowerCase(Locale.ENGLISH).endsWith(RubyLanguage.CEXT_EXTENSION);
    }

    @TruffleBoundary
    private void requireCExtension(String feature, String expandedPath, Node currentNode) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();

        final Object library;
        try {
            featureLoader.ensureCExtImplementationLoaded(feature, this);

            if (getContext().getOptions().CEXTS_LOG_LOAD) {
                RubyLanguage.LOGGER
                        .info(String.format("loading cext module %s (requested as %s)", expandedPath, feature));
            }

            library = featureLoader.loadCExtLibrary(feature, expandedPath, currentNode, false);
        } catch (Exception e) {
            handleCExtensionException(feature, e);
            throw e;
        }

        requireMetric("before-execute-" + feature);
        var currentFiber = getLanguage().getCurrentFiber();

        ValueWrapperManager.allocateNewBlock(getContext(), getLanguage());
        var prevGlobals = currentFiber.cGlobalVariablesDuringInitFunction;
        currentFiber.cGlobalVariablesDuringInitFunction = createEmptyArray();
        var prevThreadSafeExtension = currentFiber.threadSafeExtension;
        currentFiber.threadSafeExtension = false;
        try {
            RubyContext.send(currentNode, coreLibrary().truffleCExtModule, "init_extension", library, expandedPath);
        } finally {
            currentFiber.threadSafeExtension = prevThreadSafeExtension;
            currentFiber.cGlobalVariablesDuringInitFunction = prevGlobals;
            ValueWrapperManager.allocateNewBlock(getContext(), getLanguage());
            requireMetric("after-execute-" + feature);
        }
    }

    @TruffleBoundary
    private void handleCExtensionException(String feature, Exception e) {
        TranslateExceptionNode.logJavaException(getContext(), this, e);

        final Throwable linkErrorException = searchForException("NFIUnsatisfiedLinkError", e);
        if (linkErrorException != null) {
            final String linkError = linkErrorException.getMessage();

            if (getContext().getOptions().CEXTS_LOG_LOAD) {
                RubyLanguage.LOGGER.info("unsatisfied link error " + linkError);
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().loadError(linkError, this));
        }

        final Throwable linkerException = searchForException("LLVMLinkerException", e);
        if (linkerException != null) {
            final String linkError = linkerException.getMessage();
            final String message;
            final String home = getLanguage().getRubyHome();
            final String postInstallHook = home + "/lib/truffle/post_install_hook.sh";

            // Mismatches between the libssl compiled against and the libssl used at runtime (typically on a different machine)
            if (feature.contains("openssl")) {
                message = String.format(
                        "%s (%s)",
                        "the OpenSSL C extension was compiled against a different libssl than the one used on this system - recompile by running " +
                                postInstallHook,
                        linkError);
            } else {
                message = linkError;
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().runtimeError(message, this));
        }
    }

    private Throwable searchForException(String exceptionClass, Throwable exception) {
        while (exception != null) {
            if (exception.getClass().getSimpleName().equals(exceptionClass)) {
                return exception;
            }
            exception = exception.getCause();
        }

        return null;
    }

    public boolean isFeatureLoaded(Object feature) {
        final Object included = isInLoadedFeatures
                .call(coreLibrary().truffleFeatureLoaderModule, "feature_provided?", feature, true);
        return BooleanCastNode.executeUncached(included);
    }

    private void addToLoadedFeatures(Object feature) {
        addToLoadedFeatures.call(coreLibrary().truffleFeatureLoaderModule, "provide_feature", feature);
    }

    private void warnCircularRequire(String path) {
        if (warningNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warningNode = insert(new WarningNode());
        }

        if (warningNode.shouldWarn()) {
            final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
            warningNode.warningMessage(
                    sourceSection,
                    "loading in progress, circular require considered harmful - " + path);
        }
    }

    private void requireMetric(String id) {
        if (Metrics.getMetricsTime() && getContext().getOptions().METRICS_TIME_REQUIRE) {
            Metrics.printTime(id);
        }
    }
}
