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
package org.truffleruby.core.objectspace;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.collections.SimpleEntry;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.hash.CompareByRubyIdentityWrapper;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.Nil;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.yield.CallBlockNode;

import java.util.Collection;

/** Note that WeakMap uses identity comparison semantics. See top comment in src/main/ruby/truffleruby/core/weakmap.rb
 * for more information. */
@CoreModule(value = "ObjectSpace::WeakMap", isClass = true)
public abstract class WeakMapNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyWeakMap allocate(RubyClass rubyClass) {
            final RubyWeakMap weakMap = new RubyWeakMap(rubyClass, getLanguage().weakMapShape);
            AllocationTracing.trace(weakMap, this);
            return weakMap;
        }
    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int size(RubyWeakMap map) {
            return map.storage.size();
        }
    }

    @CoreMethod(names = { "key?", "member?", "include?" }, required = 1)
    public abstract static class MemberNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isMember(RubyWeakMap map, Object key) {
            return map.storage.get(new CompareByRubyIdentityWrapper(key)) != null;
        }
    }

    @CoreMethod(names = "[]", required = 1)
    public abstract static class GetIndexNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object get(RubyWeakMap map, Object key) {
            Object value = map.storage.get(new CompareByRubyIdentityWrapper(key));
            return value == null ? nil : value;
        }
    }

    @CoreMethod(names = "[]=", required = 2)
    public abstract static class SetIndexNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object set(RubyWeakMap map, Object key, Object value) {
            map.storage.put(new CompareByRubyIdentityWrapper(key), value);
            return value;
        }
    }

    @CoreMethod(names = "keys")
    public abstract static class KeysNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray getKeys(RubyWeakMap map) {
            return createArray(keys(map.storage));
        }
    }

    @CoreMethod(names = "values")
    public abstract static class ValuesNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyArray getValues(RubyWeakMap map) {
            return createArray(values(map.storage));
        }
    }

    @CoreMethod(names = "delete", required = 1, needsBlock = true)
    public abstract static class DeleteNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object delete(RubyWeakMap map, Object key, RubyProc block,
                @Cached @Shared InlinedConditionProfile isContainedProfile,
                @Cached CallBlockNode yieldNode) {
            Object value = map.storage.remove(new CompareByRubyIdentityWrapper(key));

            if (isContainedProfile.profile(this, value != null)) {
                return value;
            } else {
                return yieldNode.yield(this, block, key);
            }
        }

        @Specialization
        Object delete(RubyWeakMap map, Object key, Nil block,
                @Cached @Shared InlinedConditionProfile isContainedProfile) {
            Object value = map.storage.remove(new CompareByRubyIdentityWrapper(key));

            if (isContainedProfile.profile(this, value != null)) {
                return value;
            } else {
                return nil;
            }
        }
    }

    @CoreMethod(names = "each_key", needsBlock = true)
    public abstract static class EachKeyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyWeakMap eachKey(RubyWeakMap map, Nil block) {
            return eachNoBlockProvided(this, map);
        }

        @Specialization
        RubyWeakMap eachKey(RubyWeakMap map, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            for (Object key : keys(map.storage)) {
                yieldNode.yield(this, block, key);
            }
            return map;
        }
    }

    @CoreMethod(names = "each_value", needsBlock = true)
    public abstract static class EachValueNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyWeakMap eachValue(RubyWeakMap map, Nil block) {
            return eachNoBlockProvided(this, map);
        }

        @Specialization
        RubyWeakMap eachValue(RubyWeakMap map, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            for (Object value : values(map.storage)) {
                yieldNode.yield(this, block, value);
            }
            return map;
        }
    }

    @CoreMethod(names = { "each", "each_pair" }, needsBlock = true)
    public abstract static class EachNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyWeakMap each(RubyWeakMap map, Nil block) {
            return eachNoBlockProvided(this, map);
        }

        @Specialization
        RubyWeakMap each(RubyWeakMap map, RubyProc block,
                @Cached CallBlockNode yieldNode) {

            for (SimpleEntry<?, ?> entry : entries(map.storage)) {
                yieldNode.yield(this, block, entry.getKey(), entry.getValue());
            }

            return map;
        }
    }

    @TruffleBoundary
    private static Object[] keys(WeakMapStorage storage) {
        final Collection<CompareByRubyIdentityWrapper> keyWrappers = storage.keys();
        final Object[] keys = new Object[keyWrappers.size()];
        int i = 0;
        for (CompareByRubyIdentityWrapper keyWrapper : keyWrappers) {
            keys[i++] = keyWrapper.key;
        }
        return keys;
    }

    @TruffleBoundary
    private static Object[] values(WeakMapStorage storage) {
        return storage.values().toArray();
    }

    @TruffleBoundary
    private static SimpleEntry<?, ?>[] entries(WeakMapStorage storage) {
        final Collection<SimpleEntry<CompareByRubyIdentityWrapper, Object>> wrappedEntries = storage.entries();
        final SimpleEntry<?, ?>[] entries = new SimpleEntry<?, ?>[wrappedEntries.size()];
        int i = 0;
        for (SimpleEntry<CompareByRubyIdentityWrapper, Object> wrappedEntry : wrappedEntries) {
            entries[i++] = new SimpleEntry<>(wrappedEntry.getKey().key, wrappedEntry.getValue());
        }
        return entries;
    }

    private static RubyWeakMap eachNoBlockProvided(RubyBaseNode node, RubyWeakMap map) {
        if (map.storage.size() == 0) {
            return map;
        }
        final RubyContext context = RubyContext.get(node);
        throw new RaiseException(context, context.getCoreExceptions().localJumpError("no block given", node));
    }
}
