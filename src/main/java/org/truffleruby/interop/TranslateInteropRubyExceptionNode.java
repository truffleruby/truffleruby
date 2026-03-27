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
package org.truffleruby.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import org.truffleruby.core.cast.IntegerCastNode;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

/** Translates the Ruby exceptions nested under the {@code Truffle::Interop} into the corresponding (same name) Java
 * interop exceptions (see {@link com.oracle.truffle.api.interop.InteropLibrary InteropLibrary}. This is used to allow
 * users to implement the dynamic Interop API in Ruby (see /doc/contributor/interop.md and
 * /doc/contributor/interop_details.md). */
@GenerateUncached
public abstract class TranslateInteropRubyExceptionNode extends RubyBaseNode {

    public final AssertionError execute(RaiseException exception)
            throws UnsupportedMessageException {
        try {
            return execute(exception, 0, null, null);
        } catch (InvalidArrayIndexException | UnknownIdentifierException | UnsupportedTypeException | ArityException
                | UnknownKeyException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    public final AssertionError execute(RaiseException exception, long index)
            throws UnsupportedMessageException, InvalidArrayIndexException {
        try {
            return execute(exception, index, null, null);
        } catch (UnknownIdentifierException | UnsupportedTypeException | ArityException | UnknownKeyException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    public final AssertionError execute(RaiseException exception, String name)
            throws UnsupportedMessageException, UnknownIdentifierException {
        try {
            return execute(exception, 0, name, null);
        } catch (InvalidArrayIndexException | UnsupportedTypeException | ArityException | UnknownKeyException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    public final AssertionError execute(RaiseException exception, long index, Object value)
            throws UnsupportedMessageException, InvalidArrayIndexException, UnsupportedTypeException {
        try {
            return execute(exception, index, null, new Object[]{ value });
        } catch (UnknownIdentifierException | ArityException | UnknownKeyException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    public final AssertionError execute(RaiseException exception, String name, Object... arguments)
            throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
        try {
            return execute(exception, 0, name, arguments);
        } catch (InvalidArrayIndexException | UnknownKeyException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    public final AssertionError execute(RaiseException exception, Object key)
            throws UnsupportedMessageException, UnknownKeyException {
        try {
            return execute(exception, 0, null, new Object[]{ key });
        } catch (InvalidArrayIndexException | UnknownIdentifierException | UnsupportedTypeException
                | ArityException e) {
            throw handleBadErrorType(e, exception);
        }
    }

    protected abstract AssertionError execute(
            RaiseException exception, long index, String identifier, Object[] arguments)
            throws UnsupportedMessageException, InvalidArrayIndexException, UnknownIdentifierException,
            UnsupportedTypeException, ArityException, UnknownKeyException;

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().unsupportedMessageExceptionClass")
    AssertionError unsupportedMessageExceptionClass(
            RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached @Shared LogicalClassNode logicalClassNode) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create(exception);
    }

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().invalidArrayIndexExceptionClass")
    AssertionError invalidArrayIndexExceptionClass(
            RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached @Shared LogicalClassNode logicalClassNode) throws InvalidArrayIndexException {
        throw InvalidArrayIndexException.create(index, exception);
    }

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().unknownIdentifierExceptionClass")
    AssertionError unknownIdentifierExceptionClass(
            RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached @Shared LogicalClassNode logicalClassNode) throws UnknownIdentifierException {
        throw UnknownIdentifierException.create(identifier, exception);
    }

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().unsupportedTypeExceptionClass")
    AssertionError unsupportedTypeExceptionClass(
            RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached @Shared LogicalClassNode logicalClassNode) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(arguments, null, exception);
    }

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().arityExceptionClass")
    AssertionError arityExceptionClass(RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached DispatchNode dispatch,
            @Cached IntegerCastNode intCastNode,
            @Cached @Shared LogicalClassNode logicalClassNode) throws ArityException {
        int minExpected = intCastNode.execute(this, dispatch.call(exception.getException(), "min_expected"));
        int maxExpected = intCastNode.execute(this, dispatch.call(exception.getException(), "max_expected"));
        throw ArityException.create(minExpected, maxExpected, arguments.length, exception);
    }

    @Specialization(
            guards = "logicalClassNode.execute(exception.getException()) == coreLibrary().unknownKeyExceptionClass")
    AssertionError unknownKeyExceptionClass(RaiseException exception, long index, String identifier, Object[] arguments,
            @Cached @Shared LogicalClassNode logicalClassNode) throws UnknownKeyException {
        throw UnknownKeyException.create(arguments[0]); // the key can be any object, not just a string
    }

    @Fallback
    AssertionError fallback(RaiseException exception, long index, String identifier, Object[] arguments) {
        throw exception;
    }

    @TruffleBoundary
    protected AssertionError handleBadErrorType(InteropException e, RaiseException rubyException) {
        final RubyException exception = coreExceptions().runtimeError(
                Utils.concat("Wrong exception raised from a Ruby method implementing polyglot behavior: ", e),
                this);
        exception.cause = rubyException;
        throw new RaiseException(getContext(), exception);
    }

}
