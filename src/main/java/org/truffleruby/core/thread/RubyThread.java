/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import java.lang.invoke.VarHandle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.InterruptMode;
import org.truffleruby.core.exception.RubyException;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.core.fiber.RubyFiber.FiberStatus;
import org.truffleruby.core.hash.HashOperations;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.support.PRNGRandomizerNodes;
import org.truffleruby.core.support.RubyPRNGRandomizer;
import org.truffleruby.core.tracepoint.TracePointState;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;

import com.oracle.truffle.api.object.Shape;

import static org.truffleruby.language.RubyBaseNode.nil;

public final class RubyThread extends RubyDynamicObject implements ObjectGraphNode {

    // Fields initialized here are initialized just after the super() call, and before the rest of the constructor
    public Object processStatus = nil; // thread-local variable $?
    public InterruptMode interruptMode = InterruptMode.IMMEDIATE; // only accessed by this Ruby Thread and its Fibers
    public volatile ThreadStatus status = ThreadStatus.RUN;
    public final List<ReentrantLock> ownedLocks = new ArrayList<>();
    private final RubyFiber rootFiber;
    private RubyFiber currentFiber;
    public final Set<RubyFiber> runningFibers = newFiberSet();
    CountDownLatch finishedLatch = new CountDownLatch(1);
    final RubyHash threadLocalVariables;
    final RubyHash recursiveObjects;
    final RubyHash recursiveObjectsSingle;
    final RubyPRNGRandomizer randomizer;
    public final TracePointState tracePointState = new TracePointState();
    boolean reportOnException;
    boolean abortOnException;
    public volatile Thread thread = null;
    /** Either nil or long */
    public volatile Object nativeThreadId = nil;
    volatile RubyException exception = null;
    volatile Object value = nil;
    public final AtomicBoolean wakeUp = new AtomicBoolean(false);
    volatile int priority = Thread.NORM_PRIORITY;
    ThreadLocalBuffer ioBuffer;
    Object threadGroup;
    public String sourceLocation;
    Object name = nil;

    // Decimal formats are not thread safe, so we'll create them on the thread as we need them.

    public DecimalFormat noExpFormat;
    public DecimalFormat smallExpFormat;
    public DecimalFormat largeExpFormat;

    public DecimalFormat formatFFloat;
    public DecimalFormat formatEFloat;
    public DecimalFormat formatGFloatSimple;
    public DecimalFormat formatGFloatExponential;

    public RubyThread(
            RubyClass rubyClass,
            Shape shape,
            RubyContext context,
            RubyLanguage language,
            boolean reportOnException,
            boolean abortOnException,
            Object threadGroup,
            String sourceLocation) {
        super(rubyClass, shape);
        this.threadLocalVariables = HashOperations.newEmptyHash(context, language);
        this.recursiveObjects = HashOperations.newEmptyHash(context, language);
        this.recursiveObjectsSingle = HashOperations.newEmptyHash(context, language);
        this.recursiveObjectsSingle.compareByIdentity = true;
        // This random instance is only for this thread and thus does not need to be thread-safe
        this.randomizer = PRNGRandomizerNodes.newRandomizer(context, language, false);
        this.reportOnException = reportOnException;
        this.abortOnException = abortOnException;
        this.threadGroup = threadGroup;
        this.sourceLocation = sourceLocation;

        // Initialized last as it captures `this`
        VarHandle.storeStoreFence();
        this.rootFiber = new RubyFiber(
                context.getCoreLibrary().fiberClass,
                language.fiberShape,
                context,
                language,
                this,
                FiberStatus.RESUMED,
                "root");
        this.currentFiber = rootFiber;
    }

    public RubyFiber getRootFiber() {
        return rootFiber;
    }

    public RubyFiber getCurrentFiber() {
        assert RubyLanguage.getCurrentLanguage()
                .getCurrentThread() == this : "Trying to read the current Fiber of another Thread which is inherently racy";
        return currentFiber;
    }

    // If the currentFiber is read from another Ruby Thread,
    // there is no guarantee that fiber will remain the current one
    // as it could switch to another Fiber before the actual operation on the returned fiber.
    public RubyFiber getCurrentFiberRacy() {
        return currentFiber;
    }

    public void setCurrentFiber(RubyFiber fiber) {
        currentFiber = fiber;
    }

    public ThreadLocalBuffer getIoBuffer(RubyContext context) {
        Pointer.checkNativeAccess(context);
        return ioBuffer;
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.addProperty(reachable, threadLocalVariables);
        ObjectGraph.addProperty(reachable, name);
        // share fibers of a thread as its fiberLocals might be accessed by other threads with Thread#[]
        reachable.add(rootFiber);
        reachable.addAll(runningFibers);
    }

    @Override
    public String toString() {
        return super.toString() + " " + sourceLocation;
    }

    @TruffleBoundary
    private static Set<RubyFiber> newFiberSet() {
        return ConcurrentHashMap.newKeySet();
    }
}
