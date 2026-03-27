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
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.collections.BoundaryIterable;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.basicobject.RubyBasicObject;
import org.truffleruby.core.queue.RubyQueue;
import org.truffleruby.core.queue.UnsizedQueue;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

/** Share the plain Java fields which may contain objets for subclasses of RubyDynamicObject.
 * {@link RubyDynamicObject#metaClass} is handled by {@link ShareObjectNode}. */
@GenerateInline
@GenerateCached(false)
@ImportStatic(ArrayGuards.class)
public abstract class ShareInternalFieldsNode extends RubyBaseNode {

    protected static final int CACHE_LIMIT = 8;

    public final void execute(Node node, RubyDynamicObject object, int depth) {
        CompilerAsserts.partialEvaluationConstant(depth);
        executeInternal(node, object, depth);
    }

    protected abstract void executeInternal(Node node, RubyDynamicObject object, int depth);

    @Specialization(limit = "CACHE_LIMIT")
    static void shareArray(RubyArray array, int depth,
            @Bind("array.getStore()") Object store,
            @CachedLibrary("store") ArrayStoreLibrary stores) {
        array.setStore(stores.makeShared(store, array.size));
    }

    @Specialization
    static void shareCachedQueue(Node node, RubyQueue object, int depth,
            @Cached InlinedConditionProfile profileEmpty,
            @Cached(inline = false) WriteBarrierNode writeBarrierNode) {
        // WriteBarrierNode inline = false to fix recursive inlining:
        // WriteBarrierNode->ShareObjectNode->ShareInternalFieldsNode->WriteBarrierNode
        final UnsizedQueue queue = object.queue;
        if (!profileEmpty.profile(node, queue.isEmpty())) {
            for (Object e : BoundaryIterable.wrap(queue.getContents())) {
                writeBarrierNode.executeCached(e, depth);
            }
        }
    }

    @Specialization
    static void shareCachedBasicObject(RubyBasicObject object, int depth) {
        /* No extra Java fields for RubyBasicObject */
    }

    @Specialization(
            replaces = {
                    "shareArray",
                    "shareCachedQueue",
                    "shareCachedBasicObject" })
    static void shareUncached(RubyDynamicObject object, int depth) {
        SharedObjects.shareInternalFields(object);
    }

}
