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
package org.truffleruby.core.thread;

import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.InterruptibleFunction;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.collections.ConcurrentWeakSet;
import org.truffleruby.core.InterruptMode;
import org.truffleruby.core.basicobject.BasicObjectNodes.ObjectIDNode;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.exception.RubySystemExit;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.support.PRNGRandomizerNodes;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.interop.InteropNodes;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.SafepointAction;
import org.truffleruby.language.SafepointManager;
import org.truffleruby.language.SafepointPredicate;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.KillException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.objects.shared.SharedObjects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.signal.LibRubySignal;

public final class ThreadManager {

    public static final String NAME_PREFIX = "Ruby Thread";

    private final RubyContext context;
    private final RubyLanguage language;

    private final RubyThread rootThread;
    @CompilationFinal private Thread rootJavaThread;

    private final Set<RubyThread> runningRubyThreads = ConcurrentHashMap.newKeySet();

    /** The set of Java threads TruffleRuby created, and is responsible to exit in {@link #killAndWaitOtherThreads()}.
     * Needs to be weak because {@link RubyLanguage#disposeThread} is called late as Fibers use a "host thread"
     * (disposeThread is only called on Context#close for those). If a Thread is unreachable we do not need to wait for
     * it in {@link #killAndWaitOtherThreads()}, but otherwise we need to and we can never remove from this Set as
     * otherwise we cannot guarantee we wait until the Thread truly finishes execution. */
    private final ConcurrentWeakSet<Thread> rubyManagedThreads = new ConcurrentWeakSet<>();

    private boolean nativeInterrupt;
    private Timer nativeInterruptTimer;
    private ThreadLocal<Interrupter> nativeCallInterrupter;

    public ThreadManager(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
        this.rootThread = createBootThread("main");
    }

    public void initialize() {
        nativeInterrupt = context.getOptions().NATIVE_INTERRUPT && context.getOptions().NATIVE_PLATFORM;
        if (nativeInterrupt) {
            LibRubySignal.setupSIGVTALRMEmptySignalHandler();

            nativeInterruptTimer = new Timer("Ruby-NativeCallInterrupt-Timer", true);
            nativeCallInterrupter = ThreadLocal.withInitial(
                    () -> new NativeCallInterrupter(nativeInterruptTimer, LibRubySignal.threadID()));
        }
    }

    public void dispose() {
        if (nativeInterrupt) {
            nativeInterruptTimer.cancel();
            nativeInterruptTimer = null;
        }
    }

    public void initializeMainThread(Thread mainJavaThread) {
        rootJavaThread = mainJavaThread;
        start(rootThread, rootJavaThread);
    }

    public void resetMainThread() {
        cleanup(rootThread, rootJavaThread);
        rootJavaThread = null;
    }

    public void restartMainThread(Thread mainJavaThread) {
        initializeMainThread(mainJavaThread);

        rootThread.status = ThreadStatus.RUN;
        rootThread.finishedLatch = new CountDownLatch(1);

        final RubyFiber rootFiber = rootThread.getRootFiber();
        rootFiber.restart();

        PRNGRandomizerNodes.resetSeed(context, rootThread.randomizer);
    }

    public Thread createFiberJavaThread(RubyFiber fiber, SourceSection sourceSection, Runnable beforeEnter,
            Runnable body, Runnable afterLeave, Node node) {
        if (context.isPreInitializing()) {
            throw CompilerDirectives
                    .shouldNotReachHere("fibers should not be created while pre-initializing the context");
        }

        final Thread thread = context.getEnv().newTruffleThreadBuilder(body).beforeEnter(beforeEnter)
                .afterLeave(afterLeave).virtual(context.getOptions().VIRTUAL_THREAD_FIBERS).build();

        language.rubyThreadInitMap.put(thread, fiber.rubyThread);
        language.rubyFiberInitMap.put(thread, fiber);
        thread.setName(FiberManager.NAME_PREFIX + " id=" + thread.threadId() + " from " +
                language.fileLine(sourceSection));
        thread.setDaemon(true); // GR-33255
        rubyManagedThreads.add(thread); // need to be set before initializeThread()
        thread.setUncaughtExceptionHandler(uncaughtExceptionHandler(fiber));
        return thread;
    }

