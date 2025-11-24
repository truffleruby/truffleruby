/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.language.control.RaiseException;

@ExportLibrary(ReflectionLibrary.class)
public final class BoxedValue implements TruffleObject {

    private static final Message READ_MEMBER = Message.resolve(InteropLibrary.class, "readMember");
    private static final Message INVOKE_MEMBER = Message.resolve(InteropLibrary.class, "invokeMember");

    private final Object value;

    public BoxedValue(Object value) {
        this.value = value;
    }

    @TruffleBoundary
    @ExportMessage
    protected Object send(Message message, Object[] args,
            @Bind Node node) throws Exception {
        if (message == READ_MEMBER || message == INVOKE_MEMBER) {
            RubyContext context = RubyContext.get(node);
            throw new RaiseException(context, context.getCoreExceptions().unsupportedMessageError(
                    "Methods should not be called on a BoxedValue as that would expose the potential Ruby object behind rather than relying on interop messages",
                    node));
        }
        ReflectionLibrary reflection = ReflectionLibrary.getUncached();
        return reflection.send(value, message, args);
    }

}
