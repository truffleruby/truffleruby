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
package org.truffleruby.language.exceptions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.fiber.FiberManager.FiberShutdownException;
import org.truffleruby.core.kernel.AtExitManager;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.KillException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.shared.ProcessStatus;

public final class TopLevelRaiseHandler extends RubyBaseNode {

    // Must use ProcessStatus.exitCode() or ProcessStatus.signal() for every return
    @TruffleBoundary
    public int execute(Runnable body) {
        int exitCode = 0;
        AbstractTruffleException caughtException = null;

        // Execute the main script
        try {
            body.run();
        } catch (ExitException e) {
            // hard #exit!, return immediately, skip at_exit hooks
            return ProcessStatus.exitCode(e.getCode());
        } catch (AbstractTruffleException e) {
            // No KillException, it's a SystemExit instead for the main thread
            assert !(e instanceof KillException) : e;

            caughtException = e;
            exitCode = statusFromException(caughtException);
            // Set $! for at_exit
            getLanguage().getCurrentFiber().setLastException(ExceptionOperations
                    .getExceptionObject(caughtException));
            // printing the main script exception is delayed after at_exit hooks
        } catch (ThreadDeath e) { // Context#close(true)
            throw e;
        } catch (RuntimeException | Error e) {
            // No FiberShutdownException, it's only for Fibers
            assert !(e instanceof FiberShutdownException) : e;

            BacktraceFormatter.printInternalError(getContext(), e,
                    "an internal exception escaped out of the interpreter");
            return ProcessStatus.exitCode(1);
        }

        // Execute at_exit hooks (except if hard #exit!)
        String step = "unexpected internal exception in at_exit";
        try {
            AbstractTruffleException atExitException = getContext().getAtExitManager().runAtExitHooks();

            if (atExitException != null) {
                exitCode = statusFromException(atExitException);
            }

            step = "an internal exception escaped out of the interpreter";
            if (caughtException != null) {
                // print the main script exception now
                if (!AtExitManager.isSilentException(getContext(), caughtException)) {
                    getContext().getDefaultBacktraceFormatter().printTopLevelRubyExceptionOnEnvStderr(caughtException);
                }

                int signo = handleSignalException(caughtException);
                if (signo != 0) {
                    return ProcessStatus.signal(signo);
                }
            }
        } catch (ExitException e) {
            // hard #exit! during at_exit: ignore the main script exception
            exitCode = e.getCode();
        } catch (ThreadDeath e) { // Context#close(true)
            throw e;
        } catch (RuntimeException | Error e) { // Internal error
            BacktraceFormatter.printInternalError(getContext(), e, step);
            return ProcessStatus.exitCode(1);
        }

        return ProcessStatus.exitCode(exitCode);
    }

    private int statusFromException(AbstractTruffleException exception) {
        InteropLibrary interopLibrary = InteropLibrary.getUncached(exception);
        try {
            if (interopLibrary.getExceptionType(exception) == ExceptionType.EXIT) {
                return interopLibrary.getExceptionExitStatus(exception);
            } else {
                return 1;
            }
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    /** See rb_ec_cleanup() in CRuby, which calls ruby_default_signal(), which uses raise(3). We need to raise(3) after
     * Context#close for e.g. letting tools print their output, so just return the signal number here. */
    private int handleSignalException(AbstractTruffleException exception) {
        if (exception instanceof RaiseException) {
            RubyException rubyException = ((RaiseException) exception).getException();

            if (IsANode.getUncached().executeIsA(rubyException, coreLibrary().signalExceptionClass)) {
                return (int) DispatchNode.getUncached().call(rubyException, "signo");
            }
        }
        return 0;
    }

}