    private Thread createJavaThread(Runnable runnable, RubyThread rubyThread, String info, Node node) {
        if (context.getOptions().SINGLE_THREADED) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().securityError("threads not allowed in single-threaded mode", node));
        }

        if (context.isPreInitializing()) {
            throw CompilerDirectives
                    .shouldNotReachHere("threads should not be created while pre-initializing the context");
        }

        final Thread thread = context.getEnv().newTruffleThreadBuilder(runnable).build();

        language.rubyThreadInitMap.put(thread, rubyThread);
        language.rubyFiberInitMap.put(thread, rubyThread.getRootFiber());
        thread.setName(NAME_PREFIX + " id=" + thread.threadId() + " from " + info);
        rubyManagedThreads.add(thread); // need to be set before initializeThread()
        thread.setUncaughtExceptionHandler(uncaughtExceptionHandler(rubyThread.getRootFiber()));
        return thread;
    }

    private static Thread.UncaughtExceptionHandler uncaughtExceptionHandler(RubyFiber fiber) {
        assert fiber != null;
        return (javaThread, throwable) -> {
            if (throwable instanceof KillException) {
                // The exception killed the thread, as expected, do not print anything.
                // We cannot just catch (KillException e) in threadMain() because it can also happen
                // on the poll() done just after the thread enters the context and
                // *before* running the Runnable, i.e. before running threadMain().
                return;
            }

            printInternalError(throwable);
            try {
                fiber.uncaughtException = throwable;

                // If an uncaught exception happens, we already left the context, so it is safe to let other Fibers run

                // the Fiber is not yet initialized, unblock the caller and rethrow the exception to it
                fiber.initializedLatch.countDown();
            } catch (Throwable t) { // exception inside this UncaughtExceptionHandler
                t.initCause(throwable);
                printInternalError(t);
            }
        };
    }

    /** Whether the thread was created by TruffleRuby. */
    @TruffleBoundary
    public boolean isRubyManagedThread(Thread thread) {
        return rubyManagedThreads.contains(thread);
    }

    @TruffleBoundary
    public RubyThread createBootThread(String info) {
        return createThread(
                context.getCoreLibrary().threadClass,
                language.threadShape,
                language,
                Nil.INSTANCE,
                info);
    }

    public RubyThread createThread(RubyClass rubyClass, Shape shape, RubyLanguage language) {
        final Object currentGroup = language.getCurrentThread().threadGroup;
        assert currentGroup != null;
        return createThread(rubyClass, shape, language, currentGroup, "<uninitialized>");
    }

    @TruffleBoundary
    public RubyThread createForeignThread() {
        final Object currentGroup = rootThread.threadGroup;
        assert currentGroup != null;
        return createThread(
                context.getCoreLibrary().threadClass,
                language.threadShape,
                language,
                currentGroup,
                "<foreign thread>");
    }

    private RubyThread createThread(RubyClass rubyClass, Shape shape, RubyLanguage language, Object currentGroup,
            String info) {
        return new RubyThread(
                rubyClass,
                shape,
                context,
                language,
                getGlobalReportOnException(),
                getGlobalAbortOnException(),
                currentGroup,
                info);
    }

    private boolean getGlobalReportOnException() {
        final RubyClass threadClass = context.getCoreLibrary().threadClass;
        return (boolean) DynamicObjectLibrary.getUncached().getOrDefault(threadClass, "@report_on_exception", null);
    }

    private boolean getGlobalAbortOnException() {
        final RubyClass threadClass = context.getCoreLibrary().threadClass;
        return (boolean) DynamicObjectLibrary.getUncached().getOrDefault(threadClass, "@abort_on_exception", null);
    }

    public void initialize(RubyThread rubyThread, Node currentNode, String info, String sharingReason,
            Supplier<Object> task) {
        startSharing(rubyThread, sharingReason);

        rubyThread.sourceLocation = info;
        final RubyFiber rootFiber = rubyThread.getRootFiber();

        final Thread thread = createJavaThread(() -> threadMain(rubyThread, currentNode, task), rubyThread, info,
                currentNode);
        rubyThread.thread = thread;

        thread.start();

        // Must not leave the context here, to perform safepoint actions, if e.g. the new thread Thread#raise this one
        FiberManager.waitForInitializationEntered(context, rootFiber, currentNode);
    }

    /** {@link RubyLanguage#initializeThread(RubyContext, Thread)} runs before this, and
     * {@link RubyLanguage#disposeThread(RubyContext, Thread)} runs after this. Note this is NOT guaranteed to run, an
     * exception might happen in the poll() done when the thread enters the context. */
    private void threadMain(RubyThread thread, Node currentNode, Supplier<Object> task) {
        try {
            final Object result = task.get();
            setThreadValue(thread, result);
            // Handlers in the same order as in FiberManager
        } catch (KillException e) { // handled in uncaughtExceptionHandler()
            throw e;
        } catch (ThreadDeath e) { // Context#close(true), handled by Truffle
            throw e;
        } catch (RaiseException e) {
            setException(thread, e.getException(), currentNode);
        } catch (DynamicReturnException e) {
            setException(thread, context.getCoreExceptions().unexpectedReturn(currentNode), currentNode);
        } catch (ExitException e) {
            rethrowOnMainThread(currentNode, e);
        } catch (Throwable e) {
            final RuntimeException runtimeException = printInternalError(e);
            rethrowOnMainThread(currentNode, runtimeException);
        } finally {
            assert thread.value != null || thread.exception != null;
            cleanupKillOtherFibers(thread);
        }
    }

    public static RuntimeException printInternalError(Throwable e) {
        final String message = StringUtils
                .format("%s terminated with internal error:", Thread.currentThread().getName());
        final RuntimeException runtimeException = new RuntimeException(message, e);
        // Immediately print internal exceptions, in case they would cause a deadlock
        runtimeException.printStackTrace();
        return runtimeException;
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    private void rethrowOnMainThread(Node currentNode, RuntimeException e) {
        context.getSafepointManager().pauseRubyThreadAndExecute(
                currentNode,
                new SafepointAction("rethrow " + e.getClass() + " to main thread", getRootThread(), true, false) {
                    @Override
                    public void run(RubyThread rubyThread, Node currentNode) {
                        throw e;
                    }
                });
    }

    private void setThreadValue(RubyThread thread, Object value) {
        // A Thread is always shared (Thread.list)
        assert value != null;
        SharedObjects.propagate(language, thread, value);
        thread.value = value;
    }

    private void setException(RubyThread thread, RubyException exception, Node currentNode) {
        // We materialize the backtrace eagerly here, as the exception escapes the thread and needs
        // to capture the backtrace from this thread.
        RaiseException truffleException = exception.backtrace == null ? null : exception.backtrace.getRaiseException();
        if (truffleException != null) {
            TruffleStackTrace.fillIn(truffleException);
        }

        // A Thread is always shared (Thread.list)
        SharedObjects.propagate(language, thread, exception);
        thread.exception = exception;

        final RubyThread mainThread = context.getThreadManager().getRootThread();
        if (thread != mainThread) {
            final boolean isSystemExit = exception instanceof RubySystemExit;

            if (!isSystemExit && thread.reportOnException) {
                final TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                boolean sideEffects = safepoint.setAllowSideEffects(false);
                try {
                    DispatchNode.getUncached().call(context.getCoreLibrary().truffleThreadOperationsModule,
                            "report_exception", thread, exception);
                } finally {
                    safepoint.setAllowSideEffects(sideEffects);
                }
            }

            if (isSystemExit || thread.abortOnException) {
                ThreadNodes.ThreadRaisePrimitiveNode
                        .raiseInThread(language, context, mainThread, exception, currentNode);
            }
        }
    }

    // Share the Ruby Thread before it can be accessed concurrently, and before it is added to Thread.list
    public void startSharing(RubyThread rubyThread, String reason) {
        if (language.options.SHARED_OBJECTS_ENABLED) {
            // TODO (eregon, 22 Sept 2017): no need if singleThreaded in isThreadAccessAllowed()
            context.getSharedObjects().startSharing(language, reason);
            SharedObjects.writeBarrier(language, rubyThread);
        }
    }

    public void startForeignThread(RubyThread rubyThread, Thread javaThread) {
        startSharing(rubyThread, "creating a foreign thread");
        start(rubyThread, javaThread);
    }

    public void start(RubyThread thread, Thread javaThread) {
        final var isSameThread = javaThread == Thread.currentThread();
        if (isSameThread) {
            thread.nativeThreadId = LibRubySignal.getNativeThreadID();
        }

        thread.thread = javaThread;
        thread.ioBuffer = context.getOptions().NATIVE_PLATFORM ? Pointer.getNullBuffer(context) : null;
        registerThread(thread);

        final RubyFiber rootFiber = thread.getRootFiber();
        context.fiberManager.start(rootFiber, javaThread);
        // fully initialized
        rootFiber.initializedLatch.countDown();
    }

    public void cleanup(RubyThread thread, Thread javaThread) {
        cleanupKillOtherFibers(thread);
        cleanupThreadState(thread, javaThread);
    }

    /** We cannot call this from {@link RubyLanguage#disposeThread} because that's called under a context lock. */
    private void cleanupKillOtherFibers(RubyThread thread) {
        thread.nativeThreadId = Nil.INSTANCE;
        thread.status = ThreadStatus.DEAD;
        context.fiberManager.killOtherFibers(thread);
    }

    public void cleanupThreadState(RubyThread thread, Thread javaThread) {
        context.fiberManager.cleanup(thread.getRootFiber(), javaThread);

        if (thread.ioBuffer != null) {
            thread.ioBuffer.freeAll(thread);
        }

        unregisterThread(thread);
        thread.thread = null;
        thread.getRootFiber().thread = null;

        if (Thread.currentThread() == javaThread) {
            for (ReentrantLock lock : thread.ownedLocks) {
                while (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } else {
            if (!thread.ownedLocks.isEmpty()) {
                RubyLanguage.LOGGER.warning(
                        "could not release locks of " + javaThread + " as its cleanup happened on another Java Thread");
            }
        }

        thread.finishedLatch.countDown();
    }

    public Thread getRootJavaThread() {
        return rootJavaThread;
    }

    @TruffleBoundary
    public synchronized Thread getOrInitializeRootJavaThread() {
        // rootJavaThread can be null with a pre-initialized context.
        // In such a case, the first Thread in is considered the root Thread.
        if (rootJavaThread == null) {
            rootJavaThread = Thread.currentThread();
        }
        return rootJavaThread;
    }

    public RubyThread getRootThread() {
        return rootThread;
    }

    public interface BlockingAction<T> {
        boolean SUCCESS = true;

        T block() throws InterruptedException;
    }

    @TruffleBoundary
    public <T, R> R runUntilResultKeepStatus(Node currentNode, InterruptibleFunction<T, R> action, T object) {
        assert context.getEnv().getContext().isEntered() : "Use retryWhileInterrupted() when not entered";
        return TruffleSafepoint.setBlockedThreadInterruptibleFunction(currentNode, action, object);
    }

    public static Object executeBlockingCall(RubyThread thread, Interrupter interrupter, Object executable,
            Object[] args, BlockingCallInterruptible blockingCallInterruptible, Node currentNode) {
        final TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();

        final BlockingCallInterruptible.State state = new BlockingCallInterruptible.State(thread, executable, args);
        return safepoint.setBlockedFunction(currentNode, interrupter, blockingCallInterruptible, state, null, null);
    }

    public static class BlockingCallInterruptible
            implements TruffleSafepoint.CompiledInterruptibleFunction<BlockingCallInterruptible.State, Object> {

        final InteropLibrary receivers;
        final TranslateInteropExceptionNode translateInteropExceptionNode;
        final Node node;

        public BlockingCallInterruptible(
                Node node,
                InteropLibrary receivers,
                TranslateInteropExceptionNode translateInteropExceptionNode) {
            this.node = node;
            this.receivers = receivers;
            this.translateInteropExceptionNode = translateInteropExceptionNode;
        }

        @ValueType
        private static final class State {
            final RubyThread thread;
            final Object executable;
            final Object[] args;

            private State(RubyThread thread, Object executable, Object[] args) {
                this.thread = thread;
                this.executable = executable;
                this.args = args;
            }
        }

        @Override
        public Object apply(State state) {
            CompilerAsserts.partialEvaluationConstant(this);
            final RubyThread thread = state.thread;

            final ThreadStatus status = thread.status;
            thread.status = ThreadStatus.SLEEP;
            try {
                // NOTE: NFI uses CallTargets, so the TruffleSafepoint.poll() will happen before coming back from this call
                CompilerAsserts.partialEvaluationConstant(receivers);
                CompilerAsserts.partialEvaluationConstant(translateInteropExceptionNode);
                return InteropNodes.execute(node, state.executable, state.args, receivers,
                        translateInteropExceptionNode);
            } finally {
                thread.status = status;
            }
        }
    }

    /** Runs {@code action} until it returns a value (IOW, until it does not throw {@link InterruptedException}). The
     * given action should throw an {@link InterruptedException} when {@link Thread#interrupt()} is called. Otherwise,
     * the {@link SafepointManager} will not be able to interrupt this action. See
     * {@link ThreadNodes.ThreadRunBlockingSystemCallNode} for blocking native calls. If the action throws an
     * {@link InterruptedException}, it will be retried.
     *
     * @param action must not touch any Ruby state
     * @return the return value from {@code action} */
    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> action) {
        return runUntilResult(currentNode, action, null, null);
    }

    @TruffleBoundary
    public <T> T runUntilResult(Node currentNode, BlockingAction<T> action, Runnable beforeInterrupt,
            Consumer<Throwable> afterInterrupt) {
        final TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        final RubyThread runningThread = RubyLanguage.get(currentNode).getCurrentThread();

        // For Thread.handle_interrupt(Exception => :on_blocking),
        // we want to allow side-effecting actions to interrupt this blocking action and run here.
        final boolean onBlocking = runningThread.interruptMode == InterruptMode.ON_BLOCKING;

        final ThreadStatus status = runningThread.status;
        boolean sideEffects = false;

        if (onBlocking) {
            sideEffects = safepoint.setAllowSideEffects(true);
        }
        try {
            return safepoint.setBlockedFunction(currentNode, Interrupter.THREAD_INTERRUPT, arg -> {
                runningThread.status = ThreadStatus.SLEEP;
                try {
                    return action.block();
                } finally {
                    runningThread.status = status; // restore status for running the safepoint
                }
            }, null, beforeInterrupt, afterInterrupt);
        } finally {
            if (onBlocking) {
                safepoint.setAllowSideEffects(sideEffects);
            }
        }
    }

    /** Same as {@link TruffleContext#leaveAndEnter} but ensures it never returns null. Also the interruptible must
     * never return null. */
    public <T, R> R leaveAndEnter(Node node, Interrupter interrupter, InterruptibleFunction<T, R> interruptible,
            T object) {
        final TruffleContext truffleContext = context.getEnv().getContext();
        R result = truffleContext.leaveAndEnter(node, interrupter, interruptible, object);
        return Objects.requireNonNull(result);
    }

    @TruffleBoundary
    Interrupter getNativeCallInterrupter() {
        if (nativeInterrupt) {
            return nativeCallInterrupter.get();
        } else {
            return Interrupter.THREAD_INTERRUPT;
        }
    }

    public void registerThread(RubyThread thread) {
        if (!runningRubyThreads.add(thread)) {
            throw new UnsupportedOperationException(thread + " was already registered");
        }
    }

    public void unregisterThread(RubyThread thread) {
        if (!runningRubyThreads.remove(thread)) {
            throw new UnsupportedOperationException(thread + " was not registered");
        }
    }


    public void checkNoRunningThreads() {
        if (!runningRubyThreads.isEmpty()) {
            RubyLanguage.LOGGER
                    .warning("threads are still registered with thread manager at shutdown:\n" + getThreadDebugInfo());
        }
    }

    @TruffleBoundary
    public void killAndWaitOtherThreads() {
        // Kill all Ruby Threads and Fibers
        if (runningRubyThreads.size() > 1) {
            doKillOtherThreads();
        }
        context.fiberManager.killOtherFibers(language.getCurrentThread());

        // Wait and join all Java threads we created
        for (Thread thread : rubyManagedThreads) {
            if (thread != Thread.currentThread()) {
                runUntilResultKeepStatus(null, t -> {
                    t.join();
                    return BlockingAction.SUCCESS;
                }, thread);
            }
        }
    }

    /** Kill all Ruby threads, except the current Ruby Thread. Each Ruby Threads kills its fibers. */
    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    @TruffleBoundary
    private void doKillOtherThreads() {
        final Thread initiatingJavaThread = Thread.currentThread();
        SafepointPredicate predicate = (context, thread, action) -> Thread.currentThread() != initiatingJavaThread &&
                language.getCurrentFiber() == thread.getCurrentFiber();

        context.getSafepointManager().pauseAllThreadsAndExecute(null,
                new SafepointAction("kill other threads for shutdown", predicate, true, true) {
                    @Override
                    public void run(RubyThread rubyThread, Node currentNode) {
                        rubyThread.status = ThreadStatus.ABORTING;
                        throw new KillException(currentNode);
                    }
                });
    }

    @TruffleBoundary
    public Object[] getThreadList() {
        // This must not pre-allocate an array, or it could contain null's.
        return runningRubyThreads.toArray();
    }

    @TruffleBoundary
    public Iterable<RubyThread> iterateThreads() {
        return runningRubyThreads;
    }

    public String getThreadDebugInfo() {
        final StringBuilder builder = new StringBuilder();

        for (RubyThread thread : runningRubyThreads) {
            builder.append("thread @");
            builder.append(ObjectIDNode.getUncached().execute(thread));

            if (thread == rootThread) {
                builder.append(" (root)");
            }

            // cannot use getCurrentThread() as it might have been cleared
            if (thread == language.getCurrentThread()) {
                builder.append(" (current)");
            }

            builder.append("\n");

            builder.append(context.fiberManager.getFiberDebugInfo(thread));
        }

        if (builder.length() == 0) {
            return "no ruby threads\n";
        } else {
            return builder.toString();
        }
    }

}
