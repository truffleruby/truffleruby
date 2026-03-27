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
package org.truffleruby.core.format.convert;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.cext.CExtNodes;
import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.Nil;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class StringToPointerNode extends FormatNode {

    @Specialization
    long toPointer(Nil nil) {
        return 0;
    }

    @SuppressWarnings("unchecked")
    @Specialization(guards = "strings.isRubyString(this, string)", limit = "1")
    static long toPointer(VirtualFrame frame, Object string,
            @Cached CExtNodes.StringToNativeNode stringToNativeNode,
            @Cached RubyStringLibrary strings,
            @Bind Node node) {

        final Pointer pointer = stringToNativeNode.executeToNative(node, string, true);

        List<Pointer> associated = (List<Pointer>) frame.getObject(FormatFrameDescriptor.ASSOCIATED_SLOT);

        if (associated == null) {
            associated = new ArrayList<>();
            frame.setObject(FormatFrameDescriptor.ASSOCIATED_SLOT, associated);
        }

        add(associated, pointer);

        return pointer.getAddress();
    }

    @TruffleBoundary
    private static void add(List<Pointer> list, Pointer ptr) {
        list.add(ptr);
    }
}
