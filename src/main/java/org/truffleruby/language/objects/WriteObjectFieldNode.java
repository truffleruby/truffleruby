/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.language.objects.shared.WriteBarrierNode;

import java.lang.invoke.VarHandle;

@ReportPolymorphism // should probably be removed
@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class WriteObjectFieldNode extends RubyBaseNode {

    public abstract void execute(Node node, RubyDynamicObject object, Object name, Object value);

    @Specialization(guards = "!objectLibrary.isShared(object)", limit = "getDynamicObjectCacheLimit()")
    static void writeLocal(RubyDynamicObject object, Object name, Object value,
            @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
        objectLibrary.put(object, name, value);
    }

    @Specialization(guards = "objectLibrary.isShared(object)")
    static void writeShared(Node node, RubyDynamicObject object, Object name, Object value,
            @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary,
            @Cached WriteBarrierNode writeBarrierNode) {

        // Share `value` before it becomes reachable through `object`
        writeBarrierNode.execute(node, value);

        /* We need a STORE_STORE memory barrier here, to ensure the value is seen as shared by all threads when
         * published below by writing the value to a field of the object. Otherwise, the compiler could theoretically
         * move the write barrier inside the synchronized block, and then the compiler or hardware could potentially
         * reorder the writes so that publication would happen before sharing. */
        VarHandle.storeStoreFence();

        synchronized (object) {
            objectLibrary.put(object, name, value);
        }
    }
}
