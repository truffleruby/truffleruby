/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.backtrace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.VMPrimitiveNodes.InitStackOverflowClassesEagerlyNode;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.methods.TranslateExceptionNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class BacktraceFormatter {

    public enum FormattingFlags {
        OMIT_EXCEPTION,
        OMIT_FROM_PREFIX,
        INTERLEAVE_JAVA
    }

    /** Flags for a backtrace exposed to Ruby via #caller, #caller_locations, Exception#backtrace and
     * Thread#backtrace. */
    public static final EnumSet<FormattingFlags> USER_BACKTRACE_FLAGS = EnumSet
            .of(FormattingFlags.OMIT_FROM_PREFIX, FormattingFlags.OMIT_EXCEPTION);

    private final RubyContext context;
    private final RubyLanguage language;
    private final EnumSet<FormattingFlags> flags;

    @TruffleBoundary
    public static BacktraceFormatter createDefaultFormatter(RubyContext context, RubyLanguage language) {
        final EnumSet<FormattingFlags> flags = EnumSet.noneOf(FormattingFlags.class);

        if (context.getOptions().BACKTRACES_INTERLEAVE_JAVA) {
            flags.add(FormattingFlags.INTERLEAVE_JAVA);
        }

        return new BacktraceFormatter(context, language, flags);
    }

    // For debugging:
    // org.truffleruby.language.backtrace.BacktraceFormatter.printableRubyBacktrace(this)
    public static String printableRubyBacktrace(Object maybeNode) {
        final Node node = maybeNode instanceof Node n && n.isAdoptable() ? (Node) maybeNode : null;
        final RubyContext context = RubyLanguage.getCurrentContext();
        final BacktraceFormatter backtraceFormatter = new BacktraceFormatter(
                context,
                RubyLanguage.getCurrentLanguage(),
                EnumSet.noneOf(FormattingFlags.class));
        final String backtrace = backtraceFormatter.formatBacktrace(null, context.getCallStack().getBacktrace(node));
        if (backtrace.isEmpty()) {
            return "<empty backtrace>";
        } else if (node == null) {
            return "# the first entry line is imprecise because 'this' is not an adopted Node, select caller Java frames in the debugger until 'this' is an adopted Node to fix this\n" +
                    backtrace;
        } else {
            return backtrace;
        }
    }

    /** For debug purposes. */
    public static boolean isApplicationCode(RubyLanguage language, SourceSection sourceSection) {
        return isUserSourceSection(language, sourceSection) &&
                !language.getSourcePath(sourceSection.getSource()).contains("/lib/stdlib/rubygems");
    }

    public BacktraceFormatter(RubyContext context, RubyLanguage language, EnumSet<FormattingFlags> flags) {
        this.context = context;
        this.language = language;
        this.flags = flags;
    }

    @TruffleBoundary
    public void printTopLevelRubyExceptionOnEnvStderr(AbstractTruffleException exception) {
        final RubyException rubyException = exception instanceof RaiseException
                ? ((RaiseException) exception).getException()
                : null;
        final Backtrace backtrace = rubyException != null ? rubyException.backtrace : null;
        if (backtrace != null && backtrace.getStackTrace().length == 0) {
            // An Exception with a non-null empty stacktrace, so an Exception from Truffle::Boot.main
            printRubyExceptionOnEnvStderr("truffleruby: ", exception);
        } else {
            printRubyExceptionOnEnvStderr("", exception);
        }
    }

    @SuppressFBWarnings("OS")
    @TruffleBoundary
    public void printRubyExceptionOnEnvStderr(String info, AbstractTruffleException exception) {
        final Object exceptionObject = ExceptionOperations.getExceptionObject(exception);

        if (InitStackOverflowClassesEagerlyNode.ignore(exceptionObject)) {
            return;
        }

        // Ensure the backtrace is materialized here, before calling Ruby methods.
        if (exception != null) {
            TruffleStackTrace.fillIn(exception);
        }

        final PrintStream printer = context.getEnvErrStream();
        if (!info.isEmpty()) {
            printer.print(info);
        }

        try {
            final Object fullMessage = DispatchNode.getUncached().call(
                    context.getCoreLibrary().truffleExceptionOperationsModule,
                    "get_formatted_backtrace",
                    exceptionObject);
            final String formatted = RubyStringLibrary.getTStringUncached(fullMessage).toJavaStringUncached();
            if (formatted.endsWith("\n")) {
                printer.print(formatted);
            } else {
                printer.println(formatted);
            }
        } catch (Throwable error) {
            printer.println("Error while formatting Ruby exception:");
            if (error instanceof RaiseException) {
                RubyException errorRubyException = ((RaiseException) error).getException();
                printer.println(formatBacktrace(errorRubyException, errorRubyException.backtrace));
            } else {
                error.printStackTrace(printer);
            }

            if (exceptionObject instanceof RubyException) {
                RubyException rubyException = (RubyException) exceptionObject;
                printer.println("Original Ruby exception:");
                printer.println(formatBacktrace(rubyException, rubyException.backtrace));
            } else {
                printer.println("Original foreign exception:");
                exception.printStackTrace(printer);
            }
            printer.println(); // To make it easier to spot the end of both error + original
        }
    }

    public void printBacktraceOnEnvStderr(Node currentNode) {
        printBacktraceOnEnvStderr("", currentNode);
    }

    @SuppressFBWarnings("OS")
    @TruffleBoundary
    public void printBacktraceOnEnvStderr(String info, Node currentNode) {
        final Backtrace backtrace = context.getCallStack().getBacktrace(currentNode);

        final PrintStream printer = context.getEnvErrStream();

        if (!info.isEmpty()) {
            printer.print(info);
        }

        printer.println(formatBacktrace(null, backtrace));
    }

    /** Format the backtrace as a String with \n between each line, but no trailing \n. */
    public String formatBacktrace(RubyException exception, Backtrace backtrace) {
        return formatBacktrace(exception, backtrace, Integer.MAX_VALUE);
    }

    /** Formats at most {@code length} elements of the backtrace (starting from the top of the call stack) as a String
     * with \n between each line, but no trailing \n. */
    @TruffleBoundary
    public String formatBacktrace(RubyException exception, Backtrace backtrace, int length) {
        return String.join("\n", formatBacktraceAsStringArray(exception, backtrace, length));
    }

    public RubyArray formatBacktraceAsRubyStringArray(RubyException exception, Backtrace backtrace) {
        return formatBacktraceAsRubyStringArray(exception, backtrace, Integer.MAX_VALUE);
    }

    @TruffleBoundary
    public RubyArray formatBacktraceAsRubyStringArray(RubyException exception, Backtrace backtrace, int length) {
        final String[] lines = formatBacktraceAsStringArray(exception, backtrace, length);

        final Object[] array = new Object[lines.length];

        for (int n = 0; n < lines.length; n++) {
            array[n] = StringOperations.createUTF8String(context, language, lines[n]);
        }

        return ArrayHelpers.createArray(context, language, array);
    }

    @TruffleBoundary
    private String[] formatBacktraceAsStringArray(RubyException exception, Backtrace backtrace, int length) {
        if (backtrace == null) {
            backtrace = context.getCallStack().getBacktrace(null);
        }

        final TruffleStackTraceElement[] stackTrace = backtrace.getStackTrace();
        length = Math.min(length, stackTrace.length);
        final ArrayList<String> lines = new ArrayList<>(length);

        if (length == 0 && !flags.contains(FormattingFlags.OMIT_EXCEPTION) && exception != null) {
            lines.add(formatException(exception));
            return lines.toArray(StringUtils.EMPTY_STRING_ARRAY);
        }

        for (int n = 0; n < length; n++) {
            lines.add(formatLine(stackTrace, n, exception));
        }

        if (backtrace.getJavaThrowable() != null && flags.contains(FormattingFlags.INTERLEAVE_JAVA)) {
            final List<String> interleaved = BacktraceInterleaver
                    .interleave(lines, backtrace.getJavaThrowable().getStackTrace(), backtrace.getOmitted());
            return interleaved.toArray(StringUtils.EMPTY_STRING_ARRAY);
        }

        return lines.toArray(StringUtils.EMPTY_STRING_ARRAY);
    }

    @TruffleBoundary
    public String formatLine(TruffleStackTraceElement[] stackTrace, int n, RubyException exception) {
        try {
            return formatLineInternal(stackTrace, n, exception);
        } catch (Exception e) {
            TranslateExceptionNode.logJavaException(context, null, e);

            final String firstFrame = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "";
            return StringUtils.format("(exception %s %s %s", e.getClass().getName(), e.getMessage(), firstFrame);
        }
    }

    private String formatLineInternal(TruffleStackTraceElement[] stackTrace, int n, RubyException exception) {
        final TruffleStackTraceElement element = stackTrace[n];

        final StringBuilder builder = new StringBuilder();

        if (!flags.contains(FormattingFlags.OMIT_FROM_PREFIX) && n > 0) {
            builder.append("\tfrom ");
        }

        var callNode = element.getLocation();
        var rootNode = element.getTarget().getRootNode();
        var sourceSection = callNode == null ? rootNode.getSourceSection() : callNode.getEncapsulatingSourceSection();
        var methodName = Backtrace.labelFor(element);

        if (rootNode instanceof RubyRootNode) { // A Ruby frame
            final SourceSection reportedSourceSection;

            // Unavailable SourceSections are always skipped, as there is no source position information.
            if (isAvailable(sourceSection)) {
                reportedSourceSection = sourceSection;
            } else {
                final SourceSection nextUserSourceSection = nextAvailableSourceSection(stackTrace, n);
                // if there is no next source section use a core one to avoid ???
                reportedSourceSection = nextUserSourceSection != null ? nextUserSourceSection : sourceSection;
            }

            if (reportedSourceSection == null) {
                builder.append("???");
            } else {
                builder.append(language.getSourcePath(reportedSourceSection.getSource()));
                builder.append(":");
                builder.append(language.getStartLineAdjusted(reportedSourceSection));
            }
        } else { // A foreign frame
            if (sourceSection == null) {
                builder.append("???");
            } else {
                builder.append(language.fileLine(sourceSection));
            }
        }

        builder.append(":in `");
        builder.append(methodName);
        builder.append("'");

        if (!flags.contains(FormattingFlags.OMIT_EXCEPTION) && exception != null && n == 0) {
            builder.append(": ");
            builder.append(formatException(exception));
        }

        return builder.toString();
    }

    /** Like {@link StackTraceElement#toString()} but without any classloader/module info. Not formatted in Ruby order
     * (file:line:in `method') to keep a Java feel to it and be closer to {@link Throwable#printStackTrace()}. Using an
     * explicit StringBuilder to avoid using too much stack space for this (matters for StackOverflowError) */
    public static String formatStackTraceElement(StackTraceElement element) {
        StringBuilder builder = new StringBuilder();
        builder.append(element.getClassName()).append(".").append(element.getMethodName());
        builder.append("(").append(element.getFileName()).append(":").append(element.getLineNumber()).append(")");
        return builder.toString();
    }

    private String formatException(RubyException exception) {
        final StringBuilder builder = new StringBuilder();

        final String message = ExceptionOperations.messageToString(exception);

        final String exceptionClass = exception.getLogicalClass().fields.getName();

        // Show the exception class at the end of the first line of the message
        final int firstLn = message.indexOf('\n');
        if (firstLn >= 0) {
            builder.append(message, 0, firstLn);
            builder.append(" (").append(exceptionClass).append(")");
            builder.append(message.substring(firstLn));
        } else {
            builder.append(message);
            builder.append(" (").append(exceptionClass).append(")");
        }
        return builder.toString();
    }

    /** This logic should be kept in sync with
     * {@link org.truffleruby.debug.TruffleDebugNodes.IterateFrameBindingsNode} */
    public static SourceSection nextAvailableSourceSection(TruffleStackTraceElement[] stackTrace, int n) {
        while (n < stackTrace.length) {
            final Node callNode = stackTrace[n].getLocation();

            if (callNode != null) {
                final SourceSection sourceSection = callNode.getEncapsulatingSourceSection();

                if (isAvailable(sourceSection)) {
                    return sourceSection;
                }
            }

            n++;
        }
        return null;
    }

    @TruffleBoundary
    public static boolean isAvailable(SourceSection sourceSection) {
        return sourceSection != null && sourceSection.isAvailable();
    }

    public static boolean isUserSourceSection(RubyLanguage language, SourceSection sourceSection) {
        return isAvailable(sourceSection) && !isRubyCore(language, sourceSection.getSource());
    }

    public static boolean isRubyCore(RubyLanguage language, Source source) {
        final String path = RubyLanguage.getPath(source);
        return path.startsWith(language.coreLoadPath);
    }

    public static String formatJavaThrowableMessage(Throwable t) {
        final String message = t.getMessage();
        return (message != null ? message : "<no message>") + " (" + t.getClass().getCanonicalName() + ")";
    }

    @TruffleBoundary
    public static void printInternalError(RubyContext context, Throwable throwable, String from) {
        final PrintStream stream = context.getEnvErrStream();
        final BacktraceFormatter formatter = context.getDefaultBacktraceFormatter();
        stream.println();
        stream.println("truffleruby: " + from + ",");
        stream.println("please report it to https://github.com/truffleruby/truffleruby/issues");
        stream.println();
        stream.println("```");

        boolean firstException = true;
        Throwable t = throwable;

        while (t != null) {
            if (t.getClass().getSimpleName().equals("LazyStackTrace")) {
                // Truffle's lazy stracktrace support, not a real exception
                break;
            }

            if (!firstException) {
                stream.println("Caused by:");
            }

            if (t instanceof RaiseException) {
                // A Ruby exception as a cause of a Java or C-ext exception
                final RubyException rubyException = ((RaiseException) t).getException();

                final String formattedBacktrace = formatter
                        .formatBacktrace(rubyException, rubyException.backtrace);
                stream.println(formattedBacktrace);
            } else {
                stream.println(formatJavaThrowableMessage(t));

                if (t instanceof AbstractTruffleException) {
                    // Foreign exception
                    stream.println(formatter.formatBacktrace(null, new Backtrace((AbstractTruffleException) t)));
                } else {
                    // Internal error, print it formatted like a Ruby exception
                    printJavaStackTrace(stream, t);

                    if (TruffleStackTrace.getStackTrace(t) != null) {
                        var formatted = formatter.formatBacktrace(null, new Backtrace(t));
                        if (!formatted.isEmpty()) {
                            stream.println(formatted);
                        }
                    }
                }
            }

            t = t.getCause();
            firstException = false;
        }

        stream.println("```");
    }

    public static void appendJavaStackTrace(Throwable t, StringBuilder builder) {
        final StackTraceElement[] stackTrace = t.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (BacktraceInterleaver.isCallBoundary(stackTraceElement)) {
                break;
            }
            builder.append("\tfrom ").append(formatStackTraceElement(stackTraceElement)).append('\n');
        }
    }

    private static void printJavaStackTrace(PrintStream stream, Throwable t) {
        final StringBuilder stringBuilder = new StringBuilder();
        appendJavaStackTrace(t, stringBuilder);
        stream.print(stringBuilder.toString());
    }

}
