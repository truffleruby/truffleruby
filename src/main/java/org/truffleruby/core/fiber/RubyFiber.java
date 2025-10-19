/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.fiber;

import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.collections.LightweightLayoutLock;
import org.truffleruby.core.MarkingService;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.RubyBasicObject;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.control.KillException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.objects.shared.SharedObjects;

import static org.truffleruby.language.RubyBaseNode.nil;

public final class RubyFiber extends RubyDynamicObject implements ObjectGraphNode {

    // @formatter:off
    /*
     * Fiber status:
     *    [Fiber.new] -----------------------> FIBER_CREATED
     *                                         | [Fiber#{resume,transfer}]
     *                                         v
     *                                    +--> FIBER_RESUMED -----------------+
     *    [Fiber#{resume,transfer,raise}] |    | [Fiber.yield or #transfer]   |
     *                                    |    v                              |
     *                                    +--- FIBER_SUSPENDED                | [Terminate]
     *                                                                        |
     *                                         FIBER_TERMINATED <-------------+
     */
    // @formatter:on

    public enum FiberStatus {
        CREATED("created"),

        /** ran at least once */
        RESUMED("resumed"),

        SUSPENDED("suspended"),
        TERMINATED("terminated");

        public final String label;

        FiberStatus(String label) {
            this.label = label;
        }

    }

    private Object lastException = nil; // fiber-local variable $!
    public Object errorInfo = nil; // MRI: rb_errinfo
    public final RubyBasicObject fiberLocals;
    public final RubyArray catchTags;
    public final CountDownLatch initializedLatch = new CountDownLatch(1);
    final BlockingQueue<FiberManager.FiberMessage> messageQueue = newMessageQueue();
    public final RubyThread rubyThread;
    // @formatter:off
    /*
     *               |------Fiber 1-------|                                 |------Fiber 2-------|
     *               | resumingFiber      |---Fiber 1 resumed Fiber 2------>| resumingFiber      | ----> null
     *    null <-----| lastResumedByFiber |<--Fiber 2 resumed by Fiber 1----| lastResumedByFiber |
     *               |--------------------|                                 |--------------------|
     */
    // @formatter:on
    volatile RubyFiber lastResumedByFiber = null;
    /** the most recently-resumed Fiber by this Fiber */
    volatile RubyFiber resumingFiber = null;
    volatile boolean yielding = false;
    volatile FiberStatus status;
    public Thread thread = null;
    public volatile Throwable uncaughtException = null;
    String sourceLocation;
    RubyProc body;
    Node initializeNode;
    public final MarkingService.ExtensionCallStack extensionCallStack;
    public final ValueWrapperManager.HandleBlockHolder handleData;
    boolean blocking = true;
    public RubyArray cGlobalVariablesDuringInitFunction;
    /** Always false when not inside a C ext Init_ function */
    public boolean threadSafeExtension = false;

    // Last-used cache per thread for the threadState for LightweightLayoutLock's
    private final WeakHashMap<LightweightLayoutLock, AtomicInteger> layoutLockStates = new WeakHashMap<>();
    private LightweightLayoutLock lastLayoutLock = null;
    private AtomicInteger lastThreadState = null;

    // To pass state between beforeEnter(), fiberMain() and afterLeave()
    FiberManager.FiberMessage firstMessage;
    RubyFiber returnFiber;
    FiberManager.FiberMessage lastMessage;

    public RubyFiber(
            RubyClass rubyClass,
            Shape shape,
            RubyContext context,
            RubyLanguage language,
            RubyThread rubyThread,
            FiberStatus status,
            String sourceLocation) {
        super(rubyClass, shape);
        assert rubyThread != null;
        CompilerAsserts.partialEvaluationConstant(language);
        this.fiberLocals = new RubyBasicObject(
                context.getCoreLibrary().objectClass,
                language.basicObjectShape);
        this.catchTags = ArrayHelpers.createEmptyArray(context, language);
        this.rubyThread = rubyThread;
        this.status = status;
        this.sourceLocation = sourceLocation;
        extensionCallStack = new MarkingService.ExtensionCallStack(null, Nil.INSTANCE);
        handleData = new ValueWrapperManager.HandleBlockHolder();
    }

    public void initialize(RubyLanguage language, RubyContext context, boolean blocking, RubyProc block,
            Node currentNode) {
        final SourceSection sourceSection = block.getSharedMethodInfo().getSourceSection();
        this.sourceLocation = language.fileLine(sourceSection);
        this.body = block;
        this.initializeNode = currentNode;
        this.blocking = blocking;

        // share RubyFiber as its fiberLocals might be accessed by other threads with Thread#[]
        SharedObjects.propagate(language, this.rubyThread, this);
    }

    public boolean isRootFiber() {
        return rubyThread.getRootFiber() == this;
    }

    public boolean isActive() {
        return this == rubyThread.getCurrentFiber();
    }

    public boolean isTerminated() {
        return status == FiberStatus.TERMINATED;
    }

    public void restart() {
        status = FiberStatus.CREATED;
    }

    @TruffleBoundary
    private static LinkedBlockingQueue<FiberManager.FiberMessage> newMessageQueue() {
        return new LinkedBlockingQueue<>();
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        reachable.add(fiberLocals);
        reachable.add(rubyThread);
    }

    public Object getLastException() {
        return lastException;
    }

    public void setLastException(Object exception) {
        assert !(exception instanceof KillException) : "$? should never be a KillException: " + exception;
        assert !(exception instanceof RaiseException) : "$? should never be a RaiseException: " + exception;
        assert exception == nil || exception instanceof RubyException ||
                exception instanceof AbstractTruffleException : "Unexpected exception object for $!: " + exception;
        this.lastException = exception;
    }

    public AtomicInteger getLayoutLockThreadState(LightweightLayoutLock lock, InlinedConditionProfile fastPathProfile,
            Node node) {
        if (fastPathProfile.profile(node, lock == lastLayoutLock)) {
            return lastThreadState;
        } else {
            return getLayoutLockThreadStateSlowPath(lock);
        }
    }

    @TruffleBoundary
    private AtomicInteger getLayoutLockThreadStateSlowPath(LightweightLayoutLock lock) {
        AtomicInteger threadState = layoutLockStates.get(lock);
        if (threadState == null) {
            threadState = lock.registerThread();
            layoutLockStates.put(lock, threadState);
        }
        lastLayoutLock = lock;
        lastThreadState = threadState;
        return threadState;
    }

}
