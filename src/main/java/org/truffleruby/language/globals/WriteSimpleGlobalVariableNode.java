/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.globals;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

public abstract class WriteSimpleGlobalVariableNode extends RubyBaseNode {

    protected final String name;

    @NeverDefault
    public static WriteSimpleGlobalVariableNode create(String name) {
        return WriteSimpleGlobalVariableNodeGen.create(name);
    }

    public WriteSimpleGlobalVariableNode(String name) {
        this.name = name;
    }

    public abstract Object execute(Object value);

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "referenceEqualNode.execute(node, value, previousValue)" },
            assumptions = {
                    "storage.getUnchangedAssumption()",
                    "getLanguage().getGlobalVariableNeverAliasedAssumption(index)" },
            limit = "1")
    static Object writeTryToKeepConstant(Object value,
            @Cached ReferenceEqualNode referenceEqualNode,
            @Cached(value = "getLanguage().getGlobalVariableIndex(name)", neverDefault = false) @Shared int index,
            @Cached("getContext().getGlobalVariableStorage(index)") GlobalVariableStorage storage,
            @Cached("storage.getValue()") Object previousValue,
            @Bind Node node) {
        // NOTE: we still do the volatile write to get the proper memory barrier,
        // as the global variable could be used as a publication mechanism.
        storage.setValueInternal(value);
        return previousValue;
    }

    @Specialization(
            guards = { "isSingleContext()", "storage.isAssumeConstant()" },
            assumptions = {
                    "storage.getUnchangedAssumption()",
                    "getLanguage().getGlobalVariableNeverAliasedAssumption(index)" })
    Object writeAssumeConstant(Object value,
            @Cached @Shared WriteBarrierNode writeBarrierNode,
            @Cached(value = "getLanguage().getGlobalVariableIndex(name)", neverDefault = false) @Shared int index,
            @Cached("getContext().getGlobalVariableStorage(index)") GlobalVariableStorage storage) {
        if (getContext().getSharedObjects().isSharing()) {
            writeBarrierNode.execute(this, value);
        }
        storage.setValueInternal(value);
        storage.updateAssumeConstant(getContext());
        return value;
    }

    @Specialization(replaces = "writeAssumeConstant")
    Object writeAliasedOrMultiContext(Object value,
            @Cached @Shared WriteBarrierNode writeBarrierNode,
            @Cached("create(name)") LookupGlobalVariableStorageNode lookupGlobalVariableStorageNode) {
        if (getContext().getSharedObjects().isSharing()) {
            writeBarrierNode.execute(this, value);
        }

        final GlobalVariableStorage storage = lookupGlobalVariableStorageNode.execute(null);

        storage.setValueInternal(value);
        if (storage.isAssumeConstant()) {
            storage.updateAssumeConstant(getContext());
        }
        return value;
    }

}
