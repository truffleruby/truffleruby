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
package org.truffleruby.language;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class DataNode extends RubyContextSourceNode {

    @Child private TruffleString.FromJavaStringNode fromJavaStringNode;
    @Child private DispatchNode callHelperNode;

    private final int endPosition; // position in a source file at the beginning of a line right after __END__ keyword in bytes

    public DataNode(int endPosition) {
        this.endPosition = endPosition;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (fromJavaStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fromJavaStringNode = insert(TruffleString.FromJavaStringNode.create());
        }

        if (callHelperNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callHelperNode = insert(DispatchNode.create());
        }

        final String path = getPath();
        final RubyEncoding rubyLocaleEncoding = getContext().getEncodingManager().getLocaleEncoding();
        final RubyString pathString = createString(fromJavaStringNode, path, rubyLocaleEncoding);
        final Object data = callHelperNode
                .call(coreLibrary().truffleInternalModule, "get_data", pathString, endPosition);

        coreLibrary().objectClass.fields.setConstant(getContext(), null, "DATA", data);

        return nil;
    }

    @TruffleBoundary
    private String getPath() {
        return RubyLanguage.getPath(getEncapsulatingSourceSection().getSource());
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new DataNode(endPosition);
        return copy.copyFlags(this);
    }

}
