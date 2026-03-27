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
package org.truffleruby.language.objects;

import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.NoImplicitCastsToLong;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

// Specializations are ordered by their frequency on railsbench using --engine.SpecializationStatistics
@GenerateUncached
@TypeSystemReference(NoImplicitCastsToLong.class)
public abstract class LogicalClassNode extends RubyBaseNode {

    @NeverDefault
    public static LogicalClassNode create() {
        return LogicalClassNodeGen.create();
    }

    public static LogicalClassNode getUncached() {
        return LogicalClassNodeGen.getUncached();
    }

    public abstract RubyClass execute(Object value);

    @Specialization
    RubyClass logicalClassObject(RubyDynamicObject object) {
        return object.getLogicalClass();
    }

    @Specialization(guards = "isPrimitiveOrImmutable(value)")
    RubyClass logicalClassImmutable(Object value,
            @Cached ImmutableClassNode immutableClassNode) {
        return immutableClassNode.execute(this, value);
    }

    @InliningCutoff
    @Specialization(guards = "isForeignObject(object)")
    RubyClass logicalClassForeign(Object object,
            @Cached ForeignClassNode foreignClassNode) {
        return foreignClassNode.execute(this, object);
    }

    protected int getCacheLimit() {
        return getLanguage().options.CLASS_CACHE;
    }

}
