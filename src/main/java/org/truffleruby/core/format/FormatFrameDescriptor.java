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
package org.truffleruby.core.format;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.truffleruby.language.Nil;

public final class FormatFrameDescriptor {

    public static final int SOURCE_SLOT;
    public static final int SOURCE_END_POSITION_SLOT;
    public static final int SOURCE_START_POSITION_SLOT;
    public static final int SOURCE_POSITION_SLOT;
    public static final int SOURCE_ASSOCIATED_SLOT;
    public static final int OUTPUT_SLOT;
    public static final int OUTPUT_POSITION_SLOT;
    public static final int ASSOCIATED_SLOT;
    public static final FrameDescriptor FRAME_DESCRIPTOR;
    static {
        var builder = FrameDescriptor.newBuilder().defaultValue(Nil.INSTANCE);
        SOURCE_SLOT = builder.addSlot(FrameSlotKind.Object, "source", null);
        SOURCE_END_POSITION_SLOT = builder.addSlot(FrameSlotKind.Int, "source-end", null);
        SOURCE_START_POSITION_SLOT = builder.addSlot(FrameSlotKind.Int, "source-start-position", null);
        SOURCE_POSITION_SLOT = builder.addSlot(FrameSlotKind.Int, "source-position", null);
        SOURCE_ASSOCIATED_SLOT = builder.addSlot(FrameSlotKind.Object, "source-associated", null);
        OUTPUT_SLOT = builder.addSlot(FrameSlotKind.Object, "output", null);
        OUTPUT_POSITION_SLOT = builder.addSlot(FrameSlotKind.Int, "output-position", null);
        ASSOCIATED_SLOT = builder.addSlot(FrameSlotKind.Object, "associated", null);

        FRAME_DESCRIPTOR = builder.build();
    }
}
