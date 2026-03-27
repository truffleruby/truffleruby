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

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.format.exceptions.TooFewArgumentsException;
import org.truffleruby.core.string.TStringConstants;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(FormatGuards.class)
public abstract class FormatNode extends RubyBaseNode {

    public static final FormatNode[] EMPTY_ARRAY = new FormatNode[0];

    private final ConditionProfile writeMoreThanZeroBytes = ConditionProfile.create();
    private final ConditionProfile tooFewArgumentsProfile = ConditionProfile.create();
    private final ConditionProfile sourceRangeProfile = ConditionProfile.create();

    public abstract Object execute(VirtualFrame frame);

    public int getSourceEnd(VirtualFrame frame) {
        return frame.getInt(FormatFrameDescriptor.SOURCE_END_POSITION_SLOT);
    }

    public int getSourceStart(VirtualFrame frame) {
        return frame.getInt(FormatFrameDescriptor.SOURCE_START_POSITION_SLOT);
    }

    protected int getSourcePosition(VirtualFrame frame) {
        return frame.getInt(FormatFrameDescriptor.SOURCE_POSITION_SLOT);
    }

    protected void setSourcePosition(VirtualFrame frame, int position) {
        frame.setInt(FormatFrameDescriptor.SOURCE_POSITION_SLOT, position);
    }

    protected int advanceSourcePosition(VirtualFrame frame) {
        return advanceSourcePosition(frame, 1);
    }

    protected int advanceSourcePosition(VirtualFrame frame, int count) {
        final int sourcePosition = getSourcePosition(frame);

        if (tooFewArgumentsProfile.profile(sourcePosition + count > getSourceEnd(frame))) {
            throw new TooFewArgumentsException();
        }

        setSourcePosition(frame, sourcePosition + count);

        return sourcePosition;
    }

    protected int advanceSourcePositionNoThrow(VirtualFrame frame) {
        return advanceSourcePositionNoThrow(frame, 1, false);
    }

    protected int advanceSourcePositionNoThrow(VirtualFrame frame, int count, boolean consumePartial) {
        final int sourcePosition = getSourcePosition(frame);

        final int end = getSourceEnd(frame);

        if (sourceRangeProfile.profile(sourcePosition + count > end)) {
            if (consumePartial) {
                setSourcePosition(frame, end);
            }

            return -1;
        }

        setSourcePosition(frame, sourcePosition + count);

        return sourcePosition;
    }

    protected static Object getOutput(VirtualFrame frame) {
        return frame.getObject(FormatFrameDescriptor.OUTPUT_SLOT);
    }

    protected static void setOutput(VirtualFrame frame, Object output) {
        frame.setObject(FormatFrameDescriptor.OUTPUT_SLOT, output);
    }

    protected static int getOutputPosition(VirtualFrame frame) {
        return frame.getInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT);
    }

    protected static void setOutputPosition(VirtualFrame frame, int position) {
        frame.setInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT, position);
    }

    protected void writeByte(VirtualFrame frame, byte value) {
        final byte[] output = ensureCapacity(frame, 1);
        final int outputPosition = getOutputPosition(frame);
        output[outputPosition] = value;
        setOutputPosition(frame, outputPosition + 1);
    }

    protected static void writeBytes(VirtualFrame frame, byte[] values) {
        writeBytes(frame, values, 0, values.length);
    }

    protected static void writeBytes(VirtualFrame frame, byte[] values, int valuesOffset, int valuesLength) {
        byte[] output = ensureCapacity(frame, valuesLength);
        final int outputPosition = getOutputPosition(frame);
        System.arraycopy(values, valuesOffset, output, outputPosition, valuesLength);
        setOutputPosition(frame, outputPosition + valuesLength);
    }

    protected void writeNullBytes(VirtualFrame frame, int length) {
        if (writeMoreThanZeroBytes.profile(length > 0)) {
            ensureCapacity(frame, length);
            final int outputPosition = getOutputPosition(frame);
            setOutputPosition(frame, outputPosition + length);
        }
    }

    private static byte[] ensureCapacity(VirtualFrame frame, int length) {
        byte[] output = (byte[]) getOutput(frame);
        final int outputPosition = getOutputPosition(frame);

        if (outputPosition + length > output.length) {
            // If we ran out of output byte[], deoptimize and next time we'll allocate more

            CompilerDirectives.transferToInterpreterAndInvalidate();
            output = Arrays.copyOf(output,
                    ArrayUtils.capacity(getLanguage(null), output.length, outputPosition + length));
            setOutput(frame, output);
        }

        return output;
    }

    private static final Class<? extends ByteBuffer> HEAP_BYTE_BUFFER_CLASS = ByteBuffer
            .wrap(TStringConstants.EMPTY_BYTES)
            .getClass();

    public ByteBuffer wrapByteBuffer(VirtualFrame frame, byte[] source) {
        final int position = getSourcePosition(frame);
        final int end = getSourceEnd(frame);
        return CompilerDirectives
                .castExact(wrapByteBuffer(source, position, end - position), HEAP_BYTE_BUFFER_CLASS);
    }

    @TruffleBoundary
    private static ByteBuffer wrapByteBuffer(byte[] source, int position, int length) {
        return ByteBuffer.wrap(source, position, length);
    }

    @TruffleBoundary
    public static int safeGet(ByteBuffer encode) {
        while (encode.hasRemaining()) {
            int got = encode.get() & 0xff;

            if (got != 0) {
                return got;
            }
        }

        return 0;
    }

}
