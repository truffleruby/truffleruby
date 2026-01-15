/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.NeverDefault;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.utils.Utils;

@CoreModule(value = "Truffle::ArrayIndex", isClass = false)
public abstract class ArrayIndexNodes {

    @NodeChild(value = "arrayNode", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class ReadConstantIndexNode extends RubyContextSourceNode {

        private final int index;

        public static ReadConstantIndexNode create(RubyNode array, int index) {
            return ArrayIndexNodesFactory.ReadConstantIndexNodeGen.create(index, array);
        }

        protected ReadConstantIndexNode(int index) {
            this.index = index;
        }

        public abstract RubyNode getArrayNode();

        @Specialization(limit = "storageStrategyLimit()")
        Object readInBounds(RubyArray array,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached ConditionProfile isInBounds) {
            final int size = array.size;
            final int normalizedIndex = index >= 0 ? index : size + index;
            if (isInBounds.profile(0 <= normalizedIndex && normalizedIndex < size)) {
                return stores.read(store, normalizedIndex);
            } else {
                return nil;
            }
        }

        @Override
        public RubyNode cloneUninitialized() {
            var copy = ReadConstantIndexNode.create(
                    getArrayNode().cloneUninitialized(),
                    index);
            return copy.copyFlags(this);
        }

    }

    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism // for ArrayStoreLibrary
    public abstract static class ReadNormalizedNode extends PrimitiveArrayArgumentsNode {

        @NeverDefault
        public static ReadNormalizedNode create() {
            return ArrayIndexNodesFactory.ReadNormalizedNodeFactory.create(null);
        }

        public static ReadNormalizedNode create(RubyNode array, RubyNode index) {
            return ArrayIndexNodesFactory.ReadNormalizedNodeFactory.create(new RubyNode[]{ array, index });
        }

        public abstract Object executeRead(RubyArray array, int index);

        @Specialization(
                guards = "isInBounds(array, index)",
                limit = "storageStrategyLimit()")
        Object readInBounds(RubyArray array, int index,
                @Bind("array.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores) {
            return stores.read(store, index);
        }

        @Specialization(guards = "!isInBounds(array, index)")
        Object readOutOfBounds(RubyArray array, int index) {
            return nil;
        }

        protected static boolean isInBounds(RubyArray array, int index) {
            return index >= 0 && index < array.size;
        }
    }

    @ImportStatic(ArrayGuards.class)
    public abstract static class ReadSliceNormalizedNode extends RubyBaseNode {

        public abstract Object executeReadSlice(RubyArray array, int index, int length);

        @Specialization(guards = "!indexInBounds(array, index)")
        Object readIndexOutOfBounds(RubyArray array, int index, int length) {
            return nil;
        }

        @Specialization(guards = "length < 0")
        Object readNegativeLength(RubyArray array, int index, int length) {
            return nil;
        }

        @Specialization(guards = { "indexInBounds(array, index)", "length >= 0" })
        RubyArray readInBounds(RubyArray array, int index, int length,
                @Cached ArrayCopyOnWriteNode cowNode,
                @Cached ConditionProfile endsInBoundsProfile) {
            final int size = array.size;
            final int end = endsInBoundsProfile.profile(Utils.longAdd(index, length) <= size)
                    ? length
                    : size - index;
            final Object slice = cowNode.execute(array, index, end);
            return createArray(slice, end);
        }

        protected static boolean indexInBounds(RubyArray array, int index) {
            return index >= 0 && index <= array.size;
        }
    }
}
