/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.range;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.StringUtils;

@ExportLibrary(InteropLibrary.class)
public final class RubyIntRange extends RubyIntOrLongRange {

    public final int begin;
    public final int end;

    public RubyIntRange(boolean excludedEnd, int begin, int end) {
        super(excludedEnd);
        this.begin = begin;
        this.end = end;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (excludedEnd) {
            return StringUtils.format("%d...%d", begin, end);
        } else {
            return StringUtils.format("%d..%d", begin, end);
        }
    }

    // region InteropLibrary messages
    @Override
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        return toString();
    }

    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().rangeClass;
    }
    // endregion

}
