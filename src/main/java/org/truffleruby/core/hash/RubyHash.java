/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;

import org.truffleruby.RubyContext;
import org.truffleruby.collections.PEBiFunction;
import org.truffleruby.core.hash.library.BucketsHashStore;
import org.truffleruby.core.hash.library.CompactHashStore;
import org.truffleruby.core.hash.library.ConcurrentHashStore;
import org.truffleruby.core.hash.library.EmptyHashStore;
import org.truffleruby.core.hash.library.HashStoreLibrary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.interop.ForeignToRubyNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.utils.Utils;

import static org.truffleruby.language.RubyBaseNode.nil;

@ExportLibrary(InteropLibrary.class)
@ImportStatic(HashGuards.class)
public final class RubyHash extends RubyDynamicObject implements ObjectGraphNode {

    private static final VarHandle SIZE_HANDLE = Utils.getVarHandle(MethodHandles.lookup(), RubyHash.class, "size",
            int.class);

    /** Do not write directly, use {@link #setStore(Object)} */
    public Object store;
    public int size;
    public Object defaultBlock;
    public Object defaultValue;
    public boolean compareByIdentity;
    public final boolean ruby2_keywords;

    /** Creates an empty Hash */
    public RubyHash(RubyClass rubyClass, Shape shape, RubyContext context) {
        this(rubyClass, shape, context, EmptyHashStore.NULL_HASH_STORE, 0, false);
    }

    public RubyHash(
            RubyClass rubyClass,
            Shape shape,
            RubyContext context,
            Object store,
            int size,
            boolean ruby2_keywords) {
        super(rubyClass, shape);
        setStore(store);
        this.size = size;
        this.defaultBlock = nil;
        this.defaultValue = nil;
        this.compareByIdentity = false;
        this.ruby2_keywords = ruby2_keywords;

        if (context.isPreInitializing()) {
            context.getPreInitializationManager().addPreInitHash(this);
        }
    }

    public void setStore(Object store) {
        assert SharedObjects.isShared(this) == store instanceof ConcurrentHashStore : this + " = " + store;
        assert !(this.store instanceof ConcurrentHashStore) : "once a RubyHash has a ConcurrentHashStore, its store should not change anymore.";
        this.store = store;
    }

    public void copyFieldsExceptStoreAndSize(RubyHash from) {
        this.defaultBlock = from.defaultBlock;
        this.defaultValue = from.defaultValue;
        this.compareByIdentity = from.compareByIdentity;
    }

    public int getSizeVolatile() {
        return (int) SIZE_HANDLE.getVolatile(this);
    }

    public int incrementAndGetSize() {
        return (int) SIZE_HANDLE.getAndAdd(this, 1) + 1;
    }

    public int decrementSize() {
        return (int) SIZE_HANDLE.getAndAdd(this, -1) - 1;
    }

    // Not named isEmpty() has that's deprecated on DynamicObject
    public boolean empty() {
        return size == 0;
    }

    @Override
    public String toString() {
        return super.toString() + "(size=" + size + ")";
    }

    @TruffleBoundary
    public void getAdjacentObjects(Set<Object> reachable) {
        if (store instanceof BucketsHashStore bucketsHashStore) {
            bucketsHashStore.getAdjacentObjects(reachable);
        } else if (store instanceof CompactHashStore compactHashStore) {
            compactHashStore.getAdjacentObjects(reachable);
        } else if (store instanceof ConcurrentHashStore concurrentHashStore) {
            concurrentHashStore.getAdjacentObjects(reachable);
        } else {
            assert store instanceof Object[] || store instanceof EmptyHashStore : store;
            ObjectGraph.addProperty(reachable, store);
        }

        ObjectGraph.addProperty(reachable, defaultBlock);
        ObjectGraph.addProperty(reachable, defaultValue);
    }

    // region InteropLibrary messages

    // NOTE(norswap, 07 Apr 2021): For now, ignore Ruby default values.

    @ExportMessage
    public boolean hasHashEntries() {
        return true;
    }

    @ExportMessage
    public long getHashSize() {
        return size;
    }

