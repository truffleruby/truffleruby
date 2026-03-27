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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.PropertyGetter;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.utils.RunTwiceBranchProfile;

/** Share the object and all that is reachable from it (see {@link ObjectGraph#getAdjacentObjects}) */
@GenerateInline
@GenerateCached(false)
@ReportPolymorphism // inline cache
public abstract class ShareObjectNode extends RubyBaseNode {

    protected static final int CACHE_LIMIT = 8;

    public final void execute(Node node, RubyDynamicObject object, int depth) {
        CompilerAsserts.partialEvaluationConstant(depth);
        executeInternal(node, object, depth);
    }

    protected abstract void executeInternal(Node node, RubyDynamicObject object, int depth);

    /* GR-49349: we use getValidAssumption() here to ensure the Shape is not obsolete, because if it is we would need to
     * migrate first to the non-obsolete Shape and then to the shared Shape. That would be complicated, so we only use
     * this inline cache if the Shape is not obsolete. */
    @ExplodeLoop
    @Specialization(
            guards = { "object.getShape() == cachedShape", "propertyGetters.length <= MAX_EXPLODE_SIZE" },
            assumptions = { "cachedShape.getValidAssumption()", "sharedShape.getValidAssumption()" },
            limit = "CACHE_LIMIT")
    static void shareCached(Node node, RubyDynamicObject object, int depth,
            @Cached("object.getShape()") Shape cachedShape,
            @Cached("createSharedShape(cachedShape)") Shape sharedShape,
            @CachedLibrary(limit = "1") DynamicObjectLibrary objectLibrary,
            @Cached("new()") RunTwiceBranchProfile shareMetaClassProfile,
            @Cached ShareInternalFieldsNode shareInternalFieldsNode,
            @Cached(value = "getObjectProperties(sharedShape)", dimensions = 1) PropertyGetter[] propertyGetters,
            @Cached("createWriteBarrierNodes(propertyGetters)") WriteBarrierNode[] writeBarrierNodes) {
        // Mark the object as shared first to avoid recursion
        assert object.getShape() == cachedShape;
        objectLibrary.markShared(object);
        assert object.getShape() == sharedShape;

        // Share the metaclass. This will also the share the logical class, which is the same or its superclass.
        // Note that the metaclass might refer to `object` via `attached`, so it is important to share the object first.
        if (!object.getMetaClass().getShape().isShared()) {
            shareMetaClassProfile.enter();
            SharedObjects.writeBarrier(getLanguage(node), object.getMetaClass());
        }
        assert SharedObjects
                .isShared(object.getLogicalClass()) : "the logical class should have been shared by the metaclass";

        shareInternalFieldsNode.execute(node, object, depth);

        for (int i = 0; i < propertyGetters.length; i++) {
            final PropertyGetter propertyGetter = propertyGetters[i];
            final Object value = propertyGetter.get(object);
            writeBarrierNodes[i].executeCached(value, depth);
        }

        assert allFieldsAreShared(object);
    }

    private static boolean allFieldsAreShared(RubyDynamicObject object) {
        for (Object value : ObjectGraph.getAdjacentObjects(object)) {
            assert SharedObjects.isShared(value) : "unshared field in shared object: " + value;
        }

        return true;
    }

    @Specialization(replaces = "shareCached")
    static void shareUncached(Node node, RubyDynamicObject object, int depth) {
        SharedObjects.writeBarrier(getLanguage(node), object);
    }

    protected static PropertyGetter[] getObjectProperties(Shape shape) {
        final List<PropertyGetter> objectProperties = new ArrayList<>();
        for (Property property : shape.getPropertyListInternal(false)) {
            if (!property.getLocation().isPrimitive()) {
                objectProperties.add(Objects.requireNonNull(shape.makePropertyGetter(property.getKey())));
            }
        }
        return objectProperties.toArray(KernelNodes.CopyInstanceVariablesNode.EMPTY_PROPERTY_GETTER_ARRAY);
    }

    protected static WriteBarrierNode[] createWriteBarrierNodes(PropertyGetter[] propertyGetters) {
        WriteBarrierNode[] nodes = propertyGetters.length == 0
                ? WriteBarrierNode.EMPTY_ARRAY
                : new WriteBarrierNode[propertyGetters.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = WriteBarrierNodeGen.create();
        }
        return nodes;
    }

    protected static Shape createSharedShape(Shape cachedShape) {
        if (cachedShape.isShared()) {
            throw new UnsupportedOperationException(
                    "Thread-safety bug: the object is already shared. This means another thread marked the object as shared concurrently.");
        } else {
            return cachedShape.makeSharedShape();
        }
    }

}
