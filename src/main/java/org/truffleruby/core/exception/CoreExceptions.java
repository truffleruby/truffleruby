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

import static org.truffleruby.core.array.ArrayHelpers.createArray;

import java.io.IOException;
import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.exception.ExceptionOperations.ExceptionFormatter;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.CoreStrings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.fiber.FiberNodes.FiberGetExceptionNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.backtrace.BacktraceFormatter.FormattingFlags;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.objects.LogicalClassNode;

public final class CoreExceptions {

    private final RubyLanguage language;
    private final RubyContext context;
    private final BacktraceFormatter debugBacktraceFormatter;

    public CoreExceptions(RubyContext context, RubyLanguage language) {
        this.language = language;
        this.context = context;
        this.debugBacktraceFormatter = new BacktraceFormatter(
                context,
                language,
                EnumSet.of(FormattingFlags.OMIT_EXCEPTION));
    }

    public void showExceptionIfDebug(RubyException exception) {
        showExceptionIfDebug(
                exception.getLogicalClass(),
                exception.message,
                exception.backtrace);
    }

    @TruffleBoundary
    public void showExceptionIfDebug(RubyException rubyException, Backtrace backtrace) {
        if (context.getCoreLibrary().getDebug() == Boolean.TRUE) {
            final RubyClass rubyClass = rubyException.getLogicalClass();
            final Object message = DispatchNode.getUncached().call(rubyException, "to_s");
            showExceptionIfDebug(rubyClass, message, backtrace);
        }
    }

    @TruffleBoundary
    public void showExceptionIfDebug(RubyClass rubyClass, Object message, Backtrace backtrace) {
        if (context.getCoreLibrary().getDebug() == Boolean.TRUE) {
            final String exceptionClass = rubyClass.fields.getName();
            String from = "";
            if (backtrace != null && backtrace.getStackTrace().length > 0) {
                from = " at " + debugBacktraceFormatter.formatLine(backtrace.getStackTrace(), 0, null);
            }
            if (RubyStringLibrary.isRubyStringUncached(message)) {
                message = StringOperations.getJavaString(message);
            }
            final String output = "Exception '" + exceptionClass + "'" + from + " - " + message + "\n";
            if (context.getCoreLibrary().isLoaded()) {
                RubyString outputString = StringOperations.createUTF8String(context, language, output);
                Object stderr = context.getCoreLibrary().getStderr();

                DispatchNode.getUncached().call(stderr, "write", outputString);
            } else {
                context.getEnvErrStream().println(output);
            }
        }
    }

    @TruffleBoundary
    public String inspect(Object value) {
        Object rubyString = DispatchNode.getUncached().call(
                context.getCoreLibrary().truffleTypeModule, "rb_inspect", value);
        return StringOperations.getJavaString(rubyString);
    }

    @TruffleBoundary
    public String inspectReceiver(Object receiver) {
        Object rubyString = DispatchNode.getUncached().call(
                context.getCoreLibrary().truffleExceptionOperationsModule, "receiver_string", receiver);
        return StringOperations.getJavaString(rubyString);
    }

    @TruffleBoundary
    public String inspectFrozenObject(Object object) {
        Object rubyString = DispatchNode.getUncached().call(
                context.getCoreLibrary().truffleExceptionOperationsModule, "inspect_frozen_object", object);
        return StringOperations.getJavaString(rubyString);
    }

    // ArgumentError

    public RubyException argumentErrorOneHashRequired(RubyBaseNode currentNode) {
        return argumentError(coreStrings().ONE_HASH_REQUIRED.createInstance(RubyContext.get(currentNode)), currentNode,
                null);
    }

    public RubyException argumentError(String message, Node currentNode) {
        return argumentError(message, currentNode, null);
    }

    public RubyException argumentErrorProcWithoutBlock(RubyBaseNode currentNode) {
        return argumentError(coreStrings().PROC_WITHOUT_BLOCK.createInstance(RubyContext.get(currentNode)), currentNode,
                null);
    }

    public RubyException argumentErrorTooFewArguments(Node currentNode) {
        return argumentError(coreStrings().TOO_FEW_ARGUMENTS.createInstance(RubyContext.get(currentNode)), currentNode,
                null);
    }

