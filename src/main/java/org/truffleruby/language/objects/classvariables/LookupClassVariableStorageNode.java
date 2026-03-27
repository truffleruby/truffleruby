/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;

@GenerateCached(false)
@GenerateInline
@ReportPolymorphism // inline cache
public abstract class LookupClassVariableStorageNode extends RubyBaseNode {

    public abstract ClassVariableStorage execute(Node node, RubyModule module, String name);

    @Specialization(guards = "objectLibrary.containsKey(classVariableStorage, name)")
    static ClassVariableStorage lookupClassVariable(RubyModule module, String name,
            @Bind("module.fields.getClassVariables()") ClassVariableStorage classVariableStorage,
            @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary) {
        return classVariableStorage;
    }

    @Specialization(replaces = "lookupClassVariable")
    @TruffleBoundary
    static ClassVariableStorage uncachedLookupClassVariable(RubyModule module, String name) {
        return ModuleOperations.classVariableLookup(module, true, m -> {
            final ClassVariableStorage classVariables = m.fields.getClassVariables();
            if (classVariables.getShape().hasProperty(name)) {
                return classVariables;
            } else {
                return null;
            }
        });
    }

}
