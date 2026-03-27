/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.read.bytes;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.MissingValue;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.Nil;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChild("value")
public abstract class ReadStringPointerNode extends FormatNode {

    @Child private TruffleString.FromByteArrayNode fromByteArrayNode = TruffleString.FromByteArrayNode.create();

    private final BranchProfile errorProfile = BranchProfile.create();
    private final int limit;

    public ReadStringPointerNode(int limit) {
        this.limit = limit;
    }

    @Specialization
    MissingValue decode(Nil nil) {
        return MissingValue.INSTANCE;
    }

    @Specialization
    RubyString read(VirtualFrame frame, long address,
            @CachedLibrary(limit = "1") InteropLibrary interop) {
        final Pointer pointer = new Pointer(getContext(), address);
        checkAssociated(
                (Pointer[]) frame.getObject(FormatFrameDescriptor.SOURCE_ASSOCIATED_SLOT),
                pointer);

        final byte[] bytes = pointer.readZeroTerminatedByteArray(
                getContext(),
                interop,
                0,
                limit);
        return createString(fromByteArrayNode, bytes, Encodings.US_ASCII);
    }

    private void checkAssociated(Pointer[] associated, Pointer reading) {
        if (associated != null) {
            for (Pointer pointer : associated) {
                if (pointer.equals(reading)) {
                    return;
                }
            }
        }

        errorProfile.enter();
        throw new RaiseException(
                getContext(),
                getContext().getCoreExceptions().argumentError("no associated pointer", this));
    }

}
