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
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.FinalizerReference;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.Shape;

@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached // for ShareObjectNode#createWriteBarrierNodes, ShareInternalFieldsNode#shareCachedQueue
@ReportPolymorphism // inline cache
public abstract class WriteBarrierNode extends RubyBaseNode {

    public static final WriteBarrierNode[] EMPTY_ARRAY = new WriteBarrierNode[0];

    protected static final int MAX_DEPTH = 3;

    protected final void execute(Node node, Object value, int depth) {
        CompilerAsserts.partialEvaluationConstant(depth);
        executeInternal(node, value, depth);
    }

    public final void execute(Node node, Object value) {
        execute(node, value, 0);
    }

    public final void executeCached(Object value, int depth) {
        execute(this, value, depth);
    }

    protected abstract void executeInternal(Node node, Object value, int depth);

    @Specialization(guards = { "!isRubyDynamicObject(value)", "!isFinalizer(value)" })
    static void noWriteBarrier(Node node, Object value, int depth) {
    }

    @Specialization(
            guards = { "value.getShape() == cachedShape", "cachedShape.isShared()" },
            limit = "1") // limit of 1 as the next specialization is cheap
    static void alreadySharedCached(RubyDynamicObject value, int depth,
            @Cached("value.getShape()") Shape cachedShape) {
    }

    @Specialization(guards = "value.getShape().isShared()", replaces = "alreadySharedCached")
    static void alreadySharedUncached(RubyDynamicObject value, int depth) {
    }

    @Specialization(
            guards = { "depth < MAX_DEPTH", "value.getShape() == cachedShape", "!cachedShape.isShared()" },
            // limit of 1 to avoid creating many nodes if the value's Shape is polymorphic.
            limit = "1")
    static void writeBarrierCached(Node node, RubyDynamicObject value, int depth,
            @Cached("value.getShape()") Shape cachedShape,
            @Cached ShareObjectNode shareObjectNode) {
        shareObjectNode.execute(node, value, depth + 1);
    }

    @Specialization(guards = "!value.getShape().isShared()",
            replaces = "writeBarrierCached")
    static void writeBarrierUncached(Node node, RubyDynamicObject value, int depth) {
        SharedObjects.writeBarrier(getLanguage(node), value);
    }

    @Specialization
    @TruffleBoundary
    static void writeBarrierFinalizer(Node node, FinalizerReference ref, int depth) {
        ArrayList<Object> roots = new ArrayList<>();
        ref.collectRoots(roots);
        for (var root : roots) {
            SharedObjects.writeBarrier(getLanguage(node), root);
        }
    }

    protected static boolean isFinalizer(Object object) {
        return object instanceof FinalizerReference;
    }

}
