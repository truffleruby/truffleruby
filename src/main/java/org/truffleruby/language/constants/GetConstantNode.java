/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.dispatch.LazyDispatchNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

public abstract class GetConstantNode extends RubyBaseNode {


    @NeverDefault
    public static GetConstantNode create() {
        return GetConstantNodeGen.create();
    }

    public Object lookupAndResolveConstant(LexicalScope lexicalScope, RubyModule module, String name,
            LookupConstantInterface lookupConstantNode, boolean callConstMissing) {
        final RubyConstant constant = lookupConstantNode.lookupConstant(this, lexicalScope, module, name, true);
        return executeGetConstant(lexicalScope, module, name, constant, lookupConstantNode, callConstMissing);
    }

    public Object lookupAndResolveConstant(LexicalScope lexicalScope, RubyModule module, String name, boolean checkName,
            LookupConstantInterface lookupConstantNode, boolean callConstMissing) {
        final RubyConstant constant = lookupConstantNode.lookupConstant(this, lexicalScope, module, name, checkName);
        return executeGetConstant(lexicalScope, module, name, constant, lookupConstantNode, callConstMissing);
    }

    // name is always the same as constant.getName(), but is needed as constant can be null.
    protected abstract Object executeGetConstant(LexicalScope lexicalScope, RubyModule module, String name,
            Object constant, LookupConstantInterface lookupConstantNode, boolean callConstMissing);


    @Specialization(guards = { "constant != null", "constant.hasValue()" })
    Object getConstant(
            LexicalScope lexicalScope,
            RubyModule module,
            String name,
            RubyConstant constant,
            LookupConstantInterface lookupConstantNode,
            boolean callConstMissing) {
        return constant.getValue();
    }

    @TruffleBoundary
    @Specialization(guards = { "autoloadConstant != null", "autoloadConstant.isAutoload()" })
    Object autoloadConstant(
            LexicalScope lexicalScope,
            RubyModule module,
            String name,
            RubyConstant autoloadConstant,
            LookupConstantInterface lookupConstantNode,
            boolean callConstMissing) {

        final Object feature = autoloadConstant.getAutoloadConstant().getFeature();

        if (autoloadConstant.getAutoloadConstant().isAutoloadingThread()) {
            // Pretend the constant does not exist while it is autoloading
            return doMissingConstant(module, name, callConstMissing);
        }

        var featureLoader = getContext().getFeatureLoader();
        var expandedPath = featureLoader.findFeature(autoloadConstant.getAutoloadConstant().getAutoloadPath());

        if (expandedPath != null && featureLoader.getFileLocks().isCurrentThreadHoldingLock(expandedPath)) {
            // We found an autoload constant while we are already require-ing the autoload file,
            // consider it missing to avoid circular require warnings and calling #require twice.
            // For instance, autoload :RbConfig, "rbconfig"; require "rbconfig" causes this.
            // Also see https://github.com/truffleruby/truffleruby/pull/1779 and GR-14590
            if (getContext().getOptions().LOG_AUTOLOAD) {
                RubyLanguage.LOGGER.info(() -> String.format(
                        "%s: %s::%s is being treated as missing while loading %s",
                        getLanguage().fileLine(getContext().getCallStack().getTopMostUserSourceSection()),
                        module.fields.getName(),
                        name,
                        expandedPath));
            }
            return doMissingConstant(module, name, callConstMissing);
        }

        if (getContext().getOptions().LOG_AUTOLOAD) {
            RubyLanguage.LOGGER.info(() -> String.format(
                    "%s: autoloading %s with %s",
                    getLanguage().fileLine(getContext().getCallStack().getTopMostUserSourceSection()),
                    autoloadConstant,
                    autoloadConstant.getAutoloadConstant().getAutoloadPath()));
        }

        // Mark the autoload constant as loading already here and not in RequireNode so that recursive lookups act as "being loaded"
        autoloadConstantStart(getContext(), autoloadConstant, this);
        try {
            RubyContext.send(this, coreLibrary().mainObject, "require", feature);

            // This needs to run while the autoload is marked as isAutoloading(), to avoid infinite recursion
            return autoloadResolveConstant(lexicalScope, module, name, autoloadConstant, lookupConstantNode,
                    callConstMissing);
        } finally {
            autoloadConstantStop(autoloadConstant);
        }
    }

