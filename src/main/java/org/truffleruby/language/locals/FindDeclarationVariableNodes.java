/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;

public abstract class FindDeclarationVariableNodes {
    public static final class FrameSlotAndDepth {
        public final int slot;
        public final int depth;

        public FrameSlotAndDepth(int slot, int depth) {
            assert slot >= 0;
            this.slot = slot;
            this.depth = depth;
        }
    }

    public static MaterializedFrame getOuterDeclarationFrame(MaterializedFrame topFrame) {
        MaterializedFrame frame = topFrame;
        MaterializedFrame nextFrame;

        while ((nextFrame = RubyArguments.getDeclarationFrame(frame)) != null) {
            frame = nextFrame;
        }

        return frame;
    }

    private static int findSlot(FrameDescriptor descriptor, String name) {
        assert descriptor.getNumberOfAuxiliarySlots() == 0;
        int slots = descriptor.getNumberOfSlots();
        for (int slot = 0; slot < slots; slot++) {
            if (name.equals(descriptor.getSlotName(slot))) {
                return slot;
            }
        }

        return -1;
    }

    public static FrameSlotAndDepth findFrameSlotOrNull(String identifier, Frame frame) {
        CompilerAsserts.neverPartOfCompilation("Must not be called in PE code as the frame would escape");
        int depth = 0;
        do {
            int slot = findSlot(frame.getFrameDescriptor(), identifier);
            if (slot != -1) {
                return new FrameSlotAndDepth(slot, depth);
            }

            frame = RubyArguments.getDeclarationFrame(frame);
            depth++;
        } while (frame != null);
        return null;
    }

    @ReportPolymorphism // inline cache
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(FindDeclarationVariableNodes.class)
    public abstract static class FindAndReadDeclarationVariableNode extends RubyBaseNode {

        public abstract Object execute(Frame frame, Node node, String name, Object defaultValue);

        @Specialization(
                guards = { "name == cachedName", "frame.getFrameDescriptor() == cachedDescriptor" },
                limit = "getDefaultCacheLimit()")
        static Object getVariable(Frame frame, String name, Object defaultValue,
                @Cached("name") String cachedName,
                @Cached("frame.getFrameDescriptor()") FrameDescriptor cachedDescriptor,
                @Cached("findFrameSlotOrNull(name, frame)") FrameSlotAndDepth slotAndDepth,
                @Cached("createReadNode(slotAndDepth)") ReadFrameSlotNode readNode) {
            if (readNode != null) {
                final Frame storageFrame = RubyArguments.getDeclarationFrame(frame, slotAndDepth.depth);
                return readNode.executeRead(storageFrame);
            } else {
                return defaultValue;
            }
        }

        @Specialization(replaces = "getVariable")
        static Object getVariableSlow(Frame frame, String name, Object defaultValue) {
            return getVariableSlowBoundary(frame.materialize(), name, defaultValue);
        }

        @TruffleBoundary
        private static Object getVariableSlowBoundary(MaterializedFrame frame, String name, Object defaultValue) {
            final FrameSlotAndDepth slotAndDepth = findFrameSlotOrNull(name, frame);
            if (slotAndDepth == null) {
                return defaultValue;
            } else {
                final Frame storageFrame = RubyArguments.getDeclarationFrame(frame, slotAndDepth.depth);
                return storageFrame.getValue(slotAndDepth.slot);
            }
        }

        protected static ReadFrameSlotNode createReadNode(FrameSlotAndDepth frameSlot) {
            if (frameSlot == null) {
                return null;
            } else {
                return ReadFrameSlotNodeGen.create(frameSlot.slot);
            }
        }
    }
}
