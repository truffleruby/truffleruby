/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.builtins;

import org.truffleruby.annotations.Primitive;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.NotProvided;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import org.truffleruby.language.RubyNode;

@GenerateNodeFactory
public abstract class PrimitiveNode extends RubyContextSourceNode {

    // The same as "undefined" in Ruby code
    public static final Object FAILURE = NotProvided.INSTANCE;

    @Override
    public RubyNode cloneUninitialized() {
        String primitiveName = getClass().getSuperclass().getAnnotation(Primitive.class).name();
        var factory = getLanguage().primitiveManager.getPrimitive(primitiveName).getFactory();
        var copy = (PrimitiveNode) CoreMethodNodeManager.createNodeFromFactory(factory, RubyNode.EMPTY_ARRAY);
        return copy.copyFlags(this);
    }

}
