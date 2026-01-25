/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.kernel;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.exception.RubySystemExit;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class AtExitManager {

    private final RubyContext context;
    private final RubyLanguage language;

    private final Deque<RubyProc> atExitHooks = new ConcurrentLinkedDeque<>();
    private final Deque<RubyProc> systemExitHooks = new ConcurrentLinkedDeque<>();
    private final Deque<RubyProc> nativeExitHooks = new ConcurrentLinkedDeque<>();

    public AtExitManager(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
    }

    public void addRegularAtExitHook(RubyProc block) {
        atExitHooks.push(block);
    }

    public void addSystemExitHook(RubyProc block) {
        systemExitHooks.push(block);
    }

    public void addNativeExitHook(RubyProc block) {
        nativeExitHooks.push(block);
    }

    public AbstractTruffleException runAtExitHooks() {
        return runExitHooks(atExitHooks);
    }

    public void runSystemExitHooks() {
        try {
            runExitHooks(systemExitHooks);
        } catch (ExitException | ThreadDeath e) {
            throw e;
        } catch (RuntimeException | Error e) {
            BacktraceFormatter.printInternalError(context, e, "unexpected internal exception in system at_exit");
        }
    }

    public void runNativeExitHooks() {
        try {
            runExitHooks(nativeExitHooks);
        } catch (ExitException | ThreadDeath e) {
            throw e;
        } catch (RuntimeException | Error e) {
            BacktraceFormatter.printInternalError(context, e, "unexpected internal exception in native at_exit");
        }
    }

    @TruffleBoundary
    private AbstractTruffleException runExitHooks(Deque<RubyProc> stack) {
        AbstractTruffleException lastException = null;

        while (true) {
            RubyProc block = stack.poll();
            if (block == null) {
                return lastException;
            }

            try {
                ProcOperations.rootCall(block, NoKeywordArgumentsDescriptor.INSTANCE, RubyBaseNode.EMPTY_ARGUMENTS);
            } catch (AbstractTruffleException e) {
                handleAtExitException(context, language, e);
                lastException = e;
            }
        }
    }

    public List<RubyProc> getHandlers() {
        final List<RubyProc> handlers = new ArrayList<>();
        handlers.addAll(atExitHooks);
        handlers.addAll(systemExitHooks);
        return handlers;
    }

    public static boolean isSilentException(RubyContext context, AbstractTruffleException exception) {
        if (!(exception instanceof RaiseException)) {
            return false;
        }

        final RubyException rubyException = ((RaiseException) exception).getException();
        // The checks are kind_of?(SystemExit) || instance_of?(SignalException),
        // see error_handle() in eval_error.c in CRuby. So Interrupt is not silent.
        return rubyException instanceof RubySystemExit ||
                rubyException.getLogicalClass() == context.getCoreLibrary().signalExceptionClass;
    }

    private static void handleAtExitException(RubyContext context, RubyLanguage language,
            AbstractTruffleException exception) {
        // Set $! for the next at_exit handlers
        language.getCurrentFiber().setLastException(ExceptionOperations
                .getExceptionObject(exception));

        if (!isSilentException(context, exception)) {
            context.getDefaultBacktraceFormatter().printRubyExceptionOnEnvStderr("", exception);
        }
    }

}
