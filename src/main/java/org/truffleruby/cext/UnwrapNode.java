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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import org.truffleruby.core.cast.ToPointerAddressNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(ValueWrapperManager.class)
public abstract class UnwrapNode extends RubyBaseNode {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ValueWrapperManager.class)
    public abstract static class UnwrapNativeNode extends RubyBaseNode {

        public static Object executeUncached(long handle) {
            return UnwrapNodeGen.UnwrapNativeNodeGen.getUncached().execute(null, handle);
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

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(ValueWrapperManager.class)
    public abstract static class NativeToWrapperNode extends RubyBaseNode {

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

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToWrapperNode extends RubyBaseNode {

        /** Returns null for invalid handles */
        public abstract ValueWrapper execute(Node node, Object value);

        @Specialization
        static ValueWrapper wrappedValueWrapper(ValueWrapper value) {
            return value;
        }

        @Specialization
        static ValueWrapper longToWrapper(Node node, Object value,
                @Cached ToPointerAddressNode toPointerAddressNode,
                @Cached NativeToWrapperNode nativeToWrapperNode) {
            long address = toPointerAddressNode.execute(node, value);
            return nativeToWrapperNode.execute(node, address);
        }
    }

    /** Unwraps a native VALUE[] (an address and a length) to the corresponding Ruby objects */
    public abstract static class UnwrapCArrayNode extends RubyBaseNode {

        public abstract Object[] execute(long address, int size);

        @ExplodeLoop
        @Specialization(
                guards = { "size == cachedSize", "cachedSize <= MAX_EXPLODE_SIZE" },
                limit = "1")
        Object[] unwrapCArrayExplode(long address, int size,
                @Cached("size") int cachedSize,
                @Cached @Shared UnwrapNode unwrapNode) {
            final Object[] store = new Object[cachedSize];
            for (int i = 0; i < cachedSize; i++) {
                store[i] = unwrapNode.execute(this, Pointer.rawReadLong(address + i * 8L));
            }
            return store;
        }

        @Specialization(replaces = "unwrapCArrayExplode")
        Object[] unwrapCArray(long address, int size,
                @Cached @Shared UnwrapNode unwrapNode,
                @Cached LoopConditionProfile loopProfile) {
            final Object[] store = new Object[size];
            int i = 0;
            try {
                for (; loopProfile.inject(i < size); i++) {
                    store[i] = unwrapNode.execute(this, Pointer.rawReadLong(address + i * 8L));
                    TruffleSafepoint.poll(this);
                }
            } finally {
                profileAndReportLoopCount(loopProfile, i);
            }
            return store;
        }
    }

    public abstract Object execute(Node node, Object value);

    @Specialization(guards = "!isTaggedLong(value.getHandle())")
    static Object unwrapValueObject(ValueWrapper value) {
        return value.getObject();
    }

    @Specialization(guards = "isTaggedLong(value.getHandle())")
    static long unwrapValueTaggedLong(ValueWrapper value) {
        return ValueWrapperManager.untagTaggedLong(value.getHandle());
    }

    @Specialization(guards = "!isWrapper(value)")
    static Object unwrapGeneric(Node node, Object value,
            @Cached ToPointerAddressNode toPointerAddressNode,
            @Cached UnwrapNativeNode unwrapNativeNode) {
        long handle = toPointerAddressNode.execute(node, value);
        return unwrapNativeNode.execute(node, handle);
    }

    protected int getCacheLimit() {
        return getLanguage().options.DISPATCH_CACHE;
    }
}
