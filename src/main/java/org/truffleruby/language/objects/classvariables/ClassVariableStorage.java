/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.RubyLanguage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class ClassVariableStorage extends DynamicObject {

    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // Same number of inline fields as DynamicObjectBasic
    @DynamicField private long primitive1;
    @DynamicField private long primitive2;
    @DynamicField private long primitive3;
    @DynamicField private Object object1;
    @DynamicField private Object object2;
    @DynamicField private Object object3;
    @DynamicField private Object object4;

    public ClassVariableStorage(RubyLanguage language) {
        super(language.classVariableShape);
    }

    public Object read(String name, DynamicObjectLibrary objectLibrary) {
        final Object value = objectLibrary.getOrDefault(this, name, null);
        if (objectLibrary.isShared(this)) {
            // This extra fence is to ensure acquire-release semantics for class variables, so the read above behaves
            // like a load-acquire. There is a corresponding store-release barrier for class variables writes.
            // See https://preshing.com/20120913/acquire-and-release-semantics/ for where to put the memory fences.
            VarHandle.acquireFence(); // load-acquire
        }
        return value;
    }

    public void put(String name, Object value, DynamicObjectLibrary objectLibrary) {
        VarHandle.releaseFence(); // store-release
        synchronized (this) {
            objectLibrary.put(this, name, value);
        }
    }

    public boolean putIfPresent(String name, Object value, DynamicObjectLibrary objectLibrary) {
        VarHandle.releaseFence(); // store-release
        synchronized (this) {
            return objectLibrary.putIfPresent(this, name, value);
        }
    }

    public Object remove(String name, DynamicObjectLibrary objectLibrary) {
        final Object prev;
        synchronized (this) {
            prev = read(name, objectLibrary);
            if (prev != null) {
                VarHandle.releaseFence(); // store-release
                objectLibrary.removeKey(this, name);
            }
        }
        return prev;
    }

}
