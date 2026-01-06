/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.fiber.FiberNodes.FiberGetExceptionNode;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.control.KillException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

public abstract class ExceptionOperations {

    public enum ExceptionFormatter {
        // These patterns must all have 2 %s, for the method name and for the receiver string.
        SUPER_METHOD_ERROR("super: no superclass method '%s' for %s"),
        PROTECTED_METHOD_ERROR("protected method '%s' called for %s"),
        PRIVATE_METHOD_ERROR("private method '%s' called for %s"),
        NO_METHOD_ERROR("undefined method '%s' for %s"),
        NO_LOCAL_VARIABLE_OR_METHOD_ERROR("undefined local variable or method '%s' for %s");

        private final String fallbackFormat;

        ExceptionFormatter(String fallbackFormat) {
            this.fallbackFormat = fallbackFormat;
        }

        @TruffleBoundary
        public RubyProc getProc(RubyContext context) {
            final RubyModule truffleExceptionOperations = context.getCoreLibrary().truffleExceptionOperationsModule;
            final RubyConstant constant = truffleExceptionOperations.fields.getConstant(name());
            if (constant == null) { // core/truffle/exception_operations.rb not yet loaded
                return null;
            } else {
                return (RubyProc) constant.getValue();
            }
        }

        @TruffleBoundary
        public String getMessage(RubyProc formatterProc, String methodName, Object receiver) {
            if (formatterProc != null) {
                return null;
            } else {
                return getFallbackMessage(methodName, receiver);
            }
        }

        private String getFallbackMessage(String methodName, Object receiver) {
            return String.format(fallbackFormat, methodName, KernelNodes.ToSNode.uncachedBasicToS(receiver)) +
                    " (could not find formatter " + name() + ")";
        }
    }

    public static Object getExceptionObject(AbstractTruffleException exception) {
        assert !(exception instanceof KillException) : "KillException should not be used as an exception object: " +
                exception;
        if (exception instanceof RaiseException) {
            return ((RaiseException) exception).getException();
        } else {
            return exception;
        }
    }

    public static Object getExceptionObject(Node node, AbstractTruffleException exception,
            InlinedConditionProfile raiseExceptionProfile) {
        assert !(exception instanceof KillException) : "KillException should not be used as an exception object: " +
                exception;
        if (raiseExceptionProfile.profile(node, exception instanceof RaiseException)) {
            return ((RaiseException) exception).getException();
        } else {
            return exception;
        }
    }

    @TruffleBoundary
    public static String getMessage(Throwable throwable) {
        return throwable.getMessage();
    }

    @TruffleBoundary
    public static String messageFieldToString(RubyException exception) {
        Object message = exception.message;
        if (message == null || message == Nil.INSTANCE) {
            final ModuleFields exceptionClass = exception.getLogicalClass().fields;
            return exceptionClass.getName(); // What Exception#message would return if no message is set
        } else if (RubyStringLibrary.isRubyStringUncached(message)) {
            return StringOperations.getJavaString(message);
        } else {
            return message.toString();
        }
    }

    @TruffleBoundary
    public static String messageToString(RubyException exception) {
        Object messageObject = null;
        try {
            messageObject = DispatchNode.getUncached().call(exception, "message");
        } catch (RaiseException e) {
            // Fall back to the internal message field
        }
        if (messageObject != null && RubyStringLibrary.isRubyStringUncached(messageObject)) {
            return StringOperations.getJavaString(messageObject);
        } else {
            return messageFieldToString(exception);
        }
    }

    public static RubyException createRubyException(RubyContext context, RubyClass rubyClass, Object message,
            Node node, Throwable javaException) {
        final Backtrace backtrace = context.getCallStack().getBacktrace(node, 0, javaException);
        return createRubyException(context, rubyClass, message, backtrace);
    }

    @TruffleBoundary
    public static RubyException createRubyException(RubyContext context, RubyClass rubyClass, Object message,
            Backtrace backtrace) {
        final RubyLanguage language = context.getLanguageSlow();
        final Object cause = FiberGetExceptionNode.getLastException(language);
        context.getCoreExceptions().showExceptionIfDebug(rubyClass, message, backtrace);
        final Shape shape = language.exceptionShape;
        return new RubyException(rubyClass, shape, message, backtrace, cause);
    }

    @TruffleBoundary
    public static RubyException createSystemStackError(RubyContext context, Object message, Backtrace backtrace,
            boolean showExceptionIfDebug) {
        final RubyLanguage language = context.getLanguageSlow();
        final RubyClass rubyClass = context.getCoreLibrary().systemStackErrorClass;
        final Object cause = FiberGetExceptionNode.getLastException(language);
        if (showExceptionIfDebug) {
            context.getCoreExceptions().showExceptionIfDebug(rubyClass, message, backtrace);
        }
        final Shape shape = language.exceptionShape;
        return new RubyException(rubyClass, shape, message, backtrace, cause);
    }

    @TruffleBoundary
    public static RubySystemCallError createSystemCallError(RubyContext context, RubyClass rubyClass,
            Object message, int errno, Backtrace backtrace) {
        final RubyLanguage language = context.getLanguageSlow();
        final Object cause = FiberGetExceptionNode.getLastException(language);
        context.getCoreExceptions().showExceptionIfDebug(rubyClass, message, backtrace);
        final Shape shape = language.systemCallErrorShape;
        return new RubySystemCallError(rubyClass, shape, message, backtrace, cause, errno);
    }

    /** @see org.truffleruby.cext.CExtNodes.RaiseExceptionNode */
    public static RuntimeException rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        } else if (throwable instanceof Error) {
            throw (Error) throwable;
        } else {
            throw CompilerDirectives.shouldNotReachHere("Checked Java Throwable rethrown", throwable);
        }
    }

}
