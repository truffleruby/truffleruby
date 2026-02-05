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

import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.thread.RubyBacktraceLocation;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.objects.AllocationTracing;

@CoreModule(value = "Exception", isClass = true)
public abstract class ExceptionNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyException allocateException(RubyClass rubyClass) {
            final Shape shape = getLanguage().exceptionShape;
            final RubyException instance = new RubyException(rubyClass, shape, nil, null, nil);
            AllocationTracing.trace(instance, this);
            return instance;
        }
    }

    @CoreMethod(names = "initialize", optional = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyException initialize(RubyException exception, NotProvided message) {
            exception.message = nil;
            return exception;
        }

        @Specialization(guards = "wasProvided(message)")
        RubyException initialize(RubyException exception, Object message) {
            exception.message = message;
            return exception;
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "self == from")
        RubyException initializeCopySelfIsSameAsFrom(RubyException self, RubyException from) {
            return self;
        }

        @Specialization(
                guards = { "self != from", "!isNameError(from)", "!isSystemCallError(from)" })
        RubyException initializeCopy(RubyException self, RubyException from) {
            initializeExceptionCopy(self, from);
            return self;
        }

        @Specialization(guards = "self != from")
        RubyException initializeSystemCallErrorCopy(RubySystemCallError self, RubySystemCallError from) {
            initializeExceptionCopy(self, from);
            self.errno = from.errno;
            return self;
        }

        @Specialization(guards = "self != from")
        RubyException initializeCopyNoMethodError(RubyNoMethodError self, RubyNoMethodError from) {
            initializeExceptionCopy(self, from);
            initializeNameErrorCopy(self, from);
            self.args = from.args;
            return self;
        }

        @Specialization(
                guards = { "self != from", "!isNoMethodError(from)" })
        RubyException initializeCopyNameError(RubyNameError self, RubyNameError from) {
            initializeExceptionCopy(self, from);
            initializeNameErrorCopy(self, from);
            return self;
        }

        protected boolean isNameError(RubyException object) {
            return object instanceof RubyNameError;
        }

        protected boolean isNoMethodError(RubyException object) {
            return object instanceof RubyNoMethodError;
        }

        protected boolean isSystemCallError(RubyException object) {
            return object instanceof RubySystemCallError;
        }

        private void initializeNameErrorCopy(RubyNameError self, RubyNameError from) {
            self.name = from.name;
            self.receiver = from.receiver;
        }

        private void initializeExceptionCopy(RubyException self, RubyException from) {
            Backtrace backtrace = from.backtrace;
            if (backtrace != null) {
                self.backtrace = backtrace.copy(self);
            } else {
                self.backtrace = null;
            }
            self.formatter = from.formatter;
            self.message = from.message;
            self.cause = from.cause;
            self.backtraceStringArray = from.backtraceStringArray;
            self.backtraceLocations = from.backtraceLocations;
            self.customBacktrace = from.customBacktrace;
        }

    }

    @CoreMethod(names = "backtrace")
    public abstract static class BacktraceNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object backtrace(RubyException exception,
                @Cached InlinedConditionProfile hasCustomBacktraceProfile,
                @Cached InlinedConditionProfile hasBacktraceProfile) {
            final Object customBacktrace = exception.customBacktrace;

            if (hasCustomBacktraceProfile.profile(this, customBacktrace != null)) {
                return customBacktrace;
            } else if (hasBacktraceProfile.profile(this, exception.backtrace != null)) {
                RubyArray backtraceStringArray = exception.backtraceStringArray;
                if (backtraceStringArray == null) {
                    backtraceStringArray = getContext().getUserBacktraceFormatter().formatBacktraceAsRubyStringArray(
                            exception,
                            exception.backtrace);
                    exception.backtraceStringArray = backtraceStringArray;
                }
                return backtraceStringArray;
            } else {
                return nil;
            }
        }

    }

    @CoreMethod(names = "backtrace_locations")
    public abstract static class BacktraceLocationsNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object backtraceLocations(RubyException exception,
                @Cached InlinedConditionProfile hasBacktraceProfile,
                @Cached InlinedConditionProfile hasLocationsProfile) {
            if (hasBacktraceProfile.profile(this, exception.backtrace != null)) {
                Object backtraceLocations = exception.backtraceLocations;
                if (hasLocationsProfile.profile(this, backtraceLocations == null)) {
                    Backtrace backtrace = exception.backtrace;
                    backtraceLocations = backtrace
                            .getBacktraceLocations(getContext(), getLanguage(), GetBacktraceException.UNLIMITED, null);
                    exception.backtraceLocations = backtraceLocations;
                }
                return backtraceLocations;
            } else {
                return nil;
            }
        }
    }

    @Primitive(name = "exception_backtrace?")
    public abstract static class BacktraceQueryPrimitiveNode extends PrimitiveArrayArgumentsNode {

        protected static final String METHOD = "backtrace";

        /* We can cheaply determine if an Exception has a backtrace via object inspection. However, if
         * `Exception#backtrace` is redefined, then `Exception#backtrace?` needs to follow along to be consistent. So,
         * we check if the method has been redefined here and if so, fall back to the Ruby code for the method by
         * returning `FAILURE` in the fallback specialization. */
        @Specialization(
                guards = {
                        "lookupNode.lookupProtected(frame, exception, METHOD) == getContext().getCoreMethods().EXCEPTION_BACKTRACE", },
                limit = "1")
        boolean backtraceQuery(VirtualFrame frame, RubyException exception,
                @Cached LookupMethodOnSelfNode lookupNode) {
            return !(exception.customBacktrace == null && exception.backtrace == null);
        }

        @Specialization
        Object fallback(RubyException exception) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyException(exception)", limit = "getInteropCacheLimit()")
        boolean foreignException(Object exception,
                @CachedLibrary("exception") InteropLibrary interopLibrary) {
            return interopLibrary.hasExceptionStackTrace(exception);
        }
    }

    @Primitive(name = "exception_capture_backtrace", lowerFixnum = 1)
    public abstract static class CaptureBacktraceNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object captureBacktrace(RubyException exception, int offset) {
            exception.backtrace = getContext().getCallStack().getBacktrace(this, offset);
            return nil;
        }

    }

    @Primitive(name = "exception_message")
    public abstract static class MessagePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object message(RubyException exception) {
            final Object message = exception.message;
            if (message == null) {
                return nil;
            } else {
                return message;
            }
        }

    }

    @Primitive(name = "exception_set_message")
    public abstract static class MessageSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object setMessage(RubyException exception, Object message) {
            exception.message = message;
            return nil;
        }

    }

    @Primitive(name = "exception_set_custom_backtrace")
    public abstract static class SetCustomBacktrace extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object set(RubyException exception, Object customBacktrace) {
            exception.customBacktrace = customBacktrace;
            return customBacktrace;
        }

    }

    @ImportStatic(ArrayGuards.class)
    @Primitive(name = "exception_set_backtrace_locations")
    public abstract static class SetBacktraceLocations extends PrimitiveArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        Object set(RubyException exception, RubyArray backtraceLocations,
                @CachedLibrary(limit = "storageStrategyLimit()") ArrayStoreLibrary stores) {
            Object store = backtraceLocations.getStore();
            int size = backtraceLocations.size;
            assert size > 0;
            Object[] locations = stores.boxedCopyOfRange(store, 0, size);

            var newStackTrace = new TruffleStackTraceElement[size];
            for (int i = 0; i < size; i++) {
                var location = (RubyBacktraceLocation) locations[i];
                newStackTrace[i] = location.getTruffleStackTraceElement();
            }

            exception.backtrace = new Backtrace(null, 0, newStackTrace);
            exception.backtraceLocations = backtraceLocations;
            exception.customBacktrace = null;
            exception.backtraceStringArray = null;
            return nil;
        }

    }

    @Primitive(name = "exception_formatter")
    public abstract static class FormatterPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object formatter(RubyException exception) {
            final RubyProc formatter = exception.formatter;
            if (formatter == null) {
                return nil;
            } else {
                return formatter;
            }
        }

    }

    @CoreMethod(names = "cause")
    public abstract static class CauseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object cause(RubyException exception) {
            return exception.cause;
        }

    }

    @Primitive(name = "exception_set_cause")
    public abstract static class ExceptionSetCauseNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyException setCause(RubyException exception, Object cause) {
            exception.cause = cause;
            return exception;
        }

        @Specialization(guards = "!isRubyException(exception)")
        Object foreignExceptionNoCause(Object exception, Nil cause) {
            return exception;
        }

        @Specialization(guards = { "!isRubyException(exception)", "!isNil(cause)" })
        Object foreignExceptionWithCause(Object exception, Object cause) {
            RubyException exc = coreExceptions().runtimeError("Cannot set the cause of a foreign exception", this);
            exc.cause = cause;
            throw new RaiseException(getContext(), exc);
        }
    }

    @Primitive(name = "exception_errno_error", lowerFixnum = 2)
    public abstract static class ExceptionErrnoErrorPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Child ErrnoErrorNode errnoErrorNode = ErrnoErrorNode.create();

        @Specialization
        RubySystemCallError exceptionErrnoError(RubyClass errorClass, Object extraMessage, int errno, Object location) {
            return errnoErrorNode.execute(errorClass, errno, extraMessage, location, null);
        }

    }

    @Primitive(name = "java_breakpoint")
    @SuppressWarnings("unused")
    public abstract static class Breakpoint extends PrimitiveNode {

        @SuppressFBWarnings("DLS")
        @TruffleBoundary
        @Specialization
        boolean breakpoint() {
            // have a Ruby backtrace at hand
            String printableRubyBacktrace = BacktraceFormatter.printableRubyBacktrace(this);
            return true; // place to put a Java breakpoint
        }


    }

    @Primitive(name = "exception_backtrace_limit")
    public abstract static class BacktraceLimitNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        int limit() {
            return getContext().getOptions().BACKTRACE_LIMIT;
        }

    }

    @Primitive(name = "exception_get_raise_exception")
    public abstract static class GetRaiseExceptionNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object getRaiseException(RubyException exception) {
            RaiseException raiseException = exception.backtrace.getRaiseException();
            if (raiseException != null) {
                return raiseException;
            } else {
                return nil;
            }
        }

    }

    @Primitive(name = "exception_used_as_a_cause?")
    public abstract static class IsUsedAsACauseNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        boolean isUsedAsACause(RubyException exception) {
            return exception.usedAsACause;
        }

        @Fallback
        boolean isUsedAsACauseForeignException(Object exception) {
            return false;
        }

    }

    @Primitive(name = "exception_used_as_a_cause!")
    public abstract static class UsedAsACauseNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object usedAsACause(RubyException exception) {
            exception.usedAsACause = true;
            return nil;
        }

        @Fallback
        Object usedAsACauseForeignException(Object exception) {
            return nil;
        }

    }

}
