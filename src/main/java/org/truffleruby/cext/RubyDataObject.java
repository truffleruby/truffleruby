/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import java.lang.invoke.MethodHandles;

import org.truffleruby.core.DataObjectFinalizerReference;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.object.Shape;

/** A {@code T_DATA} object, i.e. an object created by a C extension to wrap a native struct with
 * {@code rb_data_object_wrap()} or {@code rb_data_typed_object_wrap()}. Only classes which otherwise use the
 * {@link org.truffleruby.core.basicobject.RubyBasicObject} layout can have {@code T_DATA} instances.
 *
 * The mark, free and size functions are not stored here as they are read from the native struct (or from its
 * {@code rb_data_type_t}) every time they are needed, since C extensions can change them after the object is
 * created. */
public final class RubyDataObject extends RubyDynamicObject {

    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // Same number of inline fields as RubyBasicObject
    @DynamicField private long primitive1;
    @DynamicField private long primitive2;
    @DynamicField private long primitive3;
    @DynamicField private Object object1;
    @DynamicField private Object object2;
    @DynamicField private Object object3;
    @DynamicField private Object object4;

    /** The address of the native struct RData or struct RTypedData */
    public final long dataStruct;
    /** Whether {@link #dataStruct} is a struct RTypedData (true) or a struct RData (false) */
    public final boolean typed;
    /** Whether the functions of the native struct should be run with the C extension lock, which depends on the context
     * in which this object was created. */
    public final boolean useCExtLock;
    /** Runs the free function and frees the native struct once this object is no longer reachable */
    public DataObjectFinalizerReference finalizerReference;
    /** The ValueWrapper of the objects marked by the last run of the mark function, to keep them alive as long as this
     * object is alive. */
    public Object[] markedObjects;

    public RubyDataObject(RubyClass rubyClass, Shape shape, long dataStruct, boolean typed, boolean useCExtLock) {
        super(rubyClass, shape);
        this.dataStruct = dataStruct;
        this.typed = typed;
        this.useCExtLock = useCExtLock;
    }

}
