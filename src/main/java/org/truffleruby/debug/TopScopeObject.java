/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.RubyLanguage;

/** Object representing the top scope. */
@ExportLibrary(InteropLibrary.class)
public final class TopScopeObject implements TruffleObject {

    @CompilationFinal(dimensions = 1) private static final String[] NAMES = {
            "interactive local variables",
            "global variables",
            "main object" };
    static final int LIMIT = NAMES.length;

    private final Object[] objects;
    private final int scopeIndex;

    public TopScopeObject(Object[] objects) {
        this(objects, 0);
    }

    private TopScopeObject(Object[] objects, int index) {
        assert objects.length == NAMES.length;
        this.objects = objects;
        this.scopeIndex = index;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return RubyLanguage.class;
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    Object toDisplayString(boolean allowSideEffects) {
        return NAMES[scopeIndex];
    }

    @ExportMessage
    boolean hasScopeParent() {
        return scopeIndex < (NAMES.length - 1);
    }

    @ExportMessage
    Object getScopeParent() throws UnsupportedMessageException {
        if (scopeIndex < (NAMES.length - 1)) {
            return new TopScopeObject(objects, scopeIndex + 1);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(boolean includeInternal,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop)
            throws UnsupportedMessageException {
        int length = NAMES.length;
        Object[] keys = new Object[length - scopeIndex];
        for (int i = scopeIndex; i < length; i++) {
            keys[i - scopeIndex] = interop.getMembers(objects[i]);
        }
        return new MergedPropertyNames(keys);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            if (interop.isMemberReadable(objects[i], member)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    Object readMember(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop)
            throws UnknownIdentifierException, UnsupportedMessageException {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberReadable(scope, member)) {
                return interop.readMember(scope, member);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberModifiable(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberModifiable(scope, member)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    boolean isMemberInsertable(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberInsertable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                return false; // saw existing member which would shadow the new member
            }
        }
        return false;
    }

    @ExportMessage
    boolean hasMemberReadSideEffects(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberReadable(scope, member)) {
                return interop.hasMemberReadSideEffects(scope, member);
            }
        }
        return false;
    }

    @ExportMessage
    boolean hasMemberWriteSideEffects(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberWritable(scope, member)) {
                return interop.hasMemberWriteSideEffects(scope, member);
            }
        }
        return false;
    }

    @ExportMessage
    void writeMember(String member, Object value,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop)
            throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberModifiable(scope, member) || interop.isMemberInsertable(scope, member)) {
                interop.writeMember(scope, member, value);
                return;
            } else if (interop.isMemberExisting(scope, member)) {
                // saw existing member which would shadow the new member
                throw UnsupportedMessageException.create();
            }
        }

        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isMemberRemovable(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop) {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberRemovable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                return false;
            }
        }
        return false;
    }

    @ExportMessage
    void removeMember(String member,
            @CachedLibrary(limit = "LIMIT") @Shared InteropLibrary interop)
            throws UnsupportedMessageException, UnknownIdentifierException {
        int length = NAMES.length;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberRemovable(scope, member)) {
                interop.removeMember(scope, member);
                return;
            } else if (interop.isMemberExisting(scope, member)) {
                break;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MergedPropertyNames implements TruffleObject {

        private final Object[] keys;
        private final long[] size;

        private MergedPropertyNames(Object[] keys) throws UnsupportedMessageException {
            this.keys = keys;
            size = new long[keys.length];
            long s = 0L;
            InteropLibrary interop = InteropLibrary.getUncached();
            for (int i = 0; i < keys.length; i++) {
                s += interop.getArraySize(keys[i]);
                size[i] = s;
            }
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return size[size.length - 1];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                @CachedLibrary(limit = "5") @Shared InteropLibrary interop) {
            if (index >= 0) {
                for (int i = 0; i < keys.length; i++) {
                    if (index < size[i]) {
                        long start = (i == 0) ? 0 : size[i - 1];
                        return interop.isArrayElementReadable(keys[i], index - start);
                    }
                }
            }
            return false;
        }

        @ExportMessage
        Object readArrayElement(long index,
                @CachedLibrary(limit = "5") @Shared InteropLibrary interop)
                throws InvalidArrayIndexException, UnsupportedMessageException {
            if (index >= 0) {
                for (int i = 0; i < keys.length; i++) {
                    if (index < size[i]) {
                        long start = (i == 0) ? 0 : size[i - 1];
                        return interop.readArrayElement(keys[i], index - start);
                    }
                }
            }
            throw InvalidArrayIndexException.create(index);
        }

    }
}
