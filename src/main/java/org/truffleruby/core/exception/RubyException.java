/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import java.util.Set;

import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.VMPrimitiveNodes.VMRaiseExceptionNode;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

import static org.truffleruby.language.RubyBaseNode.nil;

@ExportLibrary(InteropLibrary.class)
public class RubyException extends RubyDynamicObject implements ObjectGraphNode {

    /** message may be an instance of any class */
    public Object message;
    public Backtrace backtrace;
    public Object cause;

    public RubyProc formatter = null;
    public RubyArray backtraceStringArray = null;
    /** null (not yet computed), RubyArray, or nil (empty) */
    public Object backtraceLocations = null;
    /** null (not set), RubyArray of Strings, or nil (empty) */
    public Object customBacktrace = null;
    /** whether a Ruby exception referencing this exception as its cause exists. One of the way to prevent circles in
     * exception cause chains */
    public boolean usedAsACause = false;

    public RubyException(RubyClass rubyClass, Shape shape, Object message, Backtrace backtrace, Object cause) {
        super(rubyClass, shape);
        assert cause != null;
        this.message = message;
        this.backtrace = backtrace;
        this.cause = cause;

        if (cause instanceof RubyException) {
            ((RubyException) cause).usedAsACause = true;
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return message.toString();
    }

    public Node getLocation() {
        final Backtrace backtrace = this.backtrace;
        if (backtrace == null) {
            // The backtrace could be null if for example a user backtrace was passed to Kernel#raise
            return null;
        } else {
            return backtrace.getLocation();
        }
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, message);
        ObjectGraph.addProperty(reachable, cause);
    }

    // region Exception interop
    @ExportMessage
    public boolean isException() {
        return true;
    }

    @ExportMessage
    public RuntimeException throwException(
            @Bind Node node) {
        throw VMRaiseExceptionNode.reRaiseException(RubyContext.get(node), this);
    }

    @ExportMessage
    public ExceptionType getExceptionType() {
        return ExceptionType.RUNTIME_ERROR;
    }

    @ExportMessage
    public boolean hasExceptionCause() {
        return this.cause != nil;
    }

    @ExportMessage
    public Object getExceptionCause() throws UnsupportedMessageException {
        if (!hasExceptionCause()) {
            throw UnsupportedMessageException.create();
        }
        return this.cause;
    }

    @ExportMessage
    public boolean hasExceptionMessage() {
        return true;
    }

    @ExportMessage
    public Object getExceptionMessage() {
        return ExceptionOperations.messageToString(this);
    }

    @ExportMessage
    public boolean hasExceptionStackTrace() {
        return this.backtrace != null;
    }

    @TruffleBoundary
    @ExportMessage
    public Object getExceptionStackTrace() throws UnsupportedMessageException {
        if (!hasExceptionStackTrace()) {
            throw UnsupportedMessageException.create();
        }

        TruffleStackTraceElement[] stackTrace = this.backtrace.getStackTrace();
        Object[] items = new Object[stackTrace.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = stackTrace[i].getGuestObject();
        }
        return ArrayHelpers.createArray(RubyContext.get(null), RubyLanguage.get(null), items);
    }
    // endregion

}
