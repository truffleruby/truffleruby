/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.queue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.cast.BooleanCastWithDefaultNode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

/** We do not reuse much of class Queue since we need to be able to replace the queue in this case and methods are small
 * anyway. */
@CoreModule(value = "SizedQueue", isClass = true)
public abstract class SizedQueueNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySizedQueue allocate(RubyClass rubyClass) {
            final RubySizedQueue instance = new RubySizedQueue(rubyClass, getLanguage().sizedQueueShape, null);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "initialize", visibility = Visibility.PRIVATE, required = 1, lowerFixnum = 1)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySizedQueue initialize(RubySizedQueue self, int capacity,
                @Cached InlinedBranchProfile errorProfile) {
            if (capacity <= 0) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("queue size must be positive", this));
            }

            final SizedQueue blockingQueue = new SizedQueue(capacity);
            self.queue = blockingQueue;
            return self;
        }

    }

    @CoreMethod(names = "max=", required = 1, lowerFixnum = 1)
    public abstract static class SetMaxNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int setMax(RubySizedQueue self, int newCapacity,
                @Cached InlinedBranchProfile errorProfile) {
            if (newCapacity <= 0) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("queue size must be positive", this));
            }

            final SizedQueue queue = self.queue;
            queue.changeCapacity(newCapacity);
            return newCapacity;
        }

    }

    @CoreMethod(names = "max")
    public abstract static class MaxNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int max(RubySizedQueue self) {
            final SizedQueue queue = self.queue;
            return queue.getCapacity();
        }

    }

    @Primitive(name = "sized_queue_push")
    public abstract static class SizedQueuePushNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object doPush(RubySizedQueue self, final Object value, Object maybeNonBlocking, Object timeoutMilliseconds,
                @Cached BooleanCastWithDefaultNode booleanCastWithDefaultNode,
                @Cached PushNode pushNode) {
            final boolean nonBlocking = booleanCastWithDefaultNode.execute(this, maybeNonBlocking, false);
            return pushNode.execute(this, self, value, nonBlocking, timeoutMilliseconds);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class PushNode extends RubyBaseNode {

        public abstract Object execute(Node node, RubySizedQueue self, final Object value, boolean nonBlocking,
                Object timeoutMilliseconds);

        @Specialization(guards = "!nonBlocking")
        static RubySizedQueue pushBlocking(
                Node node, RubySizedQueue self, final Object value, boolean nonBlocking, Nil timeoutMilliseconds,
                @Cached @Shared PropagateSharingNode propagateSharingNode) {
            final SizedQueue queue = self.queue;

            propagateSharingNode.execute(node, self, value);
            doPushBlocking(node, value, queue);

            return self;
        }

        @TruffleBoundary
        private static void doPushBlocking(Node node, final Object value, final SizedQueue queue) {
            getContext(node).getThreadManager().runUntilResult(node, () -> {
                if (queue.put(value)) {
                    return BlockingAction.SUCCESS;
                } else {
                    throw new RaiseException(getContext(node), coreExceptions(node).closedQueueError(node));
                }
            });
        }

        @Specialization(guards = "!nonBlocking")
        static Object pushTimeout(
                Node node, RubySizedQueue self, final Object value, boolean nonBlocking, long timeoutMilliseconds,
                @Cached @Shared PropagateSharingNode propagateSharingNode) {
            final SizedQueue queue = self.queue;
            propagateSharingNode.execute(node, self, value);
            final long deadline = System.currentTimeMillis() + timeoutMilliseconds;

            boolean success = getContext(node).getThreadManager().runUntilResult(node, () -> {
                final long currentTimeout = deadline - System.currentTimeMillis();
                final Object result;

                if (currentTimeout > 0) {
                    result = queue.put(value, currentTimeout);
                } else {
                    result = queue.put(value, 0);
                }

                if (result == SizedQueue.CLOSED) {
                    throw new RaiseException(getContext(node), coreExceptions(node).closedQueueError(node));
                }

                return (boolean) result;
            });

            if (success) {
                return self;
            }

            return nil;
        }

        @Specialization(guards = "nonBlocking")
        static RubySizedQueue pushNonBlock(
                Node node, RubySizedQueue self, final Object value, boolean nonBlocking, Nil timeoutMilliseconds,
                @Cached @Shared PropagateSharingNode propagateSharingNode,
                @Cached InlinedBranchProfile errorProfile) {
            final SizedQueue queue = self.queue;

            propagateSharingNode.execute(node, self, value);

            switch (queue.offer(value)) {
                case SUCCESS:
                    return self;
                case FULL:
                    errorProfile.enter(node);
                    throw new RaiseException(getContext(node), coreExceptions(node).threadErrorQueueFull(node));
                case CLOSED:
                    errorProfile.enter(node);
                    throw new RaiseException(getContext(node), coreExceptions(node).closedQueueError(node));
            }

            return self;
        }

    }

    @Primitive(name = "sized_queue_pop")
    public abstract static class PopNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "!nonBlocking")
        Object popBlocking(RubySizedQueue self, boolean nonBlocking, Nil timeoutMilliseconds) {
            final SizedQueue queue = self.queue;

            final Object value = doPop(queue);

            if (value == SizedQueue.CLOSED) {
                return nil;
            } else {
                return value;
            }
        }

        @TruffleBoundary
        private Object doPop(SizedQueue queue) {
            return getContext().getThreadManager().runUntilResult(this, queue::take);
        }

        @Specialization(guards = "!nonBlocking")
        Object popBlocking(RubySizedQueue self, boolean nonBlocking, long timeoutMilliseconds) {
            final SizedQueue queue = self.queue;
            final long deadline = System.currentTimeMillis() + timeoutMilliseconds;

            return getContext().getThreadManager().runUntilResult(this, () -> {
                final long currentTimeout = deadline - System.currentTimeMillis();
                final Object value;

                if (currentTimeout > 0) {
                    value = queue.poll(currentTimeout);
                } else {
                    value = queue.poll();
                }

                if (value == SizedQueue.CLOSED || value == null) {
                    return nil;
                } else {
                    return value;
                }
            });
        }

        @Specialization(guards = "nonBlocking")
        Object popNonBlock(RubySizedQueue self, boolean nonBlocking, Nil timeoutMilliseconds,
                @Cached InlinedBranchProfile errorProfile) {
            final SizedQueue queue = self.queue;

            final Object value = queue.poll();

            if (value == null) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().threadError("queue empty", this));
            }

            return value;
        }

    }

    @CoreMethod(names = "empty?")
    public abstract static class EmptyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean empty(RubySizedQueue self) {
            final SizedQueue queue = self.queue;
            return queue.isEmpty();
        }

    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int size(RubySizedQueue self) {
            final SizedQueue queue = self.queue;
            return queue.size();
        }

    }

    @CoreMethod(names = "clear")
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySizedQueue clear(RubySizedQueue self) {
            final SizedQueue queue = self.queue;
            queue.clear();
            return self;
        }

    }

    @CoreMethod(names = "num_waiting")
    public abstract static class NumWaitingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int num_waiting(RubySizedQueue self) {
            final SizedQueue queue = self.queue;
            return queue.getNumberWaiting();
        }

    }

    @CoreMethod(names = "close")
    public abstract static class CloseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySizedQueue close(RubySizedQueue self) {
            self.queue.close();
            return self;
        }

    }

}
