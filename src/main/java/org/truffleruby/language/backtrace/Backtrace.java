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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.exception.GetBacktraceException;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.thread.RubyBacktraceLocation;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.truffleruby.language.objects.AllocationTracing;

/** Represents a backtrace: a list of activations (~ call sites).
 *
 * <p>
 * A backtrace is always constructed from a Java throwable, but does not always correspond to a Ruby exception (e.g.
 * {@code Kernel.caller_locations}). Whenever constructing a backtrace from a Ruby exception, it will be encapsulated in
 * a Java throwable ({@link RaiseException}).
 *
 * <p>
 * Whenever a backtrace is associated with a Ruby exception, there is a 1-1-1 match between the backtrace, the Ruby
 * exception (which has a backtrace field) and the {@link #getRaiseException() raiseException} stored in the backtrace
 * (which encapsulates the Ruby exception).
 *
 * <p>
 * NOTE(norswap): At least, that's how it should work, but there are cases where a Ruby exception's backtrace may not
 * link back to the exception (e.g. in {@code
 * TranslateExceptionNode#translateThrowable}).
 *
 * <p>
 * Note in passing that not all Ruby exceptions have an associated backtrace (simply creating an exception via
 * {@code Exception.new} does not fill its backtrace), nor does the associated backtrace necessarily match the result of
 * {@code Exception#backtrace} in Ruby, because the user may have set a custom backtrace (an array of strings).
 *
 * <p>
 * In general, there isn't any guarantee that the getters will return non-null values, excepted {@link #getStackTrace()}
 * and {@link #getBacktraceLocations(RubyContext, RubyLanguage, int, Node)}.
 *
 * <p>
 * NOTE(norswap): And this is somewhat unfortunate, as it's difficult to track the assumptions on the backtrace object
 * and generally require being very defensive depending on the information available - but fixing this would require a
 * dangerous refactoring for little benefit.
 *
 * <p>
 * Also note that the activations are recorded lazily when one of the aforementionned methods is called, excepted when
 * specified otherwise in the constructor. The activations will match the state of the Truffle call stack whenever the
 * activations are recorded (so, during the constructor call or first method call). */
public final class Backtrace {
    // region Fields
    // See accessors for info on most undocumented fields.

    private final Node location;
    private final int omitted;
    private RaiseException raiseException;
    private final Throwable javaThrowable;
    private TruffleStackTraceElement[] stackTrace;
    private int totalUnderlyingElements;

    public static final TruffleStackTraceElement[] EMPTY_STACK_TRACE_ELEMENTS_ARRAY = new TruffleStackTraceElement[0];

    // endregion
    // region Constructors

    /** Fully explicit constructor. */
    public Backtrace(Node location, int omitted, Throwable javaThrowable) {
        this.location = location;
        this.omitted = omitted;
        this.javaThrowable = javaThrowable;
    }

    /** For manually crafted backtraces. */
    public Backtrace(Node location, int omitted, TruffleStackTraceElement[] stackTraceElements) {
        this.location = location;
        this.omitted = omitted;
        this.javaThrowable = null;
        this.stackTrace = stackTraceElements;
    }

    /** Creates a backtrace for the given foreign exception, setting the {@link #getLocation() location} accordingly,
     * and computing the activations eagerly (since the exception itself is not retained). */
    public Backtrace(AbstractTruffleException exception) {
        assert !(exception instanceof RaiseException);
        this.location = exception.getLocation();
        this.omitted = 0;
        this.javaThrowable = null;
        this.stackTrace = getStackTrace(exception);
    }

    /** Creates a backtrace for the given throwable, in which only the activations and the backtrace locations may be
     * retrieved. The activations are computed eagerly, since the exception itself is not retained. */
    public Backtrace(Throwable exception) {
        this.location = null;
        this.omitted = 0;
        this.javaThrowable = null;
        this.stackTrace = getStackTrace(exception);
    }

    // endregion
    // region Accessors

    /** AST node that caused the associated exception, if the info is available, or null. */
    public Node getLocation() {
        return location;
    }

    /** Returns the wrapper for the Ruby exception associated with this backtrace, if any, and null otherwise. */
    public RaiseException getRaiseException() {
        return raiseException;
    }

    /** Sets the wrapper for the Ruby exception associated with this backtrace. */
    public void setRaiseException(RaiseException raiseException) {
        assert this.raiseException == null : "the RaiseException of a Backtrace must not be set again, otherwise the original backtrace is lost";
        this.raiseException = raiseException;
    }

    /** Returns the number of activations to omit from the top (= most recently called) of the activation stack. */
    public int getOmitted() {
        return omitted;
    }

    /** Returns the Java exception the associated Ruby exception was translated from, if any. (This is not the same as
     * {@link #getRaiseException() the raise exception} which is simply a wrapper around the Ruby exception.) */
    public Throwable getJavaThrowable() {
        return javaThrowable;
    }

    /** How many stack trace elements would there be if omitted was 0? Forces the computation of the stack trace. */
    public int getTotalUnderlyingElements() {
        getStackTrace();
        return totalUnderlyingElements;
    }

    // endregion
    // region Static Methods

    @TruffleBoundary
    public static String labelFor(TruffleStackTraceElement e) {
        RootNode root = e.getTarget().getRootNode();
        String label = root.getName();
        return label == null ? "<unknown>" : label;
    }