    @TruffleBoundary
    public static void autoloadConstantStart(RubyContext context, RubyConstant autoloadConstant, Node currentNode) {
        autoloadConstant.getAutoloadConstant().startAutoLoad(context, currentNode);

        // We need to notify cached lookup that we are autoloading the constant, as constant
        // lookup changes based on whether an autoload constant is loading or not (constant
        // lookup ignores being-autoloaded constants).
        autoloadConstant.getDeclaringModule().fields.newConstantVersion(autoloadConstant.getName());
    }

    @TruffleBoundary
    public static void autoloadConstantStop(RubyConstant autoloadConstant) {
        autoloadConstant.getAutoloadConstant().stopAutoLoad();
    }

    /** Subset of {@link #autoloadResolveConstant} which does not try to resolve the constant. */
    @TruffleBoundary
    public static boolean autoloadUndefineConstantIfStillAutoload(RubyConstant autoloadConstant) {
        final RubyModule autoloadConstantModule = autoloadConstant.getDeclaringModule();
        final ModuleFields fields = autoloadConstantModule.fields;
        return fields.undefineConstantIfStillAutoload(autoloadConstant);
    }

    @TruffleBoundary
    public static void logAutoloadResult(RubyLanguage language, RubyContext context, RubyConstant constant,
            boolean undefined) {
        if (context.getOptions().LOG_AUTOLOAD) {
            final SourceSection section = context.getCallStack().getTopMostUserSourceSection();
            final String message = language.fileLine(section) + ": " + constant + " " +
                    (undefined
                            ? "was marked as undefined as it was not assigned in "
                            : "was successfully autoloaded from ") +
                    constant.getAutoloadConstant().getAutoloadPath();
            RubyLanguage.LOGGER.info(message);
        }
    }

    @TruffleBoundary
    public Object autoloadResolveConstant(LexicalScope lexicalScope, RubyModule module, String name,
            RubyConstant autoloadConstant, LookupConstantInterface lookupConstantNode, boolean callConstMissing) {
        final RubyModule autoloadConstantModule = autoloadConstant.getDeclaringModule();
        final ModuleFields fields = autoloadConstantModule.fields;

        RubyConstant resolvedConstant = lookupConstantNode.lookupConstant(this, lexicalScope, module, name, true);

        // check if the constant was set in the ancestors of autoloadConstantModule
        if (resolvedConstant != null &&
                (ModuleOperations.inAncestorsOf(resolvedConstant.getDeclaringModule(), autoloadConstantModule) ||
                        resolvedConstant.getDeclaringModule() == coreLibrary().objectClass)) {
            // all is good, just return that constant
            logAutoloadResult(getLanguage(), getContext(), autoloadConstant, false);
        } else {
            // If the autoload constant was not set in the ancestors, undefine the constant
            boolean undefined = fields.undefineConstantIfStillAutoload(autoloadConstant);
            logAutoloadResult(getLanguage(), getContext(), autoloadConstant, undefined);

            // redo lookup, to consider the undefined constant
            resolvedConstant = lookupConstantNode.lookupConstant(this, lexicalScope, module, name, true);
        }

        return executeGetConstant(lexicalScope, module, name, resolvedConstant, lookupConstantNode, callConstMissing);
    }

    @Specialization(guards = "isNullOrUndefined(constant)")
    static Object missingConstant(
            LexicalScope lexicalScope,
            RubyModule module,
            String name,
            Object constant,
            LookupConstantInterface lookupConstantNode,
            boolean callConstMissing,
            @Cached ToSymbolNode toSymbolNode,
            @Cached @Exclusive LazyDispatchNode constMissingNode,
            @Bind Node node) {
        CompilerAsserts.partialEvaluationConstant(callConstMissing);
        if (callConstMissing) {
            return constMissingNode.get(node).call(module, "const_missing", toSymbolNode.execute(node, name));
        } else {
            return null;
        }
    }

    @TruffleBoundary
    private Object doMissingConstant(RubyModule module, String name, boolean callConstMissing) {
        if (callConstMissing) {
            return RubyContext.send(this, module, "const_missing", getSymbol(name));
        } else {
            return null;
        }
    }

    protected boolean isNullOrUndefined(Object constant) {
        return constant == null || ((RubyConstant) constant).isUndefined();
    }

    protected int getCacheLimit() {
        return getLanguage().options.CONSTANT_CACHE;
    }

}
