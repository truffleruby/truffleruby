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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class IsDefinedGlobalVariableNode extends RubyBaseNode {

    public static IsDefinedGlobalVariableNode create(String name) {
        return IsDefinedGlobalVariableNodeGen.create(name);
    }

    @Child LookupGlobalVariableStorageNode lookupGlobalVariableStorageNode;

    public IsDefinedGlobalVariableNode(String name) {
        lookupGlobalVariableStorageNode = LookupGlobalVariableStorageNode.create(name);
    }

    public abstract Object executeIsDefined(VirtualFrame frame);

    @Specialization(guards = "storage.isSimple()")
    Object simple(VirtualFrame frame,
            @Bind("getStorage(frame)") GlobalVariableStorage storage) {
        if (storage.isDefined()) {
            return FrozenStrings.GLOBAL_VARIABLE;
        } else {
            return nil;
        }
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 0" })
    static Object hooks(VirtualFrame frame,
            @Cached("getLanguage().getGlobalVariableIndex(lookupGlobalVariableStorageNode.name)") int index,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Cached("isDefinedArity(storage)") int arity,
            @Cached @Exclusive CallBlockNode yieldNode,
            @Bind Node node) {
        return yieldNode.yield(node, storage.getIsDefined());
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 1" })
    static Object hooksWithBinding(VirtualFrame frame,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Cached("isDefinedArity(storage)") int arity,
            @Cached @Exclusive CallBlockNode yieldNode,
            @Cached GetSpecialVariableStorage readStorage,
            @Bind Node node) {
        return yieldNode.yield(node, storage.getIsDefined(), readStorage.execute(frame, node));
    }

    protected int isDefinedArity(GlobalVariableStorage storage) {
        return storage.getIsDefined().getArityNumber();
    }

    protected GlobalVariableStorage getStorage(VirtualFrame dynamicArgument) {
        return lookupGlobalVariableStorageNode.execute(dynamicArgument);
    }

}
