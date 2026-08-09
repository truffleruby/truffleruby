/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

/** Creates a symbol from an interpolated string produced by the child node. */
@NodeChild(value = "stringNode", type = RubyNode.class)
public abstract class InterpolatedStringToSymbolNode extends RubyContextSourceNode {

    abstract RubyNode getStringNode();

    @Specialization
    RubySymbol doString(Object string,
            @Cached StringToSymbolNode stringToSymbolNode) {
        return stringToSymbolNode.execute(this, string, false);
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InterpolatedStringToSymbolNodeGen.create(getStringNode().cloneUninitialized());
        return copy.copyFlags(this);
    }
}