    @TruffleBoundary
    public static String baseLabelFor(TruffleStackTraceElement e) {
        RootNode root = e.getTarget().getRootNode();
        String baseLabel = root instanceof RubyRootNode
                // Ruby backtraces do not include the class name for MRI compatibility.
                ? ((RubyRootNode) root).getSharedMethodInfo().getMethodName()
                : root.getName();
        return baseLabel == null ? "<unknown>" : baseLabel;
    }

    // endregion
    // region Instance Methods

    /** Used to copy the backtrace when copying {@code exception}. */
    @TruffleBoundary
    public Backtrace copy(RubyException exception) {
        Backtrace copy = new Backtrace(location, omitted, javaThrowable);
        // A Backtrace is 1-1-1 with a RaiseException and a Ruby exception.
        // Copy the RaiseException
        RaiseException newRaiseException = new RaiseException(this.raiseException, exception);
        // Another way would be to copy the activations (copy.activations = getActivations()), but
        // then the TruffleStrackTrace would be inconsistent.
        copy.setRaiseException(newRaiseException);
        return copy;
    }

    @TruffleBoundary
    private TruffleStackTraceElement[] getStackTrace(Throwable truffleException) {
        if (this.stackTrace != null) {
            return this.stackTrace;
        }

        if (truffleException == null) {
            truffleException = new GetBacktraceException(location, GetBacktraceException.UNLIMITED);
        }

        // The stacktrace is computed here if it was not already computed and stored in the
        // TruffleException with TruffleStackTraceElement.fillIn().
        final List<TruffleStackTraceElement> fullStackTrace = TruffleStackTrace.getStackTrace(truffleException);
        assert fullStackTrace != null;

        final List<TruffleStackTraceElement> stackTraceList = new ArrayList<>();
        final RubyContext context = RubyLanguage.getCurrentContext();
        final CallStackManager callStackManager = context.getCallStack();

        int processedCount = 0;
        int retainedCount = 0;
        for (TruffleStackTraceElement stackTraceElement : fullStackTrace) {
            assert processedCount != 0 || stackTraceElement.getLocation() == location;
            ++processedCount;

            Node callNode = stackTraceElement.getLocation();
            var callTarget = stackTraceElement.getTarget();
            if (callStackManager.ignoreFrame(callNode, callTarget)) {
                continue;
            }

            if (retainedCount < omitted) {
                ++retainedCount;
                continue;
            }

            // TODO (eregon, 4 Feb 2019): we should not ignore foreign frames without a
            //  call node, but print info based on the methodName and CallTarget.
            final RootNode rootNode = callTarget.getRootNode();
            if (rootNode instanceof RubyRootNode || callNode != null) {
                stackTraceList.add(stackTraceElement);
            }

            ++retainedCount;
        }

        // If there are activations with a InternalMethod but no caller information above in the
        // stack, then all of these activations are internal as they are not called from user code.
        while (!stackTraceList.isEmpty() && stackTraceList.get(stackTraceList.size() - 1).getLocation() == null) {
            stackTraceList.remove(stackTraceList.size() - 1);
            --retainedCount;
        }

        this.totalUnderlyingElements = retainedCount;
        return this.stackTrace = stackTraceList.toArray(EMPTY_STACK_TRACE_ELEMENTS_ARRAY);
    }

    public TruffleStackTraceElement[] getStackTrace() {
        return getStackTrace(this.raiseException);
    }

    /** Returns a ruby array of {@code Thread::Backtrace::Locations} with maximum length {@code length}, and omitting
     * locations as requested ({@link #getOmitted()}). If more locations are omitted than are available, return a Ruby
     * {@code nil}.
     *
     * <p>
     * The length can be negative, in which case it is treated as a range ending. Use -1 to get the maximum length.
     *
     * <p>
     * This causes the activations to be computed if not yet the case.
     * 
     * @param length the maximum number of locations to return (if positive), or -1 minus the number of items to exclude
     *            at the end. You can use {@link GetBacktraceException#UNLIMITED} to signal that you want all locations.
     * @param node the node at which we're requiring the backtrace. Can be null if the backtrace is associated with a */
    @TruffleBoundary
    public Object getBacktraceLocations(RubyContext context, RubyLanguage language, int length, Node node) {
        final int stackTraceLength;
        if (this.raiseException != null) {
            // When dealing with the backtrace of a Ruby exception, we use the wrapping
            // exception and we don't set a limit on the retrieved activations.
            stackTraceLength = getStackTrace().length;
        } else {
            // We can't set an effective limit when dealing with negative range endings.
            final int stackTraceElementsLimit = length < 0
                    ? GetBacktraceException.UNLIMITED
                    : omitted + length;
            final Throwable e = new GetBacktraceException(node, stackTraceElementsLimit);
            stackTraceLength = getStackTrace(e).length;
        }

        // Omitting more locations than available should return nil.
        if (stackTraceLength == 0) {
            return omitted > totalUnderlyingElements
                    ? Nil.INSTANCE
                    : ArrayHelpers.createEmptyArray(context, language);
        }

        final int locationsLength = length < 0
                ? stackTraceLength + 1 + length
                // We use Math.min because length > activationsLength is possible and
                // activationsLength > length is too whenever there is a #raiseException set.
                : Math.min(stackTraceLength, length);

        final Object[] locations = new Object[locationsLength];
        for (int i = 0; i < locationsLength; i++) {
            final RubyBacktraceLocation instance = new RubyBacktraceLocation(
                    context.getCoreLibrary().threadBacktraceLocationClass,
                    language.threadBacktraceLocationShape,
                    this,
                    i);
            AllocationTracing.trace(instance, node);
            locations[i] = instance;
        }
        return ArrayHelpers.createArray(context, language, locations);
    }

    // endregion
}
