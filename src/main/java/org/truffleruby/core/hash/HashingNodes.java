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
package org.truffleruby.core.hash;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.basicobject.BasicObjectNodes.ObjectIDNode;
import org.truffleruby.core.cast.ToRubyIntegerNode;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringHelperNodes;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolNodes;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

public abstract class HashingNodes {

    @GenerateUncached
    public abstract static class ToHash extends RubyBaseNode {

        public static ToHash getUncached() {
            return HashingNodesFactory.ToHashNodeGen.getUncached();
        }

        public abstract int execute(Object key, boolean compareByIdentity);

        @Specialization(guards = "!compareByIdentity")
        int hash(Object key, boolean compareByIdentity,
                @Cached ToHashByHashCode toHashByHashCode) {
            return toHashByHashCode.execute(this, key);
        }


        @Specialization(guards = "compareByIdentity")
        int hashCompareByIdentity(Object key, boolean compareByIdentity,
                @Cached ToHashByIdentity toHashByIdentity) {
            return toHashByIdentity.execute(key);
        }
    }

    // MRI: any_hash
    /** Keep consistent with {@link org.truffleruby.core.kernel.KernelNodes.HashNode} */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @ReportPolymorphism // calling #hash on the key and many specializations
    public abstract static class ToHashByHashCode extends RubyBaseNode {

        @NeverDefault
        public static ToHashByHashCode create() {
            return HashingNodesFactory.ToHashByHashCodeNodeGen.create();
        }

        public static int executeUncached(Object key) {
            return HashingNodesFactory.ToHashByHashCodeNodeGen.getUncached().execute(null, key);
        }

        public abstract int execute(Node node, Object key);

        public final int executeCached(Object key) {
            return execute(this, key);
        }

        @Specialization
        static int hashBoolean(Node node, boolean value) {
            return (int) HashOperations.hashBoolean(value, getContext(node), node);
        }

        @Specialization
        static int hashInt(Node node, int value) {
            return (int) HashOperations.hashLong(value, getContext(node), node);
        }

        @Specialization
        static int hashLong(Node node, long value) {
            return (int) HashOperations.hashLong(value, getContext(node), node);
        }

        @Specialization
        static int hashDouble(Node node, double value) {
            return (int) HashOperations.hashDouble(value, getContext(node), node);
        }

        @Specialization
        static int hashBignum(Node node, RubyBignum value) {
            return (int) HashOperations.hashBignum(value, getContext(node), node);
        }

        @Specialization
        static int hashString(Node node, RubyString value,
                @Cached @Shared StringHelperNodes.HashStringNode stringHashNode) {
            return (int) stringHashNode.execute(node, value);
        }

        @Specialization
        static int hashImmutableString(Node node, ImmutableRubyString value,
                @Cached @Shared StringHelperNodes.HashStringNode stringHashNode) {
            return (int) stringHashNode.execute(node, value);
        }

        @Specialization
        static int hashSymbol(Node node, RubySymbol value,
                @Cached SymbolNodes.HashSymbolNode symbolHashNode) {
            return (int) symbolHashNode.execute(node, value);
        }

        @Fallback
        static int hashOther(Node node, Object value,
                @Cached(inline = false) DispatchNode callHash,
                @Cached HashCastResultNode cast) {
            return cast.execute(node, callHash.call(value, "hash"));
        }
    }

    @GenerateUncached
    public abstract static class ToHashByIdentity extends RubyBaseNode {

        public static ToHashByIdentity getUncached() {
            return HashingNodesFactory.ToHashByIdentityNodeGen.getUncached();
        }

        public abstract int execute(Object key);

        @Specialization
        int toHashByIdentity(Object hashed,
                @Cached ObjectIDNode objectIDNode,
                @Cached HashCastResultNode hashCastResultNode) {
            return hashCastResultNode.execute(this, objectIDNode.execute(hashed));
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class HashCastResultNode extends RubyBaseNode {

        public abstract int execute(Node node, Object key);

        public final int executeCached(Object key) {
            return execute(null, key);
        }

        @Specialization
        static int castInt(int hashed) {
            return hashed;
        }

        @Specialization
        static int castLong(long hashed) {
            return (int) hashed;
        }

        @Specialization
        static int castBignum(RubyBignum hashed) {
            return BigIntegerOps.hashCode(hashed);
        }

        @Specialization(guards = "!isRubyInteger(hashed)")
        static int castOther(Node node, Object hashed,
                @Cached ToRubyIntegerNode toRubyInteger,
                // recursive inlining is not supported
                @Cached(inline = false) HashCastResultNode hashCastResult) {
            return hashCastResult.executeCached(toRubyInteger.execute(node, hashed));
        }
    }
}
