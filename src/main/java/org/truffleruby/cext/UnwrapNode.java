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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

/** Unwraps a native VALUE handle to the corresponding Ruby object */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(ValueWrapperManager.class)
public abstract class UnwrapNode extends RubyBaseNode {

    public static Object executeUncached(long handle) {
        return UnwrapNodeGen.getUncached().execute(null, handle);
    }

    public abstract Object execute(Node node, long handle);

    @Specialization(guards = "handle == FALSE_HANDLE")
    static boolean unwrapFalse(long handle) {
        return false;
    }

    @Specialization(guards = "handle == TRUE_HANDLE")
    static boolean unwrapTrue(long handle) {
        return true;
    }

    @Specialization(guards = "handle == UNDEF_HANDLE")
    static NotProvided unwrapUndef(long handle) {
        return NotProvided.INSTANCE;
    }

    @Specialization(guards = "handle == NIL_HANDLE")
    static Object unwrapNil(long handle) {
        return nil;
    }

    @Specialization(guards = "isTaggedLong(handle)")
    static long unwrapTaggedLong(long handle) {
        return ValueWrapperManager.untagTaggedLong(handle);
    }

    @Specialization(guards = "isTaggedObject(handle)")
    static Object unwrapTaggedObject(Node node, long handle) {
        final ValueWrapper wrapper = getContext(node)
                .getValueWrapperManager()
                .getWrapperFromHandleMap(handle, false, getLanguage(node));
        if (wrapper == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            deadHandle(handle);
        }
        return wrapper.getObject();
    }

    @Fallback
    static ValueWrapper unWrapUnexpectedHandle(long handle) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw CompilerDirectives.shouldNotReachHere("corrupt handle 0x" + Long.toHexString(handle));
    }

    @TruffleBoundary
    private static void deadHandle(long handle) {
        throw CompilerDirectives.shouldNotReachHere("dead handle 0x" + Long.toHexString(handle));
    }
}
