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
package org.truffleruby.core.format;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.core.exception.CoreExceptions;
import org.truffleruby.core.format.exceptions.CantCompressNegativeException;
import org.truffleruby.core.format.exceptions.CantConvertException;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.format.exceptions.OutsideOfStringException;
import org.truffleruby.core.format.exceptions.RangeException;
import org.truffleruby.core.format.exceptions.TooFewArgumentsException;
import org.truffleruby.language.control.RaiseException;

public abstract class FormatExceptionTranslator {

    @TruffleBoundary
    public static RuntimeException translate(RubyContext context, Node currentNode, FormatException exception) {
        final CoreExceptions coreExceptions = context.getCoreExceptions();

        if (exception instanceof TooFewArgumentsException) {
            return new RaiseException(context, coreExceptions.argumentErrorTooFewArguments(currentNode));
        } else if (exception instanceof NoImplicitConversionException) {
            final NoImplicitConversionException e = (NoImplicitConversionException) exception;
            return new RaiseException(
                    context,
                    coreExceptions.typeErrorNoImplicitConversion(e.getObject(), e.getTarget(), currentNode));
        } else if (exception instanceof OutsideOfStringException) {
            return new RaiseException(context, coreExceptions.argumentErrorXOutsideOfString(currentNode));
        } else if (exception instanceof CantCompressNegativeException) {
            return new RaiseException(context, coreExceptions.argumentErrorCantCompressNegativeNumbers(currentNode));
        } else if (exception instanceof RangeException) {
            final RangeException e = (RangeException) exception;
            return new RaiseException(context, coreExceptions.rangeError(e.getMessage(), currentNode));
        } else if (exception instanceof CantConvertException) {
            final CantConvertException e = (CantConvertException) exception;
            return new RaiseException(context, coreExceptions.typeError(e.getMessage(), currentNode));
        } else if (exception instanceof InvalidFormatException) {
            final InvalidFormatException e = (InvalidFormatException) exception;
            return new RaiseException(context, coreExceptions.argumentError(e.getMessage(), currentNode));
        } else {
            throw CompilerDirectives.shouldNotReachHere(exception);
        }
    }

}
