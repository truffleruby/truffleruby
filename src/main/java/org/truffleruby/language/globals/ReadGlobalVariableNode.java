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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ReadGlobalVariableNode extends RubyContextSourceNode {

    public final String name;
    @Child LookupGlobalVariableStorageNode lookupGlobalVariableStorageNode;
    @Child private IsDefinedGlobalVariableNode definedNode;

    public ReadGlobalVariableNode(String name) {
        this.name = name;
        lookupGlobalVariableStorageNode = LookupGlobalVariableStorageNode.create(name);
    }

    @Specialization(guards = "storage.isSimple()")
    Object read(VirtualFrame frame,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Cached("create(name)") ReadSimpleGlobalVariableNode simpleNode) {
        return simpleNode.execute();
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 0" })
    Object readHooks(VirtualFrame frame,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Bind("getterArity(storage)") int arity,
            @Cached @Exclusive CallBlockNode yieldNode) {
        return yieldNode.yield(this, storage.getGetter());
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 1" })
    static Object readHooksWithStorage(VirtualFrame frame,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Bind("getterArity(storage)") int arity,
            @Cached @Exclusive CallBlockNode yieldNode,
            @Cached GetSpecialVariableStorage storageNode,
            @Bind Node node) {
        return yieldNode.yield(node, storage.getGetter(), storageNode.execute(frame, node));
    }

    protected int getterArity(GlobalVariableStorage storage) {
        return storage.getGetter().getArityNumber();
    }

    protected GlobalVariableStorage getStorage(VirtualFrame dynamicArgument) {
        return lookupGlobalVariableStorageNode.execute(dynamicArgument);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        if (definedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            definedNode = insert(IsDefinedGlobalVariableNode.create(name));
        }

        return definedNode.executeIsDefined(frame);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = ReadGlobalVariableNodeGen.create(name);
        return copy.copyFlags(this);
    }

}
