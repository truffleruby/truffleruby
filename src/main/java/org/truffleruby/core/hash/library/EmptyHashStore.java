/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash.library;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.hash.HashOperations;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryCallback;
import org.truffleruby.core.hash.library.HashStoreLibrary.EachEntryWithHashedCallback;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.objects.shared.SharedObjects;

@ExportLibrary(value = HashStoreLibrary.class)
@GenerateUncached
public final class EmptyHashStore {

    public static final EmptyHashStore NULL_HASH_STORE = new EmptyHashStore();

    private EmptyHashStore() {
    }

    @ExportMessage
    protected Object lookupOrDefault(Frame frame, RubyHash hash, Object key, PEBiFunction defaultNode) {
        return defaultNode.accept(frame, hash, key);
    }

    @ExportMessage
    protected boolean set(RubyHash hash, Object key, Object value, boolean byIdentity,
            @CachedLibrary(limit = "1") HashStoreLibrary packedHashStoreLibrary) {
        final Object[] packedStore = PackedHashStoreLibrary.createStore();
        hash.setStore(packedStore);
        return packedHashStoreLibrary.set(packedStore, hash, key, value, byIdentity);
    }

    @ExportMessage
    protected void clear(RubyHash hash) {
        // nothing to do, the hash is already empty
    }

    @ExportMessage
    protected Object delete(RubyHash hash, Object key) {
        return null;
    }

    @ExportMessage
    protected Object deleteLast(RubyHash hash, Object key) {
        throw CompilerDirectives.shouldNotReachHere("Cannot delete the last entry of an empty hash");
    }

    @ExportMessage
    protected Object eachEntry(RubyHash hash, EachEntryCallback callback, Object state) {
        return state;
    }

    @ExportMessage
    protected Object eachEntryHashed(RubyHash hash, EachEntryWithHashedCallback callback, Object state) {
        return state;
    }

    @ExportMessage
    protected Object eachEntrySafe(RubyHash hash, EachEntryCallback callback, Object state) {
        return state;
    }

    @ExportMessage
    protected Object copyStore() {
        return EmptyHashStore.NULL_HASH_STORE;
    }

    @ExportMessage
    protected RubyArray shift(RubyHash hash) {
        return null;
    }

    @ExportMessage
    protected void rehash(RubyHash hash) {
        // nothing to do, the hash is empty
    }

    @TruffleBoundary
    @ExportMessage
    public boolean verify(RubyHash hash) {
        assert hash.store == this;
        assert !SharedObjects.isShared(hash);

        assert hash.store == NULL_HASH_STORE;
        assert hash.size == 0;
        return true;
    }

    public static final class EmptyHashLiteralNode extends HashLiteralNode {

        public EmptyHashLiteralNode() {
            super(RubyNode.EMPTY_ARRAY);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return HashOperations.newEmptyHash(getContext(), getLanguage());
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = new EmptyHashLiteralNode();
            return copy.copyFlags(this);
        }

    }
}
