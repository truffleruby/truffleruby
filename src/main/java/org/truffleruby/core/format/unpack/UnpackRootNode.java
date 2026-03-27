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
package org.truffleruby.core.format.unpack;

import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.backtrace.InternalRootNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public final class UnpackRootNode extends RubyBaseRootNode implements InternalRootNode {

    private final RubyLanguage language;

    @Child private FormatNode child;

    @CompilationFinal private int expectedLength;

    public UnpackRootNode(RubyLanguage language, SourceSection sourceSection, FormatNode child) {
        super(language, FormatFrameDescriptor.FRAME_DESCRIPTOR, sourceSection);
        this.language = language;
        this.child = child;
        expectedLength = language.options.ARRAY_UNINITIALIZED_SIZE;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments();

        frame.setObject(FormatFrameDescriptor.SOURCE_SLOT, arguments[0]);
        frame.setInt(FormatFrameDescriptor.SOURCE_END_POSITION_SLOT, (int) arguments[1]);
        frame.setInt(FormatFrameDescriptor.SOURCE_START_POSITION_SLOT, (int) arguments[2]);
        frame.setInt(FormatFrameDescriptor.SOURCE_POSITION_SLOT, (int) arguments[2]);
        frame.setObject(FormatFrameDescriptor.SOURCE_ASSOCIATED_SLOT, arguments[3]);
        frame.setObject(FormatFrameDescriptor.OUTPUT_SLOT, new Object[expectedLength]);
        frame.setInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT, 0);

        child.execute(frame);

        final int outputLength = frame.getInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT);

        if (outputLength > expectedLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            expectedLength = ArrayUtils.capacity(language, expectedLength, outputLength);
        }

        final Object[] output = (Object[]) frame.getObject(FormatFrameDescriptor.OUTPUT_SLOT);

        return new ArrayResult(output, outputLength);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public String getName() {
        return "unpack";
    }

    @Override
    public String toString() {
        return getName();
    }

}
