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
package org.truffleruby.core.support;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.WriteObjectFieldNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

@CoreModule("Truffle::WeakRefOperations")
public abstract class WeakRefNodes {

    private static final TruffleWeakReference<Object> EMPTY_WEAK_REF = new TruffleWeakReference<>(null);
    private static final HiddenKey FIELD_NAME = new HiddenKey("weak_ref");

    @Primitive(name = "weakref_set_object")
    public abstract static class WeakRefSetObjectPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object weakRefSetObject(RubyDynamicObject weakRef, Object object,
                @Cached WriteObjectFieldNode fieldNode) {
            fieldNode.execute(this, weakRef, FIELD_NAME, new TruffleWeakReference<>(object));
            return object;
        }

    }

    @Primitive(name = "weakref_object")
    public abstract static class WeakRefObjectPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        Object weakRefObject(RubyDynamicObject weakRef,
                @CachedLibrary("weakRef") DynamicObjectLibrary objectLibrary) {
            final TruffleWeakReference<?> ref = (TruffleWeakReference<?>) objectLibrary
                    .getOrDefault(weakRef, FIELD_NAME, EMPTY_WEAK_REF);
            final Object object = ref.get();
            return object == null ? nil : object;
        }
    }
}
