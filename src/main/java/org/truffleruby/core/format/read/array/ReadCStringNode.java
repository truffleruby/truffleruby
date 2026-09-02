/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.read.array;

import org.truffleruby.cext.UnwrapNode;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.yield.CallBlockNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild(value = "source", type = FormatNode.class)
@ImportStatic(ArrayGuards.class)
public abstract class ReadCStringNode extends FormatNode {

    protected final RubyProc stringReader;

    public ReadCStringNode(RubyProc stringReader) {
        this.stringReader = stringReader;
    }

    @Specialization
    Object read(Object pointer,
            @Cached UnwrapNode unwrapNode,
            @Cached CallBlockNode callBlockNode) {
        return unwrapNode.execute(this, (long) callBlockNode.yield(this, stringReader, pointer));
    }

}
