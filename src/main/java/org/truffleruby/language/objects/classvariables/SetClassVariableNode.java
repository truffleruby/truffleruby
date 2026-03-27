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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

public abstract class SetClassVariableNode extends RubyBaseNode {

    @NeverDefault
    public static SetClassVariableNode create() {
        return SetClassVariableNodeGen.create();
    }

    public abstract Object execute(RubyModule module, String name, Object value);

    @Specialization(guards = "!objectLibrary.isShared(classVariableStorage)")
    Object setClassVariableLocal(RubyModule module, String name, Object value,
            @Bind("module.fields.getClassVariables()") ClassVariableStorage classVariableStorage,
            @CachedLibrary(limit = "getDynamicObjectCacheLimit()") @Shared DynamicObjectLibrary objectLibrary,
            @Cached @Shared InlinedBranchProfile slowPath) {
        if (!objectLibrary.putIfPresent(classVariableStorage, name, value)) {
            slowPath.enter(this);
            ModuleOperations.setClassVariable(getLanguage(), getContext(), module, name, value, this);
        }
        return value;
    }

    @Specialization(guards = "objectLibrary.isShared(classVariableStorage)")
    Object setClassVariableShared(RubyModule module, String name, Object value,
            @Bind("module.fields.getClassVariables()") ClassVariableStorage classVariableStorage,
            @CachedLibrary(limit = "getDynamicObjectCacheLimit()") @Shared DynamicObjectLibrary objectLibrary,
            @Cached WriteBarrierNode writeBarrierNode,
            @Cached @Shared InlinedBranchProfile slowPath) {
        // See WriteObjectFieldNode
        writeBarrierNode.execute(this, value);

        final boolean set = classVariableStorage.putIfPresent(name, value, objectLibrary);
        if (!set) {
            slowPath.enter(this);
            ModuleOperations.setClassVariable(getLanguage(), getContext(), module, name, value, this);
        }
        return value;
    }

}
