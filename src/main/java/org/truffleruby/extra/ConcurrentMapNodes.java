/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.extra;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.collections.SimpleEntry;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.hash.HashingNodes.ToHashByHashCode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.extra.AtomicReferenceNodes.CompareAndSetReferenceNode;
import org.truffleruby.extra.RubyConcurrentMap.Key;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.shared.PropagateSharingNode;
import org.truffleruby.language.yield.CallBlockNode;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@CoreModule(value = "TruffleRuby::ConcurrentMap", isClass = true)
public abstract class ConcurrentMapNodes {

    @TruffleBoundary
    private static Object get(ConcurrentHashMap<Key, Object> map, Key key) {
        return map.get(key);
    }

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyConcurrentMap allocate(RubyClass rubyClass) {
            final Shape shape = getLanguage().concurrentMapShape;
            final RubyConcurrentMap instance = new RubyConcurrentMap(rubyClass, shape);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @Primitive(name = "concurrent_map_initialize", lowerFixnum = { 1, 2 })
    public abstract static class InitializeNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyConcurrentMap initializeWithOptions(RubyConcurrentMap self, int initialCapacity, int loadFactor) {
            return initializeWithOptions(self, initialCapacity, (double) loadFactor);
        }

        @Specialization
        RubyConcurrentMap initializeWithOptions(RubyConcurrentMap self, int initialCapacity, double loadFactor) {
            self.allocateMap(initialCapacity, (float) loadFactor);
            return self;
        }
    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        @TruffleBoundary
        RubyConcurrentMap initializeCopy(RubyConcurrentMap self, RubyConcurrentMap other,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            if (self.getMap() == null) {
                self.allocateMap(other.getMap().size(), 0.0f);
            }

            for (var entry : other.getMap().entrySet()) {
                Key keyObject = entry.getKey();
                Object value = entry.getValue();
                propagateSharingKey.execute(this, self, keyObject.key);
                propagateSharingValue.execute(this, self, value);
                self.getMap().put(keyObject, value);
            }
            return self;
        }
    }

