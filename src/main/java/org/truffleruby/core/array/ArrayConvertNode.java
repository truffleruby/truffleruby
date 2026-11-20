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
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

/** Like rb_ary_to_ary() in CRuby: converts its argument with rb_check_array_type(), or if it does not convert, wraps it
 * inside a one-element array. */
public abstract class ArrayConvertNode extends RubyBaseNode {

    public abstract RubyArray execute(Object value);

    @Specialization
    RubyArray castArray(RubyArray array) {
        return array;
    }

    @Specialization(guards = "!isRubyArray(object)")
    RubyArray cast(Object object,
            @Cached ConditionProfile canCast,
            @Cached ArrayBuilderNode arrayBuilder,
            @Cached DispatchNode toArrayNode) {
        final Object result = toArrayNode.call(
                coreLibrary().truffleTypeModule,
                "rb_check_convert_type",
                object,
                coreLibrary().arrayClass,
                coreSymbols().TO_ARY);
        if (canCast.profile(result != nil)) {
            return (RubyArray) result;
        } else {
            return ArrayHelpers.specializedRubyArrayOf(getContext(), getLanguage(), arrayBuilder, object);
        }
    }

}
