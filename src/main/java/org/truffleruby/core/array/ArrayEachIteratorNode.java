/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInterface;
import org.truffleruby.language.yield.CallBlockNode;

@ImportStatic(ArrayGuards.class)
@ReportPolymorphism // for ArrayStoreLibrary
@GenerateInline(inlineByDefault = true)
public abstract class ArrayEachIteratorNode extends RubyBaseNode {

    public interface ArrayElementConsumerNode extends NodeInterface {
        void accept(Node node, CallBlockNode yieldNode, RubyArray array, Object state, Object element, int index,
                BooleanCastNode booleanCastNode);
    }

    public final RubyArray executeCached(RubyArray array, Object state, int startAt,
            ArrayElementConsumerNode consumerNode) {
        return execute(this, array, state, startAt, consumerNode);

    }

    public abstract RubyArray execute(Node node, RubyArray array, Object state, int startAt,
            ArrayElementConsumerNode consumerNode);

    @Specialization(limit = "storageStrategyLimit()")
    static RubyArray iterateMany(
            Node node, RubyArray array, Object state, int startAt, ArrayElementConsumerNode consumerNode,
            // Checkstyle: stop -- Verified @Bind is not necessary here due to using `Library#accepts()`.
            @CachedLibrary("array.getStore()") ArrayStoreLibrary stores,
            // Checkstyle: resume
            @Cached InlinedLoopConditionProfile loopProfile,
            @Cached InlinedIntValueProfile arraySizeProfile,
            @Cached InlinedConditionProfile strategyMatchProfile,
            @Cached LazyArrayEachIteratorNode lazyArrayEachIteratorNode,
            @Cached BooleanCastNode booleanCastNode,
            @Cached CallBlockNode yieldNode) {
        int i = startAt;
        try {
            for (; loopProfile.inject(node, i < arraySizeProfile.profile(node, array.size)); i++) {
                Object store = array.getStore();
                if (strategyMatchProfile.profile(node, stores.accepts(store))) {
                    consumerNode.accept(node, yieldNode, array, state, stores.read(store, i), i, booleanCastNode);
                } else {
                    return lazyArrayEachIteratorNode.get(node).executeCached(array, state, i, consumerNode);
                }
                TruffleSafepoint.poll(node);
            }
        } finally {
            profileAndReportLoopCount(node, loopProfile, i - startAt);
        }

        return array;
    }
}
