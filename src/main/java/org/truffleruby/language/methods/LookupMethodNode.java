/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.MethodLookupResult;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.objects.MetaClassNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;

/** Caches {@link ModuleOperations#lookupMethodCached(RubyModule, String, DeclarationContext)} on an actual instance. */
@GenerateUncached
public abstract class LookupMethodNode extends RubyBaseNode {

    @NeverDefault
    public static LookupMethodNode create() {
        return LookupMethodNodeGen.create();
    }

    public abstract InternalMethod execute(Frame frame, RubyClass metaClass, String name, DispatchConfiguration config);

    @Specialization(
            // no need to guard on the context, the metaClass is context-specific
            guards = {
                    "isSingleContext()",
                    "metaClass == cachedMetaClass",
                    "name == cachedName",
                    "config == cachedConfig" },
            assumptions = "methodLookupResult.getAssumptions()",
            limit = "getCacheLimit()")
    InternalMethod lookupMethodCached(Frame frame, RubyClass metaClass, String name, DispatchConfiguration config,
            @Cached("metaClass") RubyClass cachedMetaClass,
            @Cached("name") String cachedName,
            @Cached("config") DispatchConfiguration cachedConfig,
            @Cached("lookupCached(getContext(), frame, cachedMetaClass, cachedName, config)") MethodLookupResult methodLookupResult) {

        return methodLookupResult.getMethod();
    }

    @ReportPolymorphism.Megamorphic
    @InliningCutoff
    @Specialization(replaces = "lookupMethodCached")
    InternalMethod lookupMethodUncached(Frame frame, RubyClass metaClass, String name, DispatchConfiguration config,
            @Cached MetaClassNode metaClassNode,
            @Cached InlinedConditionProfile noCallerMethodProfile,
            @Cached InlinedConditionProfile noPrependedModulesProfile,
            @Cached InlinedConditionProfile onMetaClassProfile,
            @Cached InlinedConditionProfile hasRefinementsProfile,
            @Cached InlinedConditionProfile notFoundProfile,
            @Cached InlinedConditionProfile publicProfile,
            @Cached InlinedConditionProfile privateProfile,
            @Cached InlinedConditionProfile isVisibleProfile) {
        CompilerAsserts.partialEvaluationConstant(config); // the DispatchConfiguration is always a constant in the caller

        // Actual lookup

        final DeclarationContext declarationContext = getDeclarationContext(frame, config);
        final InternalMethod method;
        // Lookup first in the metaclass as we are likely to find the method there
        final ModuleFields fields = metaClass.fields;
        InternalMethod topMethod;
        if (noPrependedModulesProfile.profile(this, !fields.hasPrependedModules()) &&
                onMetaClassProfile.profile(this, (topMethod = fields.getMethod(name)) != null) &&
                !hasRefinementsProfile.profile(this,
                        declarationContext != null && declarationContext.hasRefinements())) {
            method = topMethod;
        } else {
            method = ModuleOperations.lookupMethodUncached(metaClass, name, declarationContext);
        }

        if (notFoundProfile.profile(this, method == null || method.isUndefined())) {
            return null;
        }

        // Check visibility

        if (!config.ignoreVisibility) {
            final Visibility visibility = method.getVisibility();
            if (publicProfile.profile(this, visibility == Visibility.PUBLIC)) {
                return method;
            }

            if (config.onlyLookupPublic) {
                return null;
            }

            if (privateProfile.profile(this, visibility == Visibility.PRIVATE)) {
                // A private method may only be called with an implicit receiver.
                return null;
            }

            // Find the caller class
            final RubyClass callerClass;
            final InternalMethod callerMethod = RubyArguments.tryGetMethod(frame);

            if (noCallerMethodProfile.profile(this, callerMethod == null)) {
                callerClass = coreLibrary().objectClass;
            } else {
                callerClass = metaClassNode.execute(this, RubyArguments.getSelf(frame));
            }

            if (!isVisibleProfile.profile(this, method.isProtectedMethodVisibleTo(callerClass))) {
                return null;
            }
        }

        return method;
    }

    protected static MethodLookupResult lookupCached(RubyContext context, Frame callingFrame,
            RubyClass metaClass, String name, DispatchConfiguration config) {
        CompilerAsserts.neverPartOfCompilation("slow-path method lookup should not be compiled");

        final DeclarationContext declarationContext = getDeclarationContext(callingFrame, config);
        final MethodLookupResult method = ModuleOperations.lookupMethodCached(metaClass, name, declarationContext);

        if (!method.isDefined()) {
            return method.withNoMethod();
        }

        // Check visibility
        if (!config.ignoreVisibility) {
            final Visibility visibility = method.getMethod().getVisibility();
            if (visibility == Visibility.PUBLIC) {
                return method;
            }

            if (config.onlyLookupPublic) {
                return method.withNoMethod();
            }

            if (visibility == Visibility.PRIVATE) {
                // A private method may only be called with an implicit receiver.
                return method.withNoMethod();
            }

            final RubyClass callerClass = getCallerClass(context, callingFrame);
            if (!method.getMethod().isProtectedMethodVisibleTo(callerClass)) {
                return method.withNoMethod();
            }
        }

        return method;
    }

    private static DeclarationContext getDeclarationContext(Frame frame, DispatchConfiguration config) {
        return config.ignoreRefinements ? null : RubyArguments.tryGetDeclarationContext(frame);
    }

    private static RubyClass getCallerClass(RubyContext context, Frame callingFrame) {
        final InternalMethod callerMethod = RubyArguments.tryGetMethod(callingFrame);
        if (callerMethod == null) {
            return context.getCoreLibrary().objectClass;
        } else {
            return MetaClassNode.executeUncached(RubyArguments.getSelf(callingFrame));
        }
    }

    protected int getCacheLimit() {
        return getLanguage().options.METHOD_LOOKUP_CACHE;
    }
}