    public RubyException argumentErrorTimeIntervalPositive(Node currentNode) {
        return argumentError(coreStrings().TIME_INTERVAL_MUST_BE_POS.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    public RubyException argumentErrorXOutsideOfString(Node currentNode) {
        return argumentError(coreStrings().X_OUTSIDE_OF_STRING.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    public RubyException argumentErrorCantCompressNegativeNumbers(Node currentNode) {
        return argumentError(coreStrings().CANT_COMPRESS_NEGATIVE.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    public RubyException argumentErrorOutOfRange(RubyBaseNode currentNode) {
        return argumentError(coreStrings().ARGUMENT_OUT_OF_RANGE.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    public RubyException argumentErrorNegativeArraySize(RubyBaseNode currentNode) {
        return argumentError(coreStrings().NEGATIVE_ARRAY_SIZE.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    public RubyException argumentErrorCharacterRequired(Node currentNode) {
        return argumentError("%c requires a character", currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorUnknownKeywords(Object[] keys, Node currentNode) {
        if (keys.length == 1) {
            return argumentError("unknown keyword: " + inspect(keys[0]), currentNode);
        }

        final String[] names = new String[keys.length];

        for (int i = 0; i < keys.length; i++) {
            names[i] = inspect(keys[i]);
        }

        return argumentError("unknown keywords: " + String.join(", ", names), currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorInvalidByteSequence(RubyEncoding encoding, Node currentNode) {
        return argumentError("invalid byte sequence in " + encoding, currentNode);
    }


    @TruffleBoundary
    public RubyException argumentErrorInvalidRadix(int radix, Node currentNode) {
        return argumentError(StringUtils.format("invalid radix %d", radix), currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorMissingKeywords(Object[] keys, Node currentNode) {
        if (keys.length == 1) {
            return argumentError("missing keyword: " + inspect(keys[0]), currentNode);
        }

        final String[] names = new String[keys.length];

        for (int i = 0; i < keys.length; i++) {
            names[i] = inspect(keys[i]);
        }

        return argumentError("missing keywords: " + String.join(", ", names), currentNode);
    }

    @TruffleBoundary
    public RubyException argumentError(int passed, int required, Node currentNode) {
        return argumentError(
                StringUtils.format("wrong number of arguments (given %d, expected %d)", passed, required),
                currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorPlus(int passed, int required, Node currentNode) {
        return argumentError(
                StringUtils.format("wrong number of arguments (given %d, expected %d+)", passed, required),
                currentNode);
    }

    @TruffleBoundary
    public RubyException argumentError(int passed, int required, int optional, Node currentNode) {
        return argumentError(StringUtils.format(
                "wrong number of arguments (given %d, expected %d..%d)",
                passed,
                required,
                required + optional), currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorMinMaxArity(int passed, int minArity, int maxArity, Node currentNode) {
        if (minArity == maxArity) {
            return argumentError(passed, minArity, currentNode);
        } else if (maxArity < 0) {
            return argumentErrorPlus(passed, minArity, currentNode);
        } else {
            return argumentError(passed, minArity, maxArity - minArity, currentNode);
        }
    }

    public RubyException argumentErrorEmptyVarargs(Node currentNode) {
        return argumentError(coreStrings().WRONG_ARGS_ZERO_PLUS_ONE.createInstance(RubyContext.get(currentNode)),
                currentNode, null);
    }

    @TruffleBoundary
    public RubyException argumentErrorWrongArgumentType(Object object, String expectedType, Node currentNode) {
        String badClassName = LogicalClassNode.getUncached().execute(object).fields.getName();
        return argumentError(
                StringUtils.format("wrong argument type %s (should be %s)", badClassName, expectedType),
                currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorInvalidStringToInteger(String string, Node currentNode) {
        return argumentError("invalid value for Integer(): " + string, currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorNoReceiver(Node currentNode) {
        return argumentError("no receiver is available", currentNode);
    }

    @TruffleBoundary
    public RubyException argumentErrorEncodingAlreadyRegistered(String nameString, Node currentNode) {
        return argumentError(StringUtils.format("encoding %s is already registered", nameString), currentNode);
    }

    @TruffleBoundary
    public RubyException argumentError(String message, Node currentNode, Throwable javaThrowable) {
        return argumentError(StringOperations.createUTF8String(context, language,
                message), currentNode, javaThrowable);
    }

    public RubyException argumentError(RubyString message, Node currentNode, Throwable javaThrowable) {
        RubyClass exceptionClass = context.getCoreLibrary().argumentErrorClass;
        return ExceptionOperations.createRubyException(context, exceptionClass, message, currentNode, javaThrowable);
    }

    @TruffleBoundary
    public RubyException argumentErrorCantUnfreeze(Object self, Node currentNode) {
        String className = LogicalClassNode.getUncached().execute(self).fields.getName();
        return argumentError(StringUtils.format("can't unfreeze %s", className), currentNode);
    }

    // FrozenError

    @TruffleBoundary
    public RubyException frozenError(Object object, Node currentNode) {
        String className = LogicalClassNode.getUncached().execute(object).fields.getName();
        String string = inspectFrozenObject(object);
        return frozenError(StringUtils.format("can't modify frozen %s: %s", className, string), currentNode,
                object);
    }

    public RubyException frozenError(Object object, String name, Node currentNode) {
        String string = inspectFrozenObject(object);
        return frozenError(StringUtils.format("can't modify frozen %s: %s", name, string), currentNode,
                object);
    }

    @TruffleBoundary
    private RubyException frozenError(String message, Node currentNode, Object receiver) {
        RubyClass exceptionClass = context.getCoreLibrary().frozenErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);
        showExceptionIfDebug(exceptionClass, errorMessage, backtrace);
        return new RubyFrozenError(
                exceptionClass,
                language.frozenErrorShape,
                errorMessage,
                backtrace,
                cause,
                receiver);
    }

    // RuntimeError

    public RubyException runtimeErrorClassVariableTopLevel(Node currentNode) {
        return runtimeError("class variable access from toplevel", currentNode);
    }

    public RubyException runtimeErrorCoverageNotEnabled(Node currentNode) {
        return runtimeError("coverage measurement is not enabled", currentNode);
    }

    @TruffleBoundary
    public RubyException runtimeError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().runtimeErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException runtimeError(String message, Node currentNode, Throwable javaThrowable) {
        RubyClass exceptionClass = context.getCoreLibrary().runtimeErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations
                .createRubyException(context, exceptionClass, errorMessage, currentNode, javaThrowable);
    }

    @TruffleBoundary
    public RubyException runtimeError(String message, Backtrace backtrace) {
        RubyClass exceptionClass = context.getCoreLibrary().runtimeErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, backtrace);
    }

    // SystemStackError

    @TruffleBoundary
    public RubyException systemStackErrorStackLevelTooDeep(Node currentNode, StackOverflowError javaThrowable,
            boolean showExceptionIfDebug) {
        final StackTraceElement[] stackTrace = javaThrowable.getStackTrace();
        final String topOfTheStack = stackTrace.length > 0
                ? BacktraceFormatter.formatStackTraceElement(stackTrace[0])
                : "<empty Java stacktrace>";
        final String message = coreStrings().STACK_LEVEL_TOO_DEEP + "\n\tfrom " + topOfTheStack;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode, 0, javaThrowable);
        final RubyString messageString = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createSystemStackError(context, messageString, backtrace, showExceptionIfDebug);
    }

    // NoMemoryError

    @TruffleBoundary
    public RubyException noMemoryError(Node currentNode, OutOfMemoryError javaThrowable) {
        RubyClass exceptionClass = context.getCoreLibrary().noMemoryErrorClass;
        return ExceptionOperations.createRubyException(
                context,
                exceptionClass,
                coreStrings().FAILED_TO_ALLOCATE_MEMORY.createInstance(context),
                currentNode,
                javaThrowable);
    }

    // NoMatchingPatternError

    @TruffleBoundary
    public RubyException noMatchingPatternError(Object errorMessage, Node currentNode) {
        assert RubyStringLibrary.isRubyStringUncached(errorMessage);
        RubyClass exceptionClass = context.getCoreLibrary().noMatchingPatternErrorClass;
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // Errno

    @TruffleBoundary
    public RubyException mathDomainErrorAcos(Node currentNode) {
        return mathDomainError("acos", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorAcosh(Node currentNode) {
        return mathDomainError("acosh", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorAsin(Node currentNode) {
        return mathDomainError("asin", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorAtanh(Node currentNode) {
        return mathDomainError("atanh", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorGamma(Node currentNode) {
        return mathDomainError("gamma", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorLog2(Node currentNode) {
        return mathDomainError("log2", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorLog10(Node currentNode) {
        return mathDomainError("log10", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainErrorLog(Node currentNode) {
        return mathDomainError("log", currentNode);
    }

    @TruffleBoundary
    public RubyException mathDomainError(String method, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().mathDomainErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language,
                StringUtils.format("Numerical argument is out of domain - \"%s\"", method));
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);

        return ExceptionOperations
                .createSystemCallError(
                        context,
                        exceptionClass,
                        errorMessage,
                        context.getCoreLibrary().getErrnoValue("EDOM"),
                        backtrace);
    }

    // IndexError

    @TruffleBoundary
    public RubyException indexError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().indexErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException indexErrorOutOfString(int index, Node currentNode) {
        return indexError(StringUtils.format("index %d out of string", index), currentNode);
    }

    @TruffleBoundary
    public RubyException indexTooSmallError(String type, int index, int length, Node currentNode) {
        return indexError(
                StringUtils.format("index %d too small for %s; minimum: -%d", index, type, length),
                currentNode);
    }

    @TruffleBoundary
    public RubyException negativeLengthError(int length, Node currentNode) {
        return indexError(StringUtils.format("negative length (%d)", length), currentNode);
    }

    @TruffleBoundary
    public RubyException indexErrorInvalidIndex(Node currentNode) {
        return indexError("invalid index", currentNode);
    }

    @TruffleBoundary
    public RubyException indexErrorInvalidArrayIndexException(InvalidArrayIndexException exception, Node currentNode) {
        return indexError("invalid array index " + exception.getInvalidIndex(), currentNode);
    }

    @TruffleBoundary
    public RubyException indexErrorInvalidBufferOffsetException(InvalidBufferOffsetException exception,
            Node currentNode) {
        return indexError(
                "invalid buffer offset " + exception.getByteOffset() + " for buffer of length " + exception.getLength(),
                currentNode);
    }

    // KeyError

    @TruffleBoundary
    public RubyException keyError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().keyErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException keyError(UnknownKeyException exception, Node currentNode) {
        return keyError(exception.getMessage(), currentNode);
    }

    // StopIteration

    @TruffleBoundary
    public RubyException stopIteration(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().stopIterationClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // LocalJumpError

    @TruffleBoundary
    public RubyException localJumpError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().localJumpErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    public RubyException noBlockGiven(Node currentNode) {
        return localJumpError("no block given", currentNode);
    }

    public RubyException breakFromProcClosure(Node currentNode) {
        return localJumpError("break from proc-closure", currentNode);
    }

    public RubyException unexpectedReturn(Node currentNode) {
        return localJumpError("unexpected return", currentNode);
    }

    public RubyException noBlockToYieldTo(Node currentNode) {
        return localJumpError("no block given (yield)", currentNode);
    }

    // TypeError

    public RubyException typeErrorCantCreateInstanceOfSingletonClass(Node currentNode) {
        return typeError("can't create instance of singleton class", currentNode, null);
    }

    @TruffleBoundary
    public RubyException typeErrorNotASingletonClass(Node currentNode, RubyClass rubyClass) {
        String className = rubyClass.fields.getName();
        return typeError(StringUtils.format("'%s' is not a singleton class", className), currentNode, null);
    }

    @TruffleBoundary
    public RubyException superclassMismatch(String name, Node currentNode) {
        return typeError("superclass mismatch for class " + name, currentNode);
    }

    public RubyException typeError(String message, Node currentNode) {
        return typeError(message, currentNode, null);
    }

    @TruffleBoundary
    public RubyException typeErrorAllocatorUndefinedFor(RubyClass rubyClass, Node currentNode) {
        String className = rubyClass.fields.getName();
        return typeError(StringUtils.format("allocator undefined for %s", className), currentNode);
    }

    public RubyException typeErrorCantDefineSingleton(Node currentNode) {
        return typeError("can't define singleton", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorCantConvertTo(Object from, String toClass, String methodUsed, Object result,
            Node currentNode) {
        String fromClass = LogicalClassNode.getUncached().execute(from).fields.getName();
        return typeError(StringUtils.format(
                "can't convert %s to %s (%s#%s gives %s)",
                fromClass,
                toClass,
                fromClass,
                methodUsed,
                LogicalClassNode.getUncached().execute(result).toString()), currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorCantConvertInto(Object from, String toClass, Node currentNode) {
        return typeError(StringUtils.format(
                "can't convert %s into %s",
                LogicalClassNode.getUncached().execute(from).fields.getName(),
                toClass), currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorIsNotA(Object value, String expectedType, Node currentNode) {
        return typeErrorIsNotA(inspectReceiver(value), expectedType, currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorIsNotA(String value, String expectedType, Node currentNode) {
        return typeError(value + " is not a " + expectedType, currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorIsNotAOrB(Object value, String expectedTypeA, String expectedTypeB,
            Node currentNode) {
        return typeError(
                StringUtils.format("%s is not a %s nor a %s", inspectReceiver(value), expectedTypeA, expectedTypeB),
                currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorIsNotAClassModule(Object value, Node currentNode) {
        return typeError(inspectReceiver(value) + " is not a class/module", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorNoImplicitConversion(Object from, String to, Node currentNode) {
        return typeError(StringUtils.format(
                "no implicit conversion of %s into %s",
                LogicalClassNode.getUncached().execute(from).fields.getName(),
                to), currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorBadCoercion(Object from, String to, String coercionMethod, Object coercedTo,
            Node currentNode) {
        String badClassName = LogicalClassNode.getUncached().execute(from).fields.getName();
        return typeError(
                StringUtils.format(
                        "can't convert %s to %s (%s#%s gives %s)",
                        badClassName,
                        to,
                        badClassName,
                        coercionMethod,
                        LogicalClassNode.getUncached().execute(coercedTo).fields.getName()),
                currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorCantDump(Object object, Node currentNode) {
        String logicalClass = LogicalClassNode.getUncached().execute(object).fields.getName();
        return typeError(StringUtils.format("can't dump %s", logicalClass), currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorWrongArgumentType(Object object, String expectedType, Node currentNode) {
        String badClassName = LogicalClassNode.getUncached().execute(object).fields.getName();
        return typeError(
                StringUtils.format("wrong argument type %s (expected %s)", badClassName, expectedType),
                currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorAlreadyInitializedClass(Node currentNode) {
        return typeError("already initialized class", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorSubclassSingletonClass(Node currentNode) {
        return typeError("can't make subclass of singleton class", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorSubclassClass(Node currentNode) {
        return typeError("can't make subclass of Class", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorSuperclassMustBeClass(Node currentNode) {
        return typeError("superclass must be a Class", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorInheritUninitializedClass(Node currentNode) {
        return typeError("can't inherit uninitialized class", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorRescueInvalidClause(Node currentNode) {
        return typeError("class or module required for rescue clause", currentNode);
    }

    @TruffleBoundary
    public RubyException typeErrorExpectedProcOrMethodOrUnboundMethod(Object object, Node currentNode) {
        String badClassName = LogicalClassNode.getUncached().execute(object).fields.getName();
        return typeError(
                StringUtils.format("wrong argument type %s (expected Proc/Method/UnboundMethod)", badClassName),
                currentNode);
    }

    @TruffleBoundary
    public RubyException typeError(String message, Node currentNode, Throwable javaThrowable) {
        RubyClass exceptionClass = context.getCoreLibrary().typeErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations
                .createRubyException(context, exceptionClass, errorMessage, currentNode, javaThrowable);
    }

    @TruffleBoundary
    public RubyException typeErrorUnsupportedTypeException(UnsupportedTypeException exception, Node currentNode) {
        String message = exception.getMessage();
        if (message == null) {
            RubyArray rubyArray = createArray(context, language, exception.getSuppliedValues());
            String formattedValues = StringOperations
                    .getJavaString(DispatchNode.getUncached().call(rubyArray, "inspect"));
            message = "unsupported type " + formattedValues;
        }
        return typeError(message, currentNode);
    }

    // NameError

    @TruffleBoundary
    public RubyNameError nameErrorWrongConstantName(String name, Node currentNode) {
        return nameError(StringUtils.format("wrong constant name %s", name), null, name, currentNode);
    }

    @TruffleBoundary
    public RubyException nameErrorConstantNotDefined(RubyModule module, String name, Node currentNode) {
        return nameError(
                StringUtils.format("constant %s not defined", ModuleOperations.constantName(context, module, name)),
                null,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorUninitializedConstant(RubyModule module, String name, Node currentNode) {
        final String message = StringUtils.format(
                "uninitialized constant %s",
                ModuleOperations.constantNameNoLeadingColon(context, module, name));
        return nameError(message, module, name, currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorPrivateConstant(RubyModule module, String name, Node currentNode) {
        return nameError(
                StringUtils
                        .format("private constant %s referenced", ModuleOperations.constantName(context, module, name)),
                module,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorUninitializedClassVariable(RubyModule module, String name, Node currentNode) {
        return nameError(StringUtils.format(
                "uninitialized class variable %s in %s",
                name,
                module.fields.getName()), module, name, currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorInstanceNameNotAllowable(String name, Object receiver, Node currentNode) {
        return nameError(
                StringUtils.format("'%s' is not allowable as an instance variable name", name),
                receiver,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorInstanceVariableNotDefined(String name, Object receiver, Node currentNode) {
        return nameError(StringUtils.format("instance variable %s not defined", name), receiver, name, currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorUndefinedMethod(String name, RubyModule module, Node currentNode) {
        return nameError(
                StringUtils.format(
                        "undefined method '%s' for %s '%s'",
                        name,
                        module instanceof RubyClass ? "class" : "module",
                        module.fields.getName()),
                module,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorUndefinedSingletonMethod(String name, Object receiver, Node currentNode) {
        String className = LogicalClassNode.getUncached().execute(receiver).fields.getName();
        return nameError(
                StringUtils.format("undefined singleton method '%s' for %s", name, className),
                receiver,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorMethodNotDefinedIn(RubyModule module, String name, Node currentNode) {
        return nameError(
                StringUtils.format("method '%s' not defined in %s", name, module.fields.getName()),
                module,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorPrivateMethod(String name, RubyModule module, Node currentNode) {
        return nameError(
                StringUtils.format("method '%s' for %s is private", name, module.fields.getName()),
                module,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorLocalVariableNotDefined(String name, RubyBinding binding, Node currentNode) {
        return nameError(
                StringUtils.format("local variable '%s' not defined for %s", name, binding.toString()),
                binding,
                name,
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorClassVariableNotDefined(String name, RubyModule module, Node currentNode) {
        return nameError(StringUtils.format(
                "class variable '%s' not defined for %s",
                name,
                module.fields.getName()), module, name, currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorImportNotFound(String name, Node currentNode) {
        return nameError(StringUtils.format("import '%s' not found", name), null, name, currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameErrorUnknownIdentifierException(
            UnknownIdentifierException exception, Object receiver, Node currentNode) {
        return nameError(
                exception.getMessage(),
                receiver,
                exception.getUnknownIdentifier(),
                currentNode);
    }

    @TruffleBoundary
    public RubyNameError nameError(String message, Object receiver, String name, Node currentNode) {
        final RubyString messageString = StringOperations.createUTF8String(context, language, message);
        final RubyClass exceptionClass = context.getCoreLibrary().nameErrorClass;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);
        showExceptionIfDebug(exceptionClass, messageString, backtrace);
        return new RubyNameError(
                context.getCoreLibrary().nameErrorClass,
                language.nameErrorShape,
                messageString,
                backtrace,
                cause,
                receiver,
                language.getSymbol(name));
    }

    @TruffleBoundary
    public RubyNameError nameErrorFromMethodMissing(ExceptionFormatter formatter, Object receiver, String name,
            Node currentNode) {
        // omit = 1 to skip over the call to 'method_missing'. MRI does not show this in the backtrace.
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode, 1);
        final Object cause = FiberGetExceptionNode.getLastException(language);

        final RubyProc formatterProc = formatter.getProc(context);
        final String message = formatter.getMessage(formatterProc, name, receiver);

        final RubyNameError exception = new RubyNameError(
                context.getCoreLibrary().nameErrorClass,
                language.nameErrorShape,
                message,
                backtrace,
                cause,
                receiver,
                language.getSymbol(name));
        exception.formatter = formatterProc;
        showExceptionIfDebug(exception, backtrace);
        return exception;
    }

    // NoMethodError

    @TruffleBoundary
    public RubyNoMethodError noMethodErrorFromMethodMissing(ExceptionFormatter formatter, Object receiver, String name,
            Object[] args, Node currentNode) {
        final RubyArray argsArray = createArray(context, language, args);

        // omit = 1 to skip over the call to 'method_missing'. MRI does not show this is the backtrace.
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode, 1);
        final Object cause = FiberGetExceptionNode.getLastException(language);

        final RubyProc formatterProc = formatter.getProc(context);
        final String message = formatter.getMessage(formatterProc, name, receiver);

        final RubyNoMethodError exception = new RubyNoMethodError(
                context.getCoreLibrary().noMethodErrorClass,
                language.noMethodErrorShape,
                message,
                backtrace,
                cause,
                receiver,
                language.getSymbol(name),
                argsArray);
        exception.formatter = formatterProc;
        showExceptionIfDebug(exception, backtrace);
        return exception;
    }

    @TruffleBoundary
    public RubyNoMethodError noMethodError(String message, Object receiver, String name, Object[] args,
            Node currentNode) {
        final RubyString messageString = StringOperations.createUTF8String(context, language, message);
        final RubyArray argsArray = createArray(context, language, args);
        final RubyClass exceptionClass = context.getCoreLibrary().noMethodErrorClass;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);

        showExceptionIfDebug(exceptionClass, messageString, backtrace);

        return new RubyNoMethodError(
                context.getCoreLibrary().noMethodErrorClass,
                language.noMethodErrorShape,
                messageString,
                backtrace,
                cause,
                receiver,
                language.getSymbol(name),
                argsArray);
    }

    @TruffleBoundary
    public RubyNoMethodError noSuperMethodOutsideMethodError(Node currentNode) {
        final RubyString messageString = StringOperations.createUTF8String(context, language,
                "super called outside of method");
        final RubyClass exceptionClass = context.getCoreLibrary().nameErrorClass;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);
        showExceptionIfDebug(exceptionClass, messageString, backtrace);
        // TODO BJF Jul 21, 2016 Review to add receiver
        return new RubyNoMethodError(
                context.getCoreLibrary().noMethodErrorClass,
                language.noMethodErrorShape,
                messageString,
                backtrace,
                cause,
                null,
                // FIXME: the name of the method is not known in this case currently
                language.getSymbol("<unknown>"),
                Nil.INSTANCE);
    }

    @TruffleBoundary
    public RubyNoMethodError noMethodErrorUnknownIdentifier(Object receiver, String name, Object[] args,
            UnknownIdentifierException exception, Node currentNode) {
        return noMethodError(exception.getMessage(), receiver, name, args, currentNode);
    }

    // LoadError

    @TruffleBoundary
    public RubyException loadError(String message, String path, Node currentNode) {
        RubyString messageString = StringOperations.createUTF8String(context, language, message);
        RubyClass exceptionClass = context.getCoreLibrary().loadErrorClass;
        RubyException loadError = ExceptionOperations
                .createRubyException(context, exceptionClass, messageString, currentNode, null);

        if ("openssl.so".equals(path)) {
            // This is a workaround for the rubygems/security.rb file expecting the error path to be openssl
            path = "openssl";
        }

        DynamicObjectLibrary.getUncached().put(loadError, "@path",
                StringOperations.createUTF8String(context, language, path));

        return loadError;
    }

    @TruffleBoundary
    public RubyException loadError(String message, Node currentNode) {
        RubyString messageString = StringOperations.createUTF8String(context, language, message);
        RubyClass exceptionClass = context.getCoreLibrary().loadErrorClass;
        RubyException loadError = ExceptionOperations
                .createRubyException(context, exceptionClass, messageString, currentNode, null);

        return loadError;
    }

    @TruffleBoundary
    public RubyException loadErrorCannotLoad(String name, Node currentNode) {
        return loadError(StringUtils.format("cannot load such file -- %s", name), name, currentNode);
    }

    @TruffleBoundary
    public RubyException loadError(IOException exception, String path, Node currentNode) {
        return loadError(BacktraceFormatter.formatJavaThrowableMessage(exception), path, currentNode);
    }

    // ZeroDivisionError

    public RubyException zeroDivisionError(Node currentNode) {
        return zeroDivisionError(currentNode, null);
    }

    @TruffleBoundary
    public RubyException zeroDivisionError(Node currentNode, ArithmeticException exception) {
        RubyClass exceptionClass = context.getCoreLibrary().zeroDivisionErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, "divided by 0");

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, exception);
    }

    // SyntaxError

    @TruffleBoundary
    public RubySyntaxError syntaxErrorInvalidRetry(Node currentNode) {
        return syntaxError("Invalid retry", currentNode, currentNode.getEncapsulatingSourceSection());
    }

    @TruffleBoundary
    public RubySyntaxError syntaxError(String message, Node currentNode, SourceSection sourceLocation) {
        String messageWithFileLine;
        if (sourceLocation != null) {
            messageWithFileLine = language.fileLine(sourceLocation) + ": " + message;
        } else {
            messageWithFileLine = "(unknown):1: " + message;
        }
        return syntaxErrorAlreadyWithFileLine(messageWithFileLine, currentNode, sourceLocation);
    }

    @TruffleBoundary
    public RubySyntaxError syntaxErrorAlreadyWithFileLine(String message, Node currentNode,
            SourceSection sourceLocation) {
        final RubyString messageString = StringOperations.createUTF8String(context, language,
                message);
        RubyClass exceptionClass = context.getCoreLibrary().syntaxErrorClass;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);
        showExceptionIfDebug(exceptionClass, messageString, backtrace);
        return new RubySyntaxError(
                exceptionClass,
                language.syntaxErrorShape,
                messageString,
                backtrace,
                cause,
                sourceLocation);
    }

    // FloatDomainError

    @TruffleBoundary
    public RubyException floatDomainError(String value, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().floatDomainErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, value);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // IOError

    @TruffleBoundary
    public RubyException ioError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().ioErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException ioError(IOException exception, Node currentNode) {
        return ioError(BacktraceFormatter.formatJavaThrowableMessage(exception), currentNode);
    }

    // RangeError

    @TruffleBoundary
    public RubyException rangeError(long code, Node currentNode) {
        return rangeError(
                StringUtils.format("%d out of char range", code),
                currentNode);
    }

    @TruffleBoundary
    public RubyException charRangeError(int codepoint, Node currentNode) {
        return rangeError(StringUtils.format("%d out of char range", codepoint), currentNode);
    }

    @TruffleBoundary
    public RubyException rangeErrorConvertToInt(long value, Node currentNode) {
        final String direction;

        if (value < Integer.MIN_VALUE) {
            direction = "small";
        } else if (value > Integer.MAX_VALUE) {
            direction = "big";
        } else {
            throw CompilerDirectives.shouldNotReachHere("long fitting in int");
        }

        return rangeError(StringUtils.format("integer %d too %s to convert to 'int'", value, direction), currentNode);
    }

    @TruffleBoundary
    public RubyException rangeError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().rangeErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // Truffle::GraalError

    public RubyException graalErrorAssertConstantNotConstant(Node currentNode) {
        return graalError("value in Primitive.assert_compilation_constant was not constant", currentNode);
    }

    public RubyException graalErrorAssertNotCompiledCompiled(Node currentNode) {
        return graalError("call to Primitive.assert_not_compiled was compiled", currentNode);
    }

    @TruffleBoundary
    private RubyException graalError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().graalErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // RegexpError

    @TruffleBoundary
    public RubyException regexpError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().regexpErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // Encoding conversion errors.

    @TruffleBoundary
    public RubyException encodingError(Object string, RubyEncoding encoding, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().encodingErrorClass;
        String message = StringUtils.format("invalid symbol in encoding %s :%s", encoding, inspect(string));
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException encodingErrorTooManyEncodings(int maxSize, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().encodingErrorClass;
        String message = StringUtils.format("too many encoding (> %d)", maxSize);
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException encodingCompatibilityErrorIncompatible(RubyEncoding a, RubyEncoding b, Node currentNode) {
        return encodingCompatibilityError(
                StringUtils.format("incompatible character encodings: %s and %s", a, b),
                currentNode);
    }

    @TruffleBoundary
    public RubyException encodingCompatibilityErrorRegexpIncompatible(RubyEncoding a, RubyEncoding b,
            Node currentNode) {
        return encodingCompatibilityError(
                StringUtils.format("incompatible encoding regexp match (%s regexp with %s string)", a, b),
                currentNode);
    }


    @TruffleBoundary
    public RubyException encodingCompatibilityErrorIncompatibleWithOperation(RubyEncoding encoding, Node currentNode) {
        return encodingCompatibilityError(
                StringUtils.format("incompatible encoding with this operation: %s", encoding),
                currentNode);
    }

    @TruffleBoundary
    public RubyException encodingCompatibilityError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().encodingCompatibilityErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    @TruffleBoundary
    public RubyException encodingUndefinedConversionError(Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().encodingUndefinedConversionErrorClass;
        return ExceptionOperations.createRubyException(
                context,
                exceptionClass,
                coreStrings().REPLACEMENT_CHARACTER_SETUP_FAILED.createInstance(context),
                currentNode,
                null);
    }

    // FiberError

    @TruffleBoundary
    public RubyException fiberError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().fiberErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    public RubyException deadFiberCalledError(Node currentNode) {
        return fiberError("dead fiber called", currentNode);
    }

    public RubyException yieldFromRootFiberError(Node currentNode) {
        return fiberError("can't yield from root fiber", currentNode);
    }

    // ThreadError

    @TruffleBoundary
    public RubyException threadError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().threadErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    public RubyException threadErrorKilledThread(Node currentNode) {
        return threadError("killed thread", currentNode);
    }

    public RubyException threadErrorRecursiveLocking(Node currentNode) {
        return threadError("deadlock; recursive locking", currentNode);
    }

    public RubyException threadErrorUnlockNotLocked(Node currentNode) {
        return threadError("Attempt to unlock a mutex which is not locked", currentNode);
    }

    public RubyException threadErrorAlreadyLocked(Node currentNode) {
        return threadError("Attempt to unlock a mutex which is locked by another thread", currentNode);
    }

    public RubyException threadErrorQueueFull(Node currentNode) {
        return threadError("queue full", currentNode);
    }

    // SecurityError

    @TruffleBoundary
    public RubyException securityError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().securityErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // FFI::NullPointerError

    @TruffleBoundary
    public RubyException ffiNullPointerError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().truffleFFINullPointerErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);
        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // SystemExit

    @TruffleBoundary
    public RubySystemExit systemExit(int exitStatus, Node currentNode) {
        final RubyString message = StringOperations.createUTF8String(context, language, "exit");
        final RubyClass exceptionClass = context.getCoreLibrary().systemExitClass;
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);
        final Object cause = FiberGetExceptionNode.getLastException(language);
        showExceptionIfDebug(exceptionClass, message, backtrace);
        return new RubySystemExit(
                exceptionClass,
                language.systemExitShape,
                message,
                backtrace,
                cause,
                exitStatus);
    }

    // ClosedQueueError

    @TruffleBoundary
    public RubyException closedQueueError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().closedQueueErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    public RubyException closedQueueError(Node currentNode) {
        return closedQueueError("queue closed", currentNode);
    }

    // TruffleRuby specific

    @TruffleBoundary
    public RubyException unsupportedMessageError(String message, Node currentNode) {
        RubyClass exceptionClass = context.getCoreLibrary().unsupportedMessageErrorClass;
        RubyString errorMessage = StringOperations.createUTF8String(context, language, message);

        return ExceptionOperations.createRubyException(context, exceptionClass, errorMessage, currentNode, null);
    }

    // Helpers

    private CoreStrings coreStrings() {
        return language.coreStrings;
    }
}
