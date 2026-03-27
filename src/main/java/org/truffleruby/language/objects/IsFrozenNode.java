/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.core.range.RubyObjectRange;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import static org.truffleruby.Layouts.FROZEN_FLAG;

// Specializations are ordered by their frequency on railsbench using --engine.SpecializationStatistics
@GenerateUncached
public abstract class IsFrozenNode extends RubyBaseNode {

    public abstract boolean execute(Object object);

    @Specialization
    boolean isFrozen(ImmutableRubyObject object) {
        return true;
    }

    @Specialization
    boolean isFrozen(RubyString object) {
        return object.frozen;
    }

    @Specialization(guards = { "!isRubyObjectRange(object)", "isNotRubyString(object)" },
            limit = "getDynamicObjectCacheLimit()")
    boolean isFrozenCached(RubyDynamicObject object,
            @CachedLibrary("object") DynamicObjectLibrary objectLibrary) {
        return (objectLibrary.getShapeFlags(object) & FROZEN_FLAG) != 0;
    }

    // Avoid the uncached DynamicObjectLibrary: this is much faster as it does not have any Shape check/library overhead
    @Specialization(guards = { "!isRubyObjectRange(object)", "isNotRubyString(object)" }, replaces = "isFrozenCached")
    boolean isFrozenUncached(RubyDynamicObject object) {
        return (object.getShape().getFlags() & FROZEN_FLAG) != 0;
    }

    @Specialization
    boolean isFrozen(RubyObjectRange object) {
        return object.frozen;
    }


    @Specialization
    boolean isFrozen(boolean object) {
        return true;
    }

    @Specialization
    boolean isFrozen(int object) {
        return true;
    }

    @Specialization
    boolean isFrozen(long object) {
        return true;
    }

    @Specialization
    boolean isFrozen(double object) {
        return true;
    }

}
