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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import org.truffleruby.language.arguments.RubyArguments;

public final class DeclarationVariables {

    public static MaterializedFrame getOuterDeclarationFrame(MaterializedFrame topFrame) {
        MaterializedFrame frame = topFrame;
        MaterializedFrame nextFrame;

        while ((nextFrame = RubyArguments.getDeclarationFrame(frame)) != null) {
            frame = nextFrame;
        }

        return frame;
    }

    public static int findSlot(FrameDescriptor descriptor, String name) {
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

}
