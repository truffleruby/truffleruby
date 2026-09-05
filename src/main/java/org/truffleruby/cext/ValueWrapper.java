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

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.cext.ValueWrapperManager.HandleBlock;
import org.truffleruby.cext.ValueWrapperManager.WrapperToHandleNode;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.debug.VariableNamesObject;
import org.truffleruby.interop.TranslateInteropExceptionNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/** Represents a VALUE in C: wraps a Ruby object and its {@link #handle}, the long the native code sees as the VALUE.
 * ValueWrappers cross the native boundary only as that long (see {@link ValueWrapperManager.WrapperToHandleNode}), so
 * this needs no pointer interop messages. The language id, display string and the "value" member are provided as we
 * always do, and are useful to inspect a ValueWrapper in the Truffle debugger.
 *
 * The wrapper IS the weak reference to its object, and is itself held strongly by its {@link HandleBlock}, so mapping a
 * handle back to a live object needs no extra WeakReference allocation per wrapper. The weak link must be on this side:
 * the object (which strongly references its wrapper) and the keep-alive lists in
 * {@link org.truffleruby.core.MarkingService} are what keep a wrapped object alive, never the handle map. The wrapper
 * is published racily (see WrapNode); that is safe because {@link #handle} and {@link #handleBlock} are final and the
 * referent is written in the {@link WeakReference} constructor before the final-fields freeze at the end of this
 * class's constructor. */
@ExportLibrary(InteropLibrary.class)
public final class ValueWrapper extends WeakReference<Object> implements TruffleObject {

    /** Consider using {@link WrapperToHandleNode} or {@link KeepAliveNode} when passing the handle to C */
    public final long handle;
    /** The handleBlock is held here to keep it alive and prevent the memory freed while wrappers still exist with
     * handles in it. */
    @SuppressWarnings("unused") private final HandleBlock handleBlock;
    /** Objects which do not strongly reference their wrapper back (boxed Long/Double for Bignum-range integer and Float
     * VALUEs, unlike RubyDynamicObject and ImmutableRubyObject) are also referenced strongly here, so keeping the
     * wrapper alive (see {@link #keepAliveObject()}) keeps such an object alive; null otherwise. */
    private final Object strongRef;

    /** The referent is {@code null} if this is a tagged long, otherwise the Ruby object. */
    public ValueWrapper(Object object, long handle, HandleBlock handleBlock) {
        super(object);
        assert (object == null) == ValueWrapperManager.isTaggedLong(handle);
        this.handle = handle;
        this.handleBlock = handleBlock;
        this.strongRef = object instanceof RubyDynamicObject || object instanceof ImmutableRubyObject ? null : object;
    }

    /** The wrapped Ruby object, or {@code null} if this is a tagged long or the object was collected (a handle passed
     * by C after its object died, which callers must treat as an error). */
    public Object getObject() {
        return get();
    }

    /** What a keep-alive list (see {@link org.truffleruby.core.MarkingService}) must reference to keep this handle
     * fully usable: the object for objects which strongly reference their wrapper back (the wrapper and its block stay
     * alive through the object), otherwise this wrapper (which strongly references a {@link #strongRef} object, and for
     * a tagged long there is nothing else to keep alive). */
    public Object keepAliveObject() {
        if (strongRef != null) {
            return this;
        }
        final Object object = get();
        return object != null ? object : this;
    }

    /** Like {@link #keepAliveObject()}, but takes the object from a strong reference the caller holds instead of
     * reading this wrapper's weak reference, which could return null if nothing else references the object strongly
     * anymore. Returns null when there is nothing to keep alive: a tagged long, or the caller only had the wrapper and
     * the object was already collected. */
    public Object keepAliveObject(Object object) {
        // The identity check only applies when the object is what keeps this handle alive: for a
        // primitive-backed wrapper the argument can be a different box of the same primitive value.
        assert strongRef != null || object == null || get() == null || get() == object;
        return strongRef != null ? this : object;
    }

    @ExportMessage
    protected boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    protected String getLanguageId() {
        return "ruby";
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (ValueWrapperManager.isTaggedLong(handle)) {
            return "ValueWrapper[" + ValueWrapperManager.untagTaggedLong(handle) + "]";
        } else {
            final Object object = get();
            return "ValueWrapper[" + (object != null ? object : "<collected>") + "]";
        }
    }

    @TruffleBoundary
    @ExportMessage
    protected String toDisplayString(boolean allowSideEffects) {
        final Object object = get();
        if (object != null) {
            final InteropLibrary interop = InteropLibrary.getUncached();
            try {
                return "VALUE: " + interop.asString(interop.toDisplayString(object, allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw TranslateInteropExceptionNode.executeUncached(e);
            }
        } else {
            return "VALUE: " + this;
        }
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected Object getMembers(boolean includeInternal) {
        return new VariableNamesObject(new String[]{ "value" });
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return "value".equals(member);
    }

    @ExportMessage
    static Object readMember(ValueWrapper wrapper, String member,
            @Cached InlinedBranchProfile errorProfile,
            @Bind Node node) throws UnknownIdentifierException {
        if ("value".equals(member)) {
            if (ValueWrapperManager.isTaggedLong(wrapper.handle)) {
                return ValueWrapperManager.untagTaggedLong(wrapper.handle);
            } else {
                final Object object = wrapper.get();
                return object != null ? object : wrapper.handle;
            }
        } else {
            errorProfile.enter(node);
            throw UnknownIdentifierException.create(member);
        }
    }
}
