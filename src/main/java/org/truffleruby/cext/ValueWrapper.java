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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.cext.ValueWrapperManager.HandleBlock;
import org.truffleruby.cext.ValueWrapperManager.WrapperToHandleNode;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
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
 * always do, and are useful to inspect a ValueWrapper in the Truffle debugger. */
@ExportLibrary(InteropLibrary.class)
public final class ValueWrapper implements TruffleObject {

    /** {@code null} if this is a tagged long, otherwise the Ruby object. */
    private final Object object;
    /** Consider using {@link WrapperToHandleNode} or {@link KeepAliveNode} when passing the handle to C */
    public final long handle;
    /** The handleBlock is held here to keep it alive and prevent the memory freed while wrappers still exist with
     * handles in it. */
    @SuppressWarnings("unused") private final HandleBlock handleBlock;

    public ValueWrapper(Object object, long handle, HandleBlock handleBlock) {
        assert (object == null) == ValueWrapperManager.isTaggedLong(handle);
        this.object = object;
        this.handle = handle;
        this.handleBlock = handleBlock;
    }

    public Object getObject() {
        return object;
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
        if (object != null) {
            return "ValueWrapper[" + object + "]";
        } else {
            assert ValueWrapperManager.isTaggedLong(handle);
            return "ValueWrapper[" + ValueWrapperManager.untagTaggedLong(handle) + "]";
        }
    }

    @TruffleBoundary
    @ExportMessage
    protected String toDisplayString(boolean allowSideEffects) {
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
            if (wrapper.object != null) {
                return wrapper.object;
            } else {
                return ValueWrapperManager.untagTaggedLong(wrapper.handle);
            }
        } else {
            errorProfile.enter(node);
            throw UnknownIdentifierException.create(member);
        }
    }
}
