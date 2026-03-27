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
import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager.AllocateHandleNode;
import org.truffleruby.cext.ValueWrapperManager.HandleBlock;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
import org.truffleruby.debug.VariableNamesObject;
import org.truffleruby.interop.TranslateInteropExceptionNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/** This object represents a VALUE in C which wraps the raw Ruby object. This allows foreign access methods to be set up
 * which convert these value wrappers to native pointers without affecting the semantics of the wrapped objects. */
@ExportLibrary(InteropLibrary.class)
public final class ValueWrapper implements TruffleObject {

    private final Object object;
    private volatile long handle;
    @SuppressWarnings("unused")
    // The handleBlock is held here to stop it being GCed and the memory freed while wrappers still
    // exist with handles in it.
    private volatile HandleBlock handleBlock;

    public ValueWrapper(Object object, long handle, HandleBlock handleBlock) {
        this.object = object;
        this.handle = handle;
        this.handleBlock = handleBlock;
    }

    public Object getObject() {
        return object;
    }

    public long getHandle() {
        return handle;
    }

    public void setHandle(long handle, HandleBlock handleBlock) {
        this.handle = handle;
        this.handleBlock = handleBlock;
    }

    @ExportMessage
    protected boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    protected Class<RubyLanguage> getLanguage() {
        return RubyLanguage.class;
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
    protected boolean isPointer() {
        return handle != ValueWrapperManager.UNSET_HANDLE;
    }

    @ExportMessage
    static void toNative(ValueWrapper wrapper,
            @Cached AllocateHandleNode createNativeHandleNode,
            @Cached @Exclusive InlinedBranchProfile createHandleProfile,
            @Bind Node node) {
        if (!wrapper.isPointer()) {
            createHandleProfile.enter(node);
            createNativeHandleNode.execute(node, wrapper);
        }
    }

    @ExportMessage
    static long asPointer(ValueWrapper wrapper,
            @Cached KeepAliveNode keepAliveNode,
            @Cached @Exclusive InlinedBranchProfile taggedObjectProfile,
            @Bind Node node) {
        long handle = wrapper.getHandle();
        assert handle != ValueWrapperManager.UNSET_HANDLE;

        if (ValueWrapperManager.isTaggedObject(handle)) {
            taggedObjectProfile.enter(node);

            keepAliveNode.execute(node, wrapper);
        }

        return handle;
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
            @Cached @Exclusive InlinedBranchProfile errorProfile,
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
