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

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.cast.ToANode;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;

@CoreModule(value = "Queue", isClass = true)
public abstract class QueueNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyQueue allocate(RubyClass rubyClass) {
            final RubyQueue instance = new RubyQueue(rubyClass, getLanguage().queueShape, new UnsizedQueue());
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = { "push", "<<", "enq" }, required = 1)
    public abstract static class PushNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyQueue push(RubyQueue self, final Object value,
                @Cached PropagateSharingNode propagateSharingNode) {
            final UnsizedQueue queue = self.queue;

            propagateSharingNode.execute(this, self, value);

            if (queue.add(value)) {
                return self;
            } else {
                throw new RaiseException(getContext(), coreExceptions().closedQueueError(this));
            }
        }

    }

    @Primitive(name = "queue_pop")
    public abstract static class PopNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "!nonBlocking")
        Object popBlocking(RubyQueue self, boolean nonBlocking, Nil timeoutMilliseconds,
                @Cached @Exclusive InlinedBranchProfile closedProfile) {
            final UnsizedQueue queue = self.queue;

            final Object value = doPop(queue);

            if (value == UnsizedQueue.CLOSED) {
                closedProfile.enter(this);
                return nil;
            } else {
                return value;
            }
        }

        @TruffleBoundary
        private Object doPop(UnsizedQueue queue) {
            return getContext().getThreadManager().runUntilResult(this, queue::take);
        }

        @Specialization(guards = "!nonBlocking")
        Object popBlocking(RubyQueue self, boolean nonBlocking, long timeoutMilliseconds) {
            final UnsizedQueue queue = self.queue;
            final long deadline = System.currentTimeMillis() + timeoutMilliseconds;

            return getContext().getThreadManager().runUntilResult(this, () -> {
                final long currentTimeout = deadline - System.currentTimeMillis();
                final Object value;

                if (currentTimeout > 0) {
                    value = queue.poll(currentTimeout);
                } else {
                    value = queue.poll();
                }

                if (value == UnsizedQueue.CLOSED || value == null) {
                    return nil;
                } else {
                    return value;
                }
            });
        }

        @Specialization(guards = "nonBlocking")
        Object popNonBlock(RubyQueue self, boolean nonBlocking, Nil timeoutMilliseconds,
                @Cached @Exclusive InlinedBranchProfile errorProfile) {
            final UnsizedQueue queue = self.queue;

            final Object value = queue.poll();

            if (value == null) {
                errorProfile.enter(this);
                throw new RaiseException(getContext(), coreExceptions().threadError("queue empty", this));
            } else {
                return value;
            }
        }

    }

    @CoreMethod(names = "empty?")
    public abstract static class EmptyNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean empty(RubyQueue self) {
            final UnsizedQueue queue = self.queue;
            return queue.isEmpty();
        }

    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int size(RubyQueue self) {
            final UnsizedQueue queue = self.queue;
            return queue.size();
        }

    }

    @CoreMethod(names = "clear")
    public abstract static class ClearNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyQueue clear(RubyQueue self) {
            final UnsizedQueue queue = self.queue;
            queue.clear();
            return self;
        }

    }

    @CoreMethod(names = "initialize", visibility = Visibility.PRIVATE, optional = 1)
    @ImportStatic(ArrayGuards.class)
    public abstract static class InitializeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyQueue initialize(RubyQueue self, NotProvided enumerable) {
            return self;
        }

        @Specialization(guards = "wasProvided(enumerable)")
        RubyQueue initialize(RubyQueue self, Object enumerable,
                @CachedLibrary(limit = "storageStrategyLimit()") ArrayStoreLibrary stores,
                @Cached ToANode toANode) {
            final RubyArray rubyArray = toANode.executeToA(enumerable);
            final Object[] array = stores.boxedCopyOfRange(rubyArray.getStore(), 0, rubyArray.size);
            self.queue.addAll(array);

            return self;
        }
    }

    @CoreMethod(names = "marshal_dump")
    public abstract static class MarshalDumpNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object marshal_dump(RubyQueue self) {
            throw new RaiseException(getContext(), coreExceptions().typeErrorCantDump(self, this));
        }

    }

    @CoreMethod(names = "num_waiting")
    public abstract static class NumWaitingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        int num_waiting(RubyQueue self) {
            final UnsizedQueue queue = self.queue;
            return queue.getNumberWaitingToTake();
        }

    }

    @CoreMethod(names = "close")
    public abstract static class CloseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyQueue close(RubyQueue self) {
            self.queue.close();
            return self;
        }

    }

    @CoreMethod(names = "closed?")
    public abstract static class ClosedNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean closed(RubyQueue self) {
            return self.queue.isClosed();
        }

        @Specialization
        boolean closed(RubySizedQueue self) {
            return self.queue.isClosed();
        }

    }

}