    @CoreMethod(names = "[]", required = 1)
    public abstract static class GetIndexNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object getIndex(RubyConcurrentMap self, Object key,
                @Cached ToHashByHashCode hashNode) {
            final int hashCode = hashNode.execute(this, key);
            return nullToNil(get(self.getMap(), new Key(key, hashCode)));
        }
    }

    @CoreMethod(names = "[]=", required = 2)
    public abstract static class SetIndexNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object setIndex(RubyConcurrentMap self, Object key, Object value,
                @Cached ToHashByHashCode hashNode,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            final int hashCode = hashNode.execute(this, key);
            propagateSharingKey.execute(this, self, key);
            propagateSharingValue.execute(this, self, value);
            put(self.getMap(), new Key(key, hashCode), value);
            return value;
        }

        @TruffleBoundary
        private static void put(ConcurrentHashMap<Key, Object> map, Key key, Object value) {
            map.put(key, value);
        }
    }

    @CoreMethod(names = "compute_if_absent", required = 1, needsBlock = true)
    public abstract static class ComputeIfAbsentNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object computeIfAbsent(RubyConcurrentMap self, Object key, RubyProc block,
                @Cached ToHashByHashCode hashNode,
                @Cached CallBlockNode yieldNode,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            final int hashCode = hashNode.execute(this, key);
            propagateSharingKey.execute(this, self, key);
            final Object returnValue = ConcurrentOperations.getOrCompute(self.getMap(), new Key(key, hashCode),
                    (k) -> propagateSharingValue.propagate(this, self, yieldNode.yield(this, block)));
            assert returnValue != null;
            return returnValue;
        }

    }

    @CoreMethod(names = "compute_if_present", required = 1, needsBlock = true)
    public abstract static class ComputeIfPresentNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object computeIfPresent(RubyConcurrentMap self, Object key, RubyProc block,
                @Cached ToHashByHashCode hashNode,
                @Cached CallBlockNode yieldNode,
                @Cached PropagateSharingNode propagateSharingValue) {
            // The key does not need to be shared for computeIfPresent() because that never inserts the key in the map
            final int hashCode = hashNode.execute(this, key);
            return nullToNil(computeIfPresent(self.getMap(), new Key(key, hashCode), (k, v) -> {
                // TODO (Chris, 6 May 2021): It's unfortunate we're calling this behind a boundary! Can we do better?
                Object value = yieldNode.yield(this, block, v);
                return nilToNull(propagateSharingValue.propagate(this, self, value));
            }));
        }

        @TruffleBoundary
        private static Object computeIfPresent(ConcurrentHashMap<Key, Object> map, Key key,
                BiFunction<Key, Object, Object> remappingFunction) {
            return map.computeIfPresent(key, remappingFunction);
        }
    }

    @CoreMethod(names = "compute", required = 1, needsBlock = true)
    public abstract static class ComputeNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object compute(RubyConcurrentMap self, Object key, RubyProc block,
                @Cached ToHashByHashCode hashNode,
                @Cached CallBlockNode yieldNode,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            final int hashCode = hashNode.execute(this, key);
            propagateSharingKey.execute(this, self, key);
            return nullToNil(compute(self.getMap(), new Key(key, hashCode), (k, v) -> {
                Object value = yieldNode.yield(this, block, nullToNil(v));
                return nilToNull(propagateSharingValue.propagate(this, self, value));
            }));
        }

        @TruffleBoundary
        private static Object compute(ConcurrentHashMap<Key, Object> map, Key key,
                BiFunction<Key, Object, Object> remappingFunction) {
            return map.compute(key, remappingFunction);
        }
    }

    @CoreMethod(names = "merge_pair", required = 2, needsBlock = true)
    public abstract static class MergePairNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object mergePair(RubyConcurrentMap self, Object key, Object value, RubyProc block,
                @Cached ToHashByHashCode hashNode,
                @Cached CallBlockNode yieldNode,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            final int hashCode = hashNode.execute(this, key);
            propagateSharingKey.execute(this, self, key);
            propagateSharingKey.execute(this, self, value);
            return nullToNil(merge(self.getMap(), new Key(key, hashCode), value, (existingValue, newValue) -> nilToNull(
                    propagateSharingValue.propagate(this, self, yieldNode.yield(this, block, existingValue)))));
        }

        @TruffleBoundary
        private static Object merge(ConcurrentHashMap<Key, Object> map, Key key, Object value,
                BiFunction<Object, Object, Object> remappingFunction) {
            return map.merge(key, value, remappingFunction);
        }
    }

    @CoreMethod(names = "replace_pair", required = 3)
    public abstract static class ReplacePairNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean replacePair(RubyConcurrentMap self, Object key, Object expectedValue, Object newValue,
                @Cached ToHashByHashCode hashNode,
                @Cached PropagateSharingNode propagateSharingValue,
                @Cached ReferenceEqualNode equalNode,
                @Cached InlinedConditionProfile isPrimitiveProfile) {
            // The key does not need to be shared for replace() because that never inserts the key in the map
            final int hashCode = hashNode.execute(this, key);

            if (isPrimitiveProfile.profile(this, RubyGuards.isPrimitive(expectedValue))) {
                return replacePairPrimitive(self, key, expectedValue, newValue, hashCode, equalNode);
            } else {
                propagateSharingValue.execute(this, self, newValue);
                return replace(self.getMap(), new Key(key, hashCode), expectedValue, newValue);
            }
        }

        private boolean replacePairPrimitive(RubyConcurrentMap self, Object key, Object expectedValue, Object newValue,
                int hashCode, ReferenceEqualNode equalNode) {
            final Key keyWrapper = new Key(key, hashCode);

            while (true) {
                final Object currentValue = get(self.getMap(), keyWrapper);

                if (RubyGuards.isPrimitive(currentValue) &&
                        equalNode.execute(this, expectedValue, currentValue)) {
                    if (replace(self.getMap(), keyWrapper, currentValue, newValue)) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }

        @TruffleBoundary
        private static boolean replace(ConcurrentHashMap<Key, Object> map, Key key, Object oldValue, Object newValue) {
            return map.replace(key, oldValue, newValue);
        }
    }

    @CoreMethod(names = "delete_pair", required = 2)
    public abstract static class DeletePairNode extends CoreMethodArrayArgumentsNode {
        /** See {@link CompareAndSetReferenceNode} */
        @Specialization
        boolean deletePair(RubyConcurrentMap self, Object key, Object expectedValue,
                @Cached ToHashByHashCode hashNode,
                @Cached ReferenceEqualNode equalNode,
                @Cached InlinedConditionProfile isPrimitiveProfile) {

            final int hashCode = hashNode.execute(this, key);

            if (isPrimitiveProfile.profile(this, RubyGuards.isPrimitive(expectedValue))) {
                return deletePairPrimitive(self, key, expectedValue, hashCode, equalNode);
            } else {
                return remove(self.getMap(), new Key(key, hashCode), expectedValue);
            }
        }

        private boolean deletePairPrimitive(RubyConcurrentMap self, Object key, Object expectedValue, int hashCode,
                ReferenceEqualNode equalNode) {
            final Key keyWrapper = new Key(key, hashCode);

            while (true) {
                final Object currentValue = get(self.getMap(), keyWrapper);

                if (RubyGuards.isPrimitive(currentValue) &&
                        equalNode.execute(this, expectedValue, currentValue)) {
                    if (remove(self.getMap(), keyWrapper, currentValue)) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }

        @TruffleBoundary
        private static boolean remove(ConcurrentHashMap<Key, Object> map, Key key, Object expectedValue) {
            return map.remove(key, expectedValue);
        }
    }

    @CoreMethod(names = "replace_if_exists", required = 2)
    public abstract static class ReplaceIfExistsNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object replaceIfExists(RubyConcurrentMap self, Object key, Object newValue,
                @Cached ToHashByHashCode hashNode,
                @Cached PropagateSharingNode propagateSharingValue) {
            // The key does not need to be shared for replace() because that never inserts the key in the map
            final int hashCode = hashNode.execute(this, key);
            propagateSharingValue.execute(this, self, newValue);
            return nullToNil(replace(self.getMap(), new Key(key, hashCode), newValue));
        }

        @TruffleBoundary
        private static Object replace(ConcurrentHashMap<Key, Object> map, Key key, Object newValue) {
            return map.replace(key, newValue);
        }
    }

    @CoreMethod(names = "get_and_set", required = 2)
    public abstract static class GetAndSetNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object getAndSet(RubyConcurrentMap self, Object key, Object value,
                @Cached ToHashByHashCode hashNode,
                @Cached PropagateSharingNode propagateSharingKey,
                @Cached PropagateSharingNode propagateSharingValue) {
            final int hashCode = hashNode.execute(this, key);
            propagateSharingKey.execute(this, self, key);
            propagateSharingValue.execute(this, self, value);
            return nullToNil(put(self.getMap(), new Key(key, hashCode), value));
        }

        @TruffleBoundary
        private static Object put(ConcurrentHashMap<Key, Object> map, Key key, Object value) {
            return map.put(key, value);
        }
    }

    @CoreMethod(names = "key?", required = 1)
    public abstract static class KeyNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        boolean key(RubyConcurrentMap self, Object key,
                @Cached ToHashByHashCode hashNode) {
            final int hashCode = hashNode.execute(this, key);
            return containsKey(self.getMap(), new Key(key, hashCode));
        }

        @TruffleBoundary
        private static boolean containsKey(ConcurrentHashMap<Key, Object> map, Key key) {
            return map.containsKey(key);
        }
    }

    @CoreMethod(names = "delete", required = 1)
    public abstract static class DeleteNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object delete(RubyConcurrentMap self, Object key,
                @Cached ToHashByHashCode hashNode) {
            final int hashCode = hashNode.execute(this, key);
            return nullToNil(remove(self.getMap(), new Key(key, hashCode)));
        }

        @TruffleBoundary
        private static Object remove(ConcurrentHashMap<Key, Object> map, Key key) {
            return map.remove(key);
        }
    }

    @CoreMethod(names = "clear")
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyConcurrentMap clear(RubyConcurrentMap self) {
            self.getMap().clear();
            return self;
        }
    }

    @CoreMethod(names = "size")
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int size(RubyConcurrentMap self) {
            return self.getMap().size();
        }
    }

    @CoreMethod(names = "get_or_default", required = 2)
    public abstract static class GetOrDefaultNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        Object getOrDefault(RubyConcurrentMap self, Object key, Object defaultValue,
                @Cached ToHashByHashCode hashNode) {
            final int hashCode = hashNode.execute(this, key);
            return getOrDefault(self.getMap(), new Key(key, hashCode), defaultValue);
        }

        @TruffleBoundary
        private static Object getOrDefault(ConcurrentHashMap<Key, Object> map, Key key, Object defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }
    }

    @CoreMethod(names = "each_pair", needsBlock = true)
    public abstract static class EachPairNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object eachPair(RubyConcurrentMap self, RubyProc block,
                @Cached CallBlockNode yieldNode) {
            final Iterator<Entry<Key, Object>> iterator = iterator(self.getMap());

            while (true) {
                final SimpleEntry<Key, Object> pair = next(iterator);

                if (pair == null) {
                    break;
                }

                yieldNode.yield(this, block, pair.getKey().key, pair.getValue());
            }

            return self;
        }

        @TruffleBoundary
        private static Iterator<Entry<Key, Object>> iterator(ConcurrentHashMap<Key, Object> map) {
            return map.entrySet().iterator();
        }

        @TruffleBoundary
        private static SimpleEntry<Key, Object> next(Iterator<Entry<Key, Object>> iterator) {
            if (iterator.hasNext()) {
                final Entry<Key, Object> entry = iterator.next();
                return new SimpleEntry<>(entry.getKey(), entry.getValue());
            } else {
                return null;
            }
        }

    }
}
