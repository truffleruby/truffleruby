/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.core.VMPrimitiveNodes.InitStackOverflowClassesEagerlyNode;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.control.TerminationException;
import org.truffleruby.signal.LibRubySignal;

@GenerateUncached
@GenerateInline(inlineByDefault = true)
public abstract class TranslateExceptionNode extends RubyBaseNode {

    @NeverDefault
    public static TranslateExceptionNode create() {
        return TranslateExceptionNodeGen.create();
    }

    public abstract RuntimeException execute(Node node, Throwable throwable);

    public final RuntimeException executeCached(Throwable throwable) {
        return execute(this, throwable);
    }

    public static void logJavaException(RubyContext context, Node currentNode, Throwable exception) {
        if (context.getOptions().EXCEPTIONS_PRINT_JAVA) {
            printStackTrace(exception);

            if (context.getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                context.getDefaultBacktraceFormatter().printBacktraceOnEnvStderr(currentNode);
            }
        }
    }

    public static void logUncaughtJavaException(RubyContext context, Node currentNode, Throwable exception) {
        if (context.getOptions().EXCEPTIONS_PRINT_JAVA || context.getOptions().EXCEPTIONS_PRINT_UNCAUGHT_JAVA) {
            printStackTrace(exception);

            if (context.getOptions().EXCEPTIONS_PRINT_RUBY_FOR_JAVA) {
                context.getDefaultBacktraceFormatter().printBacktraceOnEnvStderr(currentNode);
            }
        }
    }

    @Specialization
    static RuntimeException translate(ControlFlowException e) {
        throw e;
    }

    @Specialization
    static RuntimeException translate(AbstractTruffleException e) {
        throw e;
    }

    @Specialization
    static RuntimeException translate(TerminationException e) {
        throw e;
    }

    @Specialization
    static RuntimeException translate(ThreadDeath e) {
        throw e;
    }

    @Specialization(guards = "needsSpecialTranslation(e)")
    static RuntimeException translateSpecial(Node node, Throwable e) {
        throw doTranslateSpecial(node, e);
    }

    @Fallback
    static RuntimeException translate(Node node, Throwable e) {
        // An internal exception
        CompilerDirectives.transferToInterpreterAndInvalidate();
        logUncaughtJavaException(getContext(node), node, e);
        throw ExceptionOperations.rethrow(e);
    }

    protected boolean needsSpecialTranslation(Throwable e) {
        return e instanceof TruffleString.IllegalByteArrayLengthException ||
                e instanceof UnsupportedSpecializationException ||
                e instanceof StackOverflowError ||
                e instanceof OutOfMemoryError;
    }

    @TruffleBoundary
    private static RaiseException doTranslateSpecial(Node node, Throwable e) {
        if (e instanceof TruffleString.IllegalByteArrayLengthException) {
            return new RaiseException(getContext(node), coreExceptions(node).argumentError(e.getMessage(), node));
        } else if (e instanceof UnsupportedSpecializationException unsupported) {
            return new RaiseException(getContext(node),
                    translateUnsupportedSpecialization(unsupported.getNode(), getContext(node), unsupported));
        } else if (e instanceof StackOverflowError stackOverflowError) {
            return new RaiseException(getContext(node),
                    translateStackOverflow(node, getContext(node), stackOverflowError));
        } else {
            return new RaiseException(getContext(node),
                    translateOutOfMemory(node, getContext(node), (OutOfMemoryError) e));
        }
    }

    @TruffleBoundary
    private static RubyException translateStackOverflow(Node node, RubyContext context, StackOverflowError error) {
        boolean ignore = InitStackOverflowClassesEagerlyNode.ignore(error);
        if (!ignore) {
            if (context.getOptions().EXCEPTIONS_WARN_STACKOVERFLOW) {
                // We cannot afford to use more Java frames
                LibRubySignal.warnStackOverflowError();
            }

            logJavaException(context, node, error);
        }

        return context.getCoreExceptions().systemStackErrorStackLevelTooDeep(node, error, !ignore);
    }

    @TruffleBoundary
    private static RubyException translateOutOfMemory(Node node, RubyContext context, OutOfMemoryError error) {
        if (context.getOptions().EXCEPTIONS_WARN_OUT_OF_MEMORY) {
            // We cannot afford to use more Java frames
            LibRubySignal.warnOutOfMemoryError();
        }

        logJavaException(context, node, error);
        return context.getCoreExceptions().noMemoryError(node, error);
    }

    @TruffleBoundary
    private static RubyException translateUnsupportedSpecialization(Node node, RubyContext context,
            UnsupportedSpecializationException exception) {

        logJavaException(context, node, exception);

        final StringBuilder builder = new StringBuilder();
        builder.append("TruffleRuby doesn't have a case for the ");
        builder.append(exception.getNode().getClass().getName());
        builder.append(" node with values of type");
        argumentsToString(builder, exception.getSuppliedValues());
        builder.append('\n');
        BacktraceFormatter.appendJavaStackTrace(exception, builder);
        String message = builder.toString().strip();
        return context.getCoreExceptions().typeError(message, node, exception);
    }

    public static StringBuilder argumentsToString(StringBuilder builder, Object[] arguments) {
        for (Object value : arguments) {
            builder.append(" ");

            if (value == null) {
                builder.append("null");
            } else if (value instanceof RubyDynamicObject dynamicObject) {

                builder.append(dynamicObject.getLogicalClass().fields.getName());
                builder.append("(");
                builder.append(value.getClass().getName());
                builder.append(")");

                if (value instanceof RubyArray array) {
                    builder.append("[");

                    if (array.getStore() == null) {
                        builder.append("null");
                    } else {
                        builder.append(array.getStore().getClass().getName());
                    }

                    builder.append(",");
                    builder.append(array.size);
                    builder.append("]");
                } else if (RubyGuards.isRubyHash(value)) {
                    final Object store = ((RubyHash) value).store;
                    builder.append("[");
                    builder.append(store.getClass().getName());
                    builder.append("]");
                }
            } else {
                builder.append(value.getClass().getName());
            }

            if (value instanceof Number || value instanceof Boolean) {
                builder.append("=");
                builder.append(value.toString());
            }
        }
        return builder;
    }

    @TruffleBoundary
    private static void printStackTrace(Throwable exception) {
        exception.printStackTrace();
    }

}
