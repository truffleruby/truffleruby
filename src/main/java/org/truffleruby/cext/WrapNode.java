/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.cext;

import static org.truffleruby.cext.ValueWrapperManager.FALSE_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.LONG_TAG;
import static org.truffleruby.cext.ValueWrapperManager.NIL_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.TRUE_HANDLE;
import static org.truffleruby.cext.ValueWrapperManager.UNDEF_HANDLE;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.cext.ValueWrapperManager.CreateWrapperNode;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.ObjectIDOperations;

/** Converts a Ruby object to the VALUE handle the native code sees, the reverse of {@link UnwrapNode}. Immediates
 * (small fixnums, nil, true, false, undef) are encoded in the handle itself, other objects get a tagged object handle
 * through their {@link ValueWrapper}, kept alive until the end of the current C extension call. Use
 * {@link ObjectToWrapperNode} only when the ValueWrapper itself is needed. */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(ObjectIDOperations.class)
public abstract class WrapNode extends RubyBaseNode {

    public static long executeUncached(Object value) {
        return WrapNodeGen.getUncached().execute(null, value);
    }

    public abstract long execute(Node node, Object value);

    @Specialization(guards = "isSmallFixnum(value)")
    static long wrapFixnum(long value) {
        return (value << 1) | LONG_TAG;
    }

    /** A boxed Long or Double cannot reference a ValueWrapper like RubyDynamicObject and ImmutableRubyObject do, so it
     * gets a new wrapper every time, which holds the box. */
    @Specialization(guards = "isNonFixnumLongOrDouble(value)")
    static long wrapNonFixnumLongOrDouble(Node node, Object value,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Cached @Shared KeepAliveNode keepAliveNode) {
        return keepAlive(node, createWrapperNode.execute(node, value), keepAliveNode);
    }

    @Specialization
    static long wrapBoolean(boolean value) {
        return value ? TRUE_HANDLE : FALSE_HANDLE;
    }

    @Specialization
    static long wrapUndef(NotProvided value) {
        return UNDEF_HANDLE;
    }

    @Specialization
    static long wrapNil(Nil value) {
        return NIL_HANDLE;
    }

    @Specialization(guards = "!isNil(value)")
    static long wrapImmutable(Node node, ImmutableRubyObject value,
            @Cached @Shared InlinedBranchProfile noHandleProfile,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Cached @Shared KeepAliveNode keepAliveNode) {
        ValueWrapper wrapper = value.getValueWrapper();
        if (wrapper == null) {
            noHandleProfile.enter(node);
            /* The racy initial read is safe because ValueWrapper only has final fields, so it sees either null or a
             * fully-initialized ValueWrapper. If two threads race, one wrapper wins and the other remains unused in its
             * HandleBlock, which is harmless. */
            wrapper = value.setValueWrapperIfAbsent(createWrapperNode.execute(node, value));
        }
        return keepAlive(node, wrapper, keepAliveNode);
    }

    @Specialization
    static long wrapValue(Node node, RubyDynamicObject value,
            @Cached @Shared InlinedBranchProfile noHandleProfile,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Cached @Shared KeepAliveNode keepAliveNode) {
        ValueWrapper wrapper = value.getValueWrapper();
        if (wrapper == null) {
            noHandleProfile.enter(node);
            /* The racy initial read is safe because ValueWrapper only has final fields, so it sees either null or a
             * fully-initialized ValueWrapper. If two threads race, one wrapper wins and the other remains unused in its
             * HandleBlock, which is harmless. */
            wrapper = value.setValueWrapperIfAbsent(createWrapperNode.execute(node, value));
        }
        return keepAlive(node, wrapper, keepAliveNode);
    }

    @Specialization(guards = "isForeignObject(value)")
    static long wrapNonRubyObject(Node node, Object value) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node).argumentError("Attempt to wrap something that isn't an Ruby object", node));
    }

    protected static boolean isNonFixnumLongOrDouble(Object value) {
        return (value instanceof Long && !ObjectIDOperations.isSmallFixnum((long) value)) || value instanceof Double;
    }

    /** The wrapper of a tagged object handle must be kept alive until the end of the current C extension call, as
     * nothing else references it (and for boxed Long/Double wrappers, the box) once the handle is in C. */
    private static long keepAlive(Node node, ValueWrapper wrapper, KeepAliveNode keepAliveNode) {
        keepAliveNode.execute(node, wrapper);
        return wrapper.handle;
    }
}
