/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.platform.ErrnoDescriptions;

public abstract class ErrnoErrorNode extends RubyBaseNode {

    @NeverDefault
    public static ErrnoErrorNode create() {
        return ErrnoErrorNodeGen.create();
    }

    @Child private DispatchNode formatMessageNode;

    public abstract RubySystemCallError execute(RubyClass rubyClass, int errno, Object extraMessage,
            Object location, Backtrace backtrace);

    @Specialization
    RubySystemCallError errnoError(
            RubyClass rubyClass, int errno, Object extraMessage, Object location, Backtrace backtrace,
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        final String errnoName = getContext().getCoreLibrary().getErrnoName(errno);

        final Object errnoDescription;
        final RubyClass errnoClass;

        if (errnoName == null) {
            errnoClass = getContext().getCoreLibrary().systemCallErrorClass;
            errnoDescription = nil;
        } else {
            if (rubyClass != null && rubyClass != getContext().getCoreLibrary().systemCallErrorClass) {
                errnoClass = rubyClass;
            } else {
                errnoClass = getContext().getCoreLibrary().getErrnoClass(errnoName);
            }

            errnoDescription = createString(fromJavaStringNode, ErrnoDescriptions.getDescription(errnoName),
                    Encodings.UTF_8);
        }

        final RubyString errorMessage = formatMessage(errnoDescription, errno, extraMessage, location);

        return ExceptionOperations
                .createSystemCallError(getContext(), errnoClass, errorMessage, errno, backtrace);
    }

    private RubyString formatMessage(Object errnoDescription, int errno, Object extraMessage, Object location) {
        assert extraMessage instanceof RubyString || extraMessage instanceof ImmutableRubyString ||
                extraMessage instanceof Nil;

        if (formatMessageNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            formatMessageNode = insert(DispatchNode.create());
        }

        if (location == null) {
            location = nil;
        }

        var arguments = new Object[]{ errnoDescription, errno, extraMessage, location };
        return (RubyString) formatMessageNode.call(
                getContext().getCoreLibrary().truffleExceptionOperationsModule,
                "format_errno_error_message",
                arguments);
    }

}
