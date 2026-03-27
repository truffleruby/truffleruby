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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

/** Creates a symbol from a string. Must be a RubyNode because it's used in the translator. */
@NodeChild(value = "stringNode", type = RubyNode.class)
public abstract class StringToSymbolNode extends RubyContextSourceNode {

    abstract RubyNode getStringNode();

    @Specialization
    RubySymbol doString(Object string,
            @Cached RubyStringLibrary libString) {
        return getSymbol(libString.getTString(this, string), libString.getEncoding(this, string));
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = StringToSymbolNodeGen.create(getStringNode().cloneUninitialized());
        return copy.copyFlags(this);
    }

}
