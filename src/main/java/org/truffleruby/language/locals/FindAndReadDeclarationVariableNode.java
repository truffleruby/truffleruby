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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.arguments.RubyArguments;

@ReportPolymorphism // inline cache
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(DeclarationVariables.class)
public abstract class FindAndReadDeclarationVariableNode extends RubyBaseNode {

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
        final FrameSlotAndDepth slotAndDepth = DeclarationVariables.findFrameSlotOrNull(name, frame);
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
