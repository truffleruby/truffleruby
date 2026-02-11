/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

/** Used to translate Java exceptions thrown by the implementation of an interop/polyglot message (see
 * {@link com.oracle.truffle.api.interop.InteropLibrary InteropLibrary}) into Ruby exceptions, so that the interop
 * messages may be sent from Ruby, using the methods in the {@code Truffle::Interop} module. */
@GenerateUncached
@GenerateInline
@GenerateCached(value = false)
public abstract class TranslateInteropExceptionNode extends RubyBaseNode {


    public final RuntimeException execute(Node node, InteropException exception) {
        return execute(node, exception, false, null, null);
    }

    public final RuntimeException execute(Node node, InteropException exception, Object receiver) {
        return execute(node, exception, false, receiver, null);
    }

    public final RuntimeException executeInInvokeMember(Node node, InteropException exception, Object receiver,
            Object[] args) {
        return execute(node, exception, true, receiver, args);
    }

    public static RuntimeException executeUncached(InteropException exception) {
        return TranslateInteropExceptionNodeGen.getUncached().execute(null, exception, false, null, null);
    }

    protected abstract RuntimeException execute(
            Node node,
            InteropException exception,
            boolean inInvokeMember,
            Object receiver,
            Object[] args);

    @Specialization
    static RuntimeException handle(
            Node node, UnsupportedMessageException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).unsupportedMessageError(exception.getMessage(), node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, InvalidArrayIndexException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).indexErrorInvalidArrayIndexException(exception, node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, InvalidBufferOffsetException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).indexErrorInvalidBufferOffsetException(exception, node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, UnknownKeyException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        assert receiver != null;
        return new RaiseException(getContext(node),
                coreExceptions(node).keyError(exception.getMessage(), receiver, exception.getUnknownKey(), node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, UnknownIdentifierException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        if (inInvokeMember) {
            return new RaiseException(
                    getContext(node),
                    coreExceptions(node).noMethodErrorUnknownIdentifier(
                            receiver,
                            exception.getUnknownIdentifier(),
                            args,
                            exception,
                            node),
                    exception);
        } else {
            return new RaiseException(
                    getContext(node),
                    coreExceptions(node).nameErrorUnknownIdentifierException(exception, receiver, node),
                    exception);
        }
    }

    @Specialization
    static RuntimeException handle(
            Node node, UnsupportedTypeException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).typeErrorUnsupportedTypeException(exception, node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, ArityException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).argumentErrorMinMaxArity(
                        exception.getActualArity(),
                        exception.getExpectedMinArity(),
                        exception.getExpectedMaxArity(),
                        node),
                exception);
    }

    @Specialization
    static RuntimeException handle(
            Node node, StopIterationException exception, boolean inInvokeMember, Object receiver, Object[] args) {
        return new RaiseException(
                getContext(node),
                coreExceptions(node).stopIteration(exception.getMessage(), node),
                exception);
    }

}
