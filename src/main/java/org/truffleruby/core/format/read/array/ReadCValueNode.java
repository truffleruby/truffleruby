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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import org.truffleruby.cext.UnwrapNode;
import org.truffleruby.core.format.FormatNode;

@NodeChild(value = "source")
public abstract class ReadCValueNode extends FormatNode {

    @Specialization
    Object read(Object source,
            @Cached UnwrapNode unwrapNode) {
        return unwrapNode.execute(this, source);
    }
}
