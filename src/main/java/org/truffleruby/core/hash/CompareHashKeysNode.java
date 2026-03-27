/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class CompareHashKeysNode extends RubyBaseNode {

    public abstract boolean execute(Node node, boolean compareByIdentity, Object key, int hashed,
            Object otherKey, int otherHashed);

    /** Checks if the two keys are the same object, which is used by both modes (by identity or not) of lookup. Enables
     * to check if the two keys are the same without a method call. */
    public static boolean referenceEqualKeys(Node node, ReferenceEqualNode refEqual, boolean compareByIdentity,
            Object key, int hashed, Object otherKey, int otherHashed) {
        return compareByIdentity
                ? refEqual.execute(node, key, otherKey)
                : hashed == otherHashed && refEqual.execute(node, key, otherKey);
    }

    @Specialization(guards = "compareByIdentity")
    static boolean refEquals(
            Node node, boolean compareByIdentity, Object key, int hashed, Object otherKey, int otherHashed,
            @Cached ReferenceEqualNode refEqual) {
        return refEqual.execute(node, key, otherKey);
    }

    @Specialization(guards = "!compareByIdentity")
    static boolean same(Node node, boolean compareByIdentity, Object key, int hashed, Object otherKey, int otherHashed,
            @Cached SameOrEqlNode same) {
        return hashed == otherHashed && same.execute(node, key, otherKey);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class AssumingEqualHashes extends RubyBaseNode {

        public abstract boolean execute(Node node, boolean compareByIdentity, Object key, Object otherKey);

        @Specialization(guards = "compareByIdentity")
        static boolean refEquals(Node node, boolean compareByIdentity, Object key, Object otherKey,
                @Cached ReferenceEqualNode refEqual) {
            return refEqual.execute(node, key, otherKey);
        }

        @Specialization(guards = "!compareByIdentity")
        static boolean same(Node node, boolean compareByIdentity, Object key, Object otherKey,
                @Cached SameOrEqlNode same) {
            return same.execute(node, key, otherKey);
        }
    }
}
