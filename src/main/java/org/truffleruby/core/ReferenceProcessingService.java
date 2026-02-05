/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.truffleruby.language.dispatch.DispatchNode;

public abstract class ReferenceProcessingService<R extends ReferenceProcessingService.PhantomProcessingReference<R, T>, T> {

    public abstract static class PhantomProcessingReference<R extends PhantomProcessingReference<R, T>, T>
            extends
            PhantomReference<T> {

        /** Doubly linked list of references to keep to allow the reference service to traverse them and to keep the
         * references alive for processing. */
        private R next;
        private R previous;
        private ReferenceProcessingService<R, T> service;

        public PhantomProcessingReference(
                T object,
                ReferenceQueue<? super Object> queue,
                ReferenceProcessingService<R, T> service) {
            super(object, queue);
            this.service = service;
        }

        public R getPrevious() {
            return previous;
        }

        public void setPrevious(R previous) {
            this.previous = previous;
        }

        public R getNext() {
            return next;
        }

        public void setNext(R next) {
            this.next = next;
        }

        @SuppressWarnings("unchecked")
        public void remove() {
            this.next = (R) this;
            this.previous = (R) this;
        }

        public ReferenceProcessingService<R, T> service() {
            return service;
        }
    }

    public static final class ReferenceProcessor {
        final ReferenceQueue<Object> processingQueue = new ReferenceQueue<>();

        private RubyThread processingThread;
        private final RubyContext context;

        public ReferenceProcessor(RubyContext context) {
            this.context = context;
        }

        @TruffleBoundary
        void processReferenceQueue(ReferenceProcessingService<?, ?> service) {
            if (processOnMainThread()) {
                drainReferenceQueues();
            } else {
                /* We can't create a new thread while the context is initializing or finalizing, as the polyglot API
                 * locks on creating new threads, and some core loading does things such as stat files which could
                 * allocate memory that is marked to be automatically freed and so would want to start the finalization
                 * thread. So don't start the finalization thread if we are initializing. We will rely on some other
                 * finalizer to be created to ever free this memory allocated during startup, but that's a reasonable
                 * assumption and a low risk of leaking a tiny number of bytes if it doesn't hold. */
                if (processingThread == null && !context.isPreInitializing() && context.isInitialized() &&
                        !context.isFinalizing()) {
                    createProcessingThread(service);
                }
            }
        }

        public boolean processOnMainThread() {
            return context.getOptions().SINGLE_THREADED;
        }

        private static final String THREAD_NAME = "Ruby-reference-processor";

        @TruffleBoundary
        private void createProcessingThread(ReferenceProcessingService<?, ?> service) {
            final ThreadManager threadManager = context.getThreadManager();
            final RubyLanguage language = context.getLanguageSlow();
            RubyThread newThread;
            synchronized (this) {
                if (processingThread != null) {
                    return;
                }
                newThread = threadManager.createBootThread(THREAD_NAME);
                processingThread = newThread;
            }
            final String sharingReason = "creating " + THREAD_NAME + " thread for " +
                    service.getClass().getSimpleName();

            threadManager.initialize(newThread, null, THREAD_NAME, sharingReason, () -> {
                DispatchNode.getUncached().call(context.getCoreLibrary().truffleThreadOperationsModule,
                        "mark_as_detached_thread", newThread);

                while (true) {
                    final PhantomProcessingReference<?, ?> reference = threadManager.runUntilResult(null,
                            () -> (PhantomProcessingReference<?, ?>) processingQueue.remove());
                    reference.service().processReference(context, language, reference);
                }
            });
        }

        @TruffleBoundary
        void drainReferenceQueues() {
            final RubyLanguage language = context.getLanguageSlow();
            while (true) {
                @SuppressWarnings("unchecked")
                PhantomProcessingReference<?, ?> reference = (PhantomProcessingReference<?, ?>) processingQueue.poll();

                if (reference == null) {
                    break;
                }

                reference.service().processReference(context, language, reference);
            }
        }

    }

    /** The head of a doubly-linked list of FinalizerReference, needed to collect finalizer Procs for ObjectSpace. */
    private R first = null;

    protected final ReferenceQueue<Object> processingQueue;

    public ReferenceProcessingService(ReferenceQueue<Object> processingQueue) {
        this.processingQueue = processingQueue;
    }

    @SuppressWarnings("unchecked")
    protected void processReference(RubyContext context, RubyLanguage language,
            PhantomProcessingReference<?, ?> reference) {
        remove((R) reference);
    }

    public interface ReferenceRunner<T> {
        public void accept(RubyContext context, RubyLanguage language, T t);
    }

    protected void runCatchingErrors(RubyContext context, RubyLanguage language,
            ReferenceRunner<R> action, R reference) {
        try {
            action.accept(context, language, reference);
        } catch (RaiseException e) {
            context.getCoreExceptions().showExceptionIfDebug(e.getException());
        }
    }

    protected synchronized void remove(R ref) {
        if (ref.getNext() == ref) {
            // Already removed.
            return;
        }

        if (first == ref) {
            if (ref.getNext() != null) {
                first = ref.getNext();
            } else {
                // The list becomes empty
                first = null;
            }
        }

        R next = ref.getNext();
        R prev = ref.getPrevious();

        if (next != null) {
            next.setPrevious(prev);
        }
        if (prev != null) {
            prev.setNext(next);
        }

        // Mark that this ref has been removed.
        ref.remove();
    }

    protected synchronized void add(R newRef) {
        if (first != null) {
            newRef.setNext(first);
            first.setPrevious(newRef);
        }
        first = newRef;
    }

    @SuppressFBWarnings("IS")
    protected R getFirst() {
        return first;
    }
}
