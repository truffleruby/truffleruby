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
import org.truffleruby.core.array.AssignableNode;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;
import org.truffleruby.core.string.FrozenStrings;
import org.truffleruby.language.RubyContextSourceAssignableNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class WriteGlobalVariableNode extends RubyContextSourceAssignableNode {

    protected final String name;
    @Child LookupGlobalVariableStorageNode lookupGlobalVariableStorageNode;

    public WriteGlobalVariableNode(String name) {
        this.name = name;
        lookupGlobalVariableStorageNode = LookupGlobalVariableStorageNode.create(name);
    }

    public abstract Object execute(VirtualFrame frame, Object value);

    @Override
    public void assign(VirtualFrame frame, Object value) {
        execute(frame, value);
    }

    @Specialization(guards = "storage.isSimple()")
    Object write(VirtualFrame frame, Object value,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Cached("create(name)") WriteSimpleGlobalVariableNode simpleNode) {
        simpleNode.execute(value);
        return value;
    }

    @Specialization(guards = { "storage.hasHooks()", "arity != 3" })
    Object writeHooks(VirtualFrame frame, Object value,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Bind("setterArity(storage)") int arity,
            @Bind("name") String boundName,
            @Cached @Exclusive CallBlockNode yieldNode) {
        yieldNode.yield(this, storage.getSetter(), boundName, value);
        return value;
    }

    @Specialization(guards = { "storage.hasHooks()", "arity == 3" })
    static Object writeHooksWithStorage(VirtualFrame frame, Object value,
            @Bind("getStorage(frame)") GlobalVariableStorage storage,
            @Bind("setterArity(storage)") int arity,
            @Bind("name") String boundName,
            @Cached @Exclusive CallBlockNode yieldNode,
            @Cached GetSpecialVariableStorage storageNode,
            @Bind Node node) {
        yieldNode.yield(node,
                storage.getSetter(),
                boundName,
                value,
                storageNode.execute(frame, node));
        return value;
    }

    protected int setterArity(GlobalVariableStorage storage) {
        return storage.getSetter().getArityNumber();
    }

    protected GlobalVariableStorage getStorage(VirtualFrame dynamicArgument) {
        return lookupGlobalVariableStorageNode.execute(dynamicArgument);
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context) {
        return FrozenStrings.ASSIGNMENT;
    }

    @Override
    public AssignableNode toAssignableNode() {
        // Cannot reassign a @NodeChild
        final WriteGlobalVariableNode node = WriteGlobalVariableNodeGen.create(name, null);
        node.copySourceSection(this);
        return node;
    }

    @Override
    public AssignableNode cloneUninitializedAssignable() {
        return (AssignableNode) cloneUninitialized();
    }

    abstract RubyNode getValueNode();

    @Override
    public RubyNode cloneUninitialized() {
        var copy = WriteGlobalVariableNodeGen.create(name, cloneUninitialized(getValueNode()));
        return copy.copyFlags(this);
    }
}
