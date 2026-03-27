/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.dispatch.DispatchNode;

// Casting of enumerable that is supposed to respond to the #to_a method to RubyArray
@NodeChild(value = "childNode", type = RubyBaseNodeWithExecute.class)
public abstract class ToANode extends RubyBaseNodeWithExecute {

    @NeverDefault
    public static ToANode create() {
        return ToANodeGen.create(null);
    }

    public static ToANode create(RubyBaseNodeWithExecute child) {
        return ToANodeGen.create(child);
    }

    public abstract RubyArray executeToA(Object object);

    abstract RubyBaseNodeWithExecute getChildNode();

    @Specialization
    RubyArray toA(RubyArray array) {
        return array;
    }

    @Specialization(guards = "!isRubyArray(object)")
    RubyArray coerceObject(Object object,
            @Cached DispatchNode toAryNode) {
        return (RubyArray) toAryNode.call(
                coreLibrary().truffleTypeModule,
                "rb_convert_type_fallback",
                object,
                coreLibrary().arrayClass,
                coreSymbols().TO_A);
    }

    @Override
    public RubyBaseNodeWithExecute cloneUninitialized() {
        return create(getChildNode().cloneUninitialized());
    }

}
