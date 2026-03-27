/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.ImmutableRubyObjectCopyable;
import org.truffleruby.language.ImmutableRubyObjectNotCopyable;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

/** Determines if an object is copyable for Kernel#clone, Kernel#dup, and rb_obj_clone. */
@GenerateUncached
public abstract class IsCopyableObjectNode extends RubyBaseNode {

    public abstract boolean execute(Object object);

    @Specialization
    boolean isCopyable(boolean object) {
        return false;
    }

    @Specialization
    boolean isCopyable(int object) {
        return false;
    }

    @Specialization
    boolean isCopyable(long object) {
        return false;
    }

    @Specialization
    boolean isCopyable(double object) {
        return false;
    }

    @Specialization
    boolean isCopyable(ImmutableRubyObjectNotCopyable object) {
        return false;
    }

    @Specialization
    boolean isCopyable(ImmutableRubyObjectCopyable object) {
        return true;
    }

    @Specialization
    boolean isCopyable(RubyDynamicObject object,
            @Cached LogicalClassNode logicalClassNode) {
        final RubyClass logicalClass = logicalClassNode.execute(object);
        return logicalClass != coreLibrary().rationalClass && logicalClass != coreLibrary().complexClass;
    }

}
