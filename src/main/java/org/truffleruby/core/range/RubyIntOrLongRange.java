/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.range;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.language.ImmutableRubyObjectCopyable;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public abstract class RubyIntOrLongRange extends ImmutableRubyObjectCopyable {

    public final boolean excludedEnd;

    public RubyIntOrLongRange(boolean excludedEnd) {
        this.excludedEnd = excludedEnd;
    }

    // region Iterator messages
    @ExportMessage
    public boolean hasIterator() {
        return true;
    }

    @ExportMessage
    public Object getIterator(
            @Bind Node node,
            @Cached @Exclusive DispatchNode dispatchNode) {
        final RubyContext context = RubyContext.get(node);
        return dispatchNode.call(context.getCoreLibrary().truffleInteropOperationsModule, "get_iterator", this);
    }
    // endregion

}
