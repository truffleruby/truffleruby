/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.array.ArrayIndexNodes.ReadSliceNormalizedNode;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.locals.WriteLocalNode;

@ImportStatic(ArrayGuards.class)
@NodeChild(value = "valueNode", type = RubyNode.class)
public abstract class ArrayFindPatternNode extends RubyContextSourceNode {

    @Children final WriteLocalNode[] writeSlots;
    @Children final RubyNode[] conditions;
    @Child WriteLocalNode writeLeftSlot;
    @Child RubyNode leftCondition;
    @Child WriteLocalNode writeRightSlot;
    @Child RubyNode rightCondition;

    protected ArrayFindPatternNode(
            WriteLocalNode[] writeSlots,
            RubyNode[] conditions,
            WriteLocalNode writeLeftSlot,
            RubyNode leftCondition,
            WriteLocalNode writeRightSlot,
            RubyNode rightCondition) {
        this.writeSlots = writeSlots;
        this.conditions = conditions;
        this.writeLeftSlot = writeLeftSlot;
        this.leftCondition = leftCondition;
        this.writeRightSlot = writeRightSlot;
        this.rightCondition = rightCondition;
    }

    abstract RubyNode getValueNode();

    @Specialization
    boolean findPattern(VirtualFrame frame, RubyArray array,
            @Bind("array.getStore()") Object store,
            @CachedLibrary(limit = "storageStrategyLimit()") ArrayStoreLibrary stores,
            @Cached ReadSliceNormalizedNode readSliceNormalizedNode) {
        int size = array.size;
        int limit = size - writeSlots.length;

        outer: for (int start = 0; start <= limit; start++) {
            for (int i = 0; i < writeSlots.length; i++) {
                Object element = stores.read(store, start + i);
                writeSlots[i].assign(frame, element);
                if (!(boolean) conditions[i].execute(frame)) {
                    continue outer;
                }
            }

            writeLeftSlot.assign(frame, readSliceNormalizedNode.executeReadSlice(array, 0, start));
            if (!(boolean) leftCondition.execute(frame)) {
                continue;
            }

            int from = start + writeSlots.length;
            writeRightSlot.assign(frame, readSliceNormalizedNode.executeReadSlice(array, from, size - from));
            if (!(boolean) rightCondition.execute(frame)) {
                continue;
            }

            return true; // match found
        }

        return false;
    }


    @Override
    public RubyNode cloneUninitialized() {
        var writeSlotsCopies = new WriteLocalNode[writeSlots.length];
        for (int i = 0; i < writeSlots.length; i++) {
            writeSlotsCopies[i] = (WriteLocalNode) writeSlots[i].cloneUninitialized();
        }

        return ArrayFindPatternNodeGen.create(
                writeSlotsCopies,
                cloneUninitialized(conditions),
                (WriteLocalNode) writeLeftSlot.cloneUninitialized(),
                leftCondition.cloneUninitialized(),
                (WriteLocalNode) writeRightSlot.cloneUninitialized(),
                rightCondition.cloneUninitialized(),
                getValueNode()).copyFlags(this);
    }
}
