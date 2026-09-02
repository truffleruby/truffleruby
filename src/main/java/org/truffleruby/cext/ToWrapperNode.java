/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import static org.truffleruby.cext.ValueWrapperManager.FALSE_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.TRUE_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.UNDEF_HANDLE;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

/** Returns the ValueWrapper for a native VALUE handle */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(ValueWrapperManager.class)
public abstract class ToWrapperNode extends RubyBaseNode {

    /** Returns null for invalid handles */
    public abstract ValueWrapper execute(Node node, long handle);

    @Specialization(guards = "handle == FALSE_HANDLE")
    static ValueWrapper unwrapFalse(long handle) {
        return new ValueWrapper(false, FALSE_HANDLE, null);
    }

    @Specialization(guards = "handle == TRUE_HANDLE")
    static ValueWrapper unwrapTrue(long handle) {
        return new ValueWrapper(true, TRUE_HANDLE, null);
    }

    @Specialization(guards = "handle == UNDEF_HANDLE")
    static ValueWrapper unwrapUndef(long handle) {
        return new ValueWrapper(NotProvided.INSTANCE, UNDEF_HANDLE, null);
    }

    @Specialization(guards = "handle == NIL_HANDLE")
    static ValueWrapper unwrapNil(long handle) {
        return nil.getValueWrapper();
    }

    @Specialization(guards = "isTaggedLong(handle)")
    static ValueWrapper unwrapTaggedLong(long handle) {
        return new ValueWrapper(null, handle, null);
    }

    @Specialization(guards = "isTaggedObject(handle)")
    static ValueWrapper unwrapTaggedObject(Node node, long handle) {
        return getContext(node).getValueWrapperManager().getWrapperFromHandleMap(handle, true, getLanguage(node));
    }

    @Fallback
    static ValueWrapper unWrapUnexpectedHandle(long handle) {
        return null;
    }
}
