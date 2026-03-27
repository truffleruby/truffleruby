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
package org.truffleruby.language.globals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class LookupGlobalVariableStorageNode extends RubyBaseNode {

    protected final String name;
    @CompilationFinal protected int index = -1;

    @NeverDefault
    public static LookupGlobalVariableStorageNode create(String name) {
        return LookupGlobalVariableStorageNodeGen.create(name);
    }

    public LookupGlobalVariableStorageNode(String name) {
        this.name = name;
    }

    public final GlobalVariableStorage execute(Object dynamicArgument) {
        if (index == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            index = getLanguage().getGlobalVariableIndex(name);
        }
        return executeInternal();
    }

    protected abstract GlobalVariableStorage executeInternal();

    @Specialization(
            guards = "isSingleContext()",
            assumptions = "getLanguage().getGlobalVariableNeverAliasedAssumption(index)")
    GlobalVariableStorage singleContext(
            @Cached("getContext().getGlobalVariableStorage(index)") GlobalVariableStorage storage) {
        return storage;
    }

    @Specialization(
            assumptions = "getLanguage().getGlobalVariableNeverAliasedAssumption(index)")
    GlobalVariableStorage multiContext() {
        return getContext().getGlobalVariableStorage(index);
    }

    @Specialization(guards = "!getLanguage().getGlobalVariableNeverAliasedAssumption(index).isValid()")
    GlobalVariableStorage aliased() {
        return getContext().globalVariablesArray.get(index);
    }

}
