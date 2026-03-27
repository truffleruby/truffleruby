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
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In Concurrent::Map, and so TruffleRuby::ConcurrentMap, keys are compared with #hash and #eql?, and values by
 * identity (#equal? in NonConcurrentMapBackend). To use custom code to compare the keys we need a wrapper for keys
 * implementing #hashCode and #equals. For comparing values by identity we use {@link ReferenceEqualNode} if the value
 * is a primitive, otherwise we rely on equals() being == on Ruby objects. */
public final class RubyConcurrentMap extends RubyDynamicObject implements ObjectGraphNode {

    public static final class Key {

        public final Object key;
        private final int hashCode;

        public Key(Object key, int hashCode) {
            this.key = key;
            this.hashCode = hashCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            assert other instanceof Key;
            final Key otherKey = (Key) other;

            if (key == otherKey.key) {
                // If they're exactly the same object then they must be equal
                return true;
            } else if (hashCode != otherKey.hashCode) {
                // If they have different hash codes then they cannot be equal
                return false;
            } else {
                // Last resort - we have to actually call eql?
                return SameOrEqlNode.executeUncached(key, otherKey.key);
            }
        }
    }

    private ConcurrentHashMap<Key, Object> map;

    public RubyConcurrentMap(RubyClass rubyClass, Shape shape) {
        super(rubyClass, shape);
    }

    @TruffleBoundary
    public void allocateMap(int initialCapacity, float loadFactor) {
        if (initialCapacity <= 0) {
            map = new ConcurrentHashMap<>();
        } else if (loadFactor <= 0.0f) {
            map = new ConcurrentHashMap<>(initialCapacity);
        } else {
            map = new ConcurrentHashMap<>(initialCapacity, loadFactor);
        }
    }

    public ConcurrentHashMap<Key, Object> getMap() {
        return map;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        for (var entry : map.entrySet()) {
            ObjectGraph.addProperty(reachable, entry.getKey().key);
            ObjectGraph.addProperty(reachable, entry.getValue());
        }
    }

}
