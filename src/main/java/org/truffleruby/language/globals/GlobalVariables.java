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
package org.truffleruby.language.globals;

import java.util.ArrayList;
import java.util.Collection;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class GlobalVariables {

    private final RubyContext context;
    private final RubyLanguage language;

    public GlobalVariables(RubyContext context) {
        this.context = context;
        this.language = context.getLanguageSlow();
    }

    @TruffleBoundary
    public boolean contains(String name) {
        int index = language.getGlobalVariableIndex(name);
        return context.globalVariablesArray.contains(index);
    }

    public GlobalVariableReader getReader(String name) {
        return new GlobalVariableReader(language, name, context.globalVariablesArray);
    }

    public GlobalVariableStorage define(String name, Object value, Node node) {
        return define(name, new GlobalVariableStorage(value, null, null, null), node);
    }

    public GlobalVariableStorage define(String name, RubyProc getter, RubyProc setter, RubyProc isDefined, Node node) {
        return define(name, new GlobalVariableStorage(getter, setter, isDefined), node);
    }

    private GlobalVariableStorage define(String name, GlobalVariableStorage storage, Node node) {
        int index = language.getGlobalVariableIndex(name);
        GlobalVariableStorage previous = context.globalVariablesArray.addIfAbsent(index, storage);

        if (previous != storage) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError(
                            "Global variable $" + name + " is already defined",
                            node));
        }
        return storage;
    }

    @TruffleBoundary
    public void alias(String oldName, String newName) {
        int oldIndex = language.getGlobalVariableIndex(oldName);
        final GlobalVariableStorage storage = context.getGlobalVariableStorage(oldIndex);

        int newIndex = language.getGlobalVariableIndex(newName);
        final GlobalVariableStorage previousStorage = context.globalVariablesArray.set(newIndex, storage);

        // If previousStorage == storage, we already have that alias and should not invalidate
        if (previousStorage != null && previousStorage != storage) {
            previousStorage.noLongerAssumeConstant();
            language.getGlobalVariableNeverAliasedAssumption(newIndex).invalidate(
                    newName + " storage was overridden with " + oldName);
        }
    }

    @TruffleBoundary
    public String[] keys() {
        return context.globalVariablesArray.keys().toArray(StringUtils.EMPTY_STRING_ARRAY);
    }

    @TruffleBoundary
    public Collection<Object> objectGraphValues() {
        final Collection<GlobalVariableStorage> storages = context.globalVariablesArray.values();
        final ArrayList<Object> values = new ArrayList<>();

        for (GlobalVariableStorage storage : storages) {
            // TODO CS 11-Mar-17 handle hooked global variable storage?
            if (!storage.hasHooks()) {
                final Object value = storage.getValue();
                if (ObjectGraph.isRubyObject(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

}