    private static final class DefaultProvider implements PEBiFunction {
        private final Object defaultValue;

        private DefaultProvider(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public Object accept(Frame frame, Object hash, Object key) {
            return defaultValue;
        }
    }

    private static final DefaultProvider NULL_PROVIDER = new DefaultProvider(null);

    @ExportMessage(name = "isHashEntryExisting", limit = "hashStrategyLimit()")
    @ExportMessage(name = "isHashEntryReadable", limit = "hashStrategyLimit()")
    public final boolean isHashEntryExisting(Object key,
            @CachedLibrary("this.store") HashStoreLibrary hashStores,
            @Cached @Shared ForeignToRubyNode toRuby,
            @Bind Node node) {
        return hashStores.lookupOrDefault(store, null, this, toRuby.execute(node, key), NULL_PROVIDER) != null;
    }

    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    public boolean isHashEntryModifiableAndRemovable(Object key,
            @CachedLibrary("this") InteropLibrary interop,
            @Cached @Shared IsFrozenNode isFrozenNode) {
        return !isFrozenNode.execute(this) && interop.isHashEntryExisting(this, key);
    }

    @ExportMessage
    public boolean isHashEntryInsertable(Object key,
            @CachedLibrary("this") InteropLibrary interop,
            @Cached @Shared IsFrozenNode isFrozenNode) {
        return !isFrozenNode.execute(this) && !interop.isHashEntryExisting(this, key);
    }

    @ExportMessage(limit = "hashStrategyLimit()")
    public Object readHashValue(Object key,
            @CachedLibrary("this.store") HashStoreLibrary hashStores,
            // @Exclusive to fix truffle-interpreted-performance warning
            @Cached @Exclusive ForeignToRubyNode toRuby,
            @Cached InlinedConditionProfile unknownKey,
            @Bind Node node)
            throws UnknownKeyException {
        final Object value = hashStores.lookupOrDefault(store, null, this, toRuby.execute(node, key), NULL_PROVIDER);
        if (unknownKey.profile(node, value == null)) {
            throw UnknownKeyException.create(key);
        }
        return value;
    }

    @ExportMessage(limit = "hashStrategyLimit()")
    public Object readHashValueOrDefault(Object key, Object defaultValue,
            @CachedLibrary("this.store") HashStoreLibrary hashStores,
            @Cached @Shared ForeignToRubyNode toRuby,
            @Bind Node node) {
        return hashStores
                .lookupOrDefault(store, null, this, toRuby.execute(node, key), new DefaultProvider(defaultValue));
    }

    @ExportMessage
    public void writeHashEntry(Object key, Object value,
            @Cached @Exclusive DispatchNode set,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @Cached @Shared ForeignToRubyNode toRuby,
            @Bind Node node)
            throws UnsupportedMessageException {
        if (isFrozenNode.execute(this)) {
            throw UnsupportedMessageException.create();
        }
        set.call(this, "[]=", toRuby.execute(node, key), value);
    }

    @ExportMessage
    public void removeHashEntry(Object key,
            @Cached @Exclusive DispatchNode delete,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @CachedLibrary("this") InteropLibrary interop,
            @Cached @Shared ForeignToRubyNode toRuby,
            @Bind Node node)
            throws UnsupportedMessageException, UnknownKeyException {
        if (isFrozenNode.execute(this)) {
            throw UnsupportedMessageException.create();
        }
        if (!interop.isHashEntryExisting(this, key)) {
            throw UnknownKeyException.create(key);
        }
        delete.call(this, "delete", toRuby.execute(node, key));
    }

    @ExportMessage
    public Object getHashEntriesIterator(
            @Cached @Exclusive DispatchNode eachPair) {
        return eachPair.call(this, "each_pair");
    }

    @ExportMessage
    public Object getHashKeysIterator(
            @Cached @Exclusive DispatchNode eachKey) {
        return eachKey.call(this, "each_key");
    }

    @ExportMessage
    public Object getHashValuesIterator(
            @Cached @Exclusive DispatchNode eachValue) {
        return eachValue.call(this, "each_value");
    }

    // endregion
}
