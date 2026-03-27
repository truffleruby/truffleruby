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

import java.util.List;

import org.truffleruby.RubyLanguage;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.backtrace.InternalRootNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

/** The node at the root of a pack expression. */
public final class FormatRootNode extends RubyBaseRootNode implements InternalRootNode {

    private final FormatEncoding encoding;

    @Child private FormatNode child;

    @CompilationFinal private int expectedLength = 0;
    private final boolean acceptOutput;
    private final boolean acceptOutputPosition;

    public FormatRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FormatEncoding encoding,
            FormatNode child,
            boolean acceptOutput,
            boolean acceptOutputPosition) {
        super(language, FormatFrameDescriptor.FRAME_DESCRIPTOR, sourceSection);
        this.encoding = encoding;
        this.child = child;
        this.acceptOutput = acceptOutput;
        this.acceptOutputPosition = acceptOutputPosition;
    }

    /** Accepts the following arguments stored in a frame: source array, its length, output buffer as a bytes array,
     * (optional) position in the output buffer to start from */
    @SuppressWarnings("unchecked")
    @Override
    public Object execute(VirtualFrame frame) {
        frame.setObject(FormatFrameDescriptor.SOURCE_SLOT, frame.getArguments()[0]);
        frame.setInt(FormatFrameDescriptor.SOURCE_END_POSITION_SLOT, (int) frame.getArguments()[1]);
        frame.setInt(FormatFrameDescriptor.SOURCE_START_POSITION_SLOT, 0);
        frame.setInt(FormatFrameDescriptor.SOURCE_POSITION_SLOT, 0);

        final byte[] outputInit = acceptOutput ? (byte[]) frame.getArguments()[2] : new byte[expectedLength];
        frame.setObject(FormatFrameDescriptor.OUTPUT_SLOT, outputInit);

        final int outputPosition = acceptOutputPosition ? (int) frame.getArguments()[3] : 0;
        frame.setInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT, outputPosition);

        frame.setObject(FormatFrameDescriptor.ASSOCIATED_SLOT, null);

        child.execute(frame);

        final int outputLength = frame.getInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT);

        if (outputLength > expectedLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            /* Don't over-compensate and allocate 2x or something like that for next time, as we have to copy the byte[]
             * at the end if it's too big to make it fit its contents. In the ideal case the byte[] is exactly the right
             * size. If we have to keep making it bigger in the slow-path, we can live with that. */

            expectedLength = outputLength;
        }

        final byte[] output = (byte[]) frame.getObject(FormatFrameDescriptor.OUTPUT_SLOT);

        final List<Pointer> associated = (List<Pointer>) frame.getObject(FormatFrameDescriptor.ASSOCIATED_SLOT);

        final Pointer[] associatedArray;
        if (associated != null) {
            associatedArray = associatedToArray(associated);
        } else {
            associatedArray = null;
        }

        return new BytesResult(output, outputLength, encoding, associatedArray);
    }

    @TruffleBoundary
    private Pointer[] associatedToArray(List<Pointer> associated) {
        return associated.toArray(Pointer.EMPTY_ARRAY);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public String getName() {
        return "format";
    }

    public int getExpectedLength() {
        return expectedLength;
    }

    @Override
    public String toString() {
        return getName();
    }
}
