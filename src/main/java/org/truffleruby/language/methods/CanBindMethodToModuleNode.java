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

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/** Caches {@link ModuleOperations#canBindMethodTo} for a method. */
@GenerateInline
@GenerateCached(false)
@ReportPolymorphism // inline cache
public abstract class CanBindMethodToModuleNode extends RubyBaseNode {

    public abstract boolean executeCanBindMethodToModule(Node node, InternalMethod method, RubyModule module);

    @Specialization(
            guards = {
                    "method.getDeclaringModule() == declaringModule",
                    "module == cachedModule" },
            limit = "getCacheLimit()")
    static boolean canBindMethodToCached(InternalMethod method, RubyModule module,
            @Cached("method.getDeclaringModule()") RubyModule declaringModule,
            @Cached("module") RubyModule cachedModule,
            @Cached("canBindMethodTo(method, cachedModule)") boolean canBindMethodTo) {
        return canBindMethodTo;
    }

    @Specialization
    static boolean canBindMethodToUncached(InternalMethod method, RubyModule module) {
        return canBindMethodTo(method, module);
    }

    protected static boolean canBindMethodTo(InternalMethod method, RubyModule module) {
        return ModuleOperations.canBindMethodTo(method, module);
    }

    protected int getCacheLimit() {
        return getLanguage().options.BIND_CACHE;
    }

}
