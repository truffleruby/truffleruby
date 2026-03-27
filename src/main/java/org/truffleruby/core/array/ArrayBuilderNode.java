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
package org.truffleruby.core.array;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.array.library.ArrayStoreLibrary.ArrayAllocator;
import org.truffleruby.core.array.ArrayBuilderNodeFactory.AppendArrayNodeGen;
import org.truffleruby.core.array.ArrayBuilderNodeFactory.AppendOneNodeGen;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

/** Builds a new Array and learns its storage strategy and its expected length. The storage strategy is generalized as
 * needed and the expected length is increased until all elements fit.
 * <p>
 * Append nodes handle only one strategy, but must still return a valid storage when:
 *
 * <li>The element(s) added do not match the strategy.
 * <li>The being-built storage no longer matches the strategy, due to the node having been replaced by another thread or
 * by another usage (e.g. recursive) of this ArrayBuilderNode. */
public abstract class ArrayBuilderNode extends RubyBaseNode {

    public static final class BuilderState {
        private int capacity;
        private int nextIndex = 0;
        Object store;

        private BuilderState(Object store, int capacity) {
            this.capacity = capacity;
            this.store = store;
        }
    }

    @NeverDefault
    public static ArrayBuilderNode create() {
        return new ArrayBuilderProxyNode();
    }

    public abstract BuilderState start(int length);

    public abstract void appendArray(BuilderState state, int index, RubyArray array);

    public abstract void appendValue(BuilderState state, int index, Object value);

    public abstract Object finish(BuilderState state, int length);

    private static final class ArrayBuilderProxyNode extends ArrayBuilderNode {

        @Child StartNode startNode = new StartNode(ArrayStoreLibrary.initialAllocator(false));
        @Child AppendArrayNode appendArrayNode;
        @Child AppendOneNode appendOneNode;

        @Override
        public BuilderState start(int length) {
            return startNode.start(length);
        }

        @Override
        public void appendArray(BuilderState state, int index, RubyArray array) {
            getAppendArrayNode().executeAppend(state, index, array);
        }

        @Override
        public void appendValue(BuilderState state, int index, Object value) {
            getAppendOneNode().executeAppend(state, index, value);
        }

        @Override
        public Object finish(BuilderState state, int length) {
            assert length == state.nextIndex;
            return state.store;
        }

        private AppendArrayNode getAppendArrayNode() {
            if (appendArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendArrayNode = insert(AppendArrayNode.create());
            }
            return appendArrayNode;
        }

        private AppendOneNode getAppendOneNode() {
            if (appendOneNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendOneNode = insert(AppendOneNode.create());
            }
            return appendOneNode;
        }

        public synchronized ArrayAllocator updateStrategy(ArrayStoreLibrary.ArrayAllocator newStrategy) {
            final ArrayStoreLibrary.ArrayAllocator oldStrategy = startNode.allocator;
            final ArrayStoreLibrary.ArrayAllocator updatedAllocator;
            // If two threads have raced to update the strategy then
            // oldStrategy may have been updated while waiting for to
            // claim the lock. We handle this by calculating the new
            // strategy explicitly here and returning it from this
            // function.
            if (oldStrategy != newStrategy) {
                updatedAllocator = ArrayStoreLibrary.getUncached().generalizeForStore(
                        oldStrategy.allocate(0),
                        newStrategy.allocate(0));
            } else {
                updatedAllocator = oldStrategy;
            }

            if (updatedAllocator != oldStrategy) {
                startNode.replace(new StartNode(updatedAllocator));
            }

            if (newStrategy != oldStrategy) {
                if (appendArrayNode != null) {
                    appendArrayNode.replace(AppendArrayNode.create());
                }
                if (appendOneNode != null) {
                    appendOneNode.replace(AppendOneNode.create());
                }
            }

            return updatedAllocator;
        }

    }

    public abstract static class ArrayBuilderBaseNode extends RubyBaseNode {

        protected ArrayAllocator replaceNodes(ArrayStoreLibrary.ArrayAllocator strategy) {
            final ArrayBuilderProxyNode parent = (ArrayBuilderProxyNode) getParent();
            return parent.updateStrategy(strategy);
        }
    }

    public static final class StartNode extends ArrayBuilderBaseNode {

        private final ArrayStoreLibrary.ArrayAllocator allocator;

        public StartNode(ArrayStoreLibrary.ArrayAllocator allocator) {
            this.allocator = allocator;
        }

        public BuilderState start(int length) {
            if (allocator == ArrayStoreLibrary.initialAllocator(false)) {
                return new BuilderState(allocator.allocate(0), length);
            } else {
                return new BuilderState(allocator.allocate(length), length);
            }
        }
    }

    @ImportStatic(ArrayGuards.class)
    public abstract static class AppendOneNode extends ArrayBuilderBaseNode {

        public static AppendOneNode create() {
            return AppendOneNodeGen.create();
        }

        public abstract void executeAppend(BuilderState array, int index, Object value);

        @Specialization(
                guards = "arrays.acceptsValue(state.store, value)",
                limit = "1")
        void appendCompatibleType(BuilderState state, int index, Object value,
                @Bind("state.store") Object store,
                @CachedLibrary("store") ArrayStoreLibrary arrays,
                @Cached BranchProfile growProfile) {
            assert state.nextIndex == index;
            final int length = arrays.capacity(state.store);
            if (index >= length) {
                growProfile.enter();
                final int capacity = ArrayUtils.capacityForOneMore(getLanguage(), length);
                state.store = arrays.expand(state.store, capacity);
                state.capacity = capacity;
            }
            arrays.write(state.store, index, value);
            state.nextIndex++;
        }

        @Specialization(
                guards = "!arrays.acceptsValue(state.store, value)",
                limit = "1")
        void appendNewStrategy(BuilderState state, int index, Object value,
                @Bind("state.store") Object store,
                @CachedLibrary("store") ArrayStoreLibrary arrays) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            assert state.nextIndex == index;
            final ArrayStoreLibrary stores = ArrayStoreLibrary.getUncached();
            ArrayStoreLibrary.ArrayAllocator newAllocator = stores.generalizeForValue(state.store, value);

            final int currentCapacity = state.capacity;
            final int neededCapacity;
            if (index >= currentCapacity) {
                neededCapacity = ArrayUtils.capacityForOneMore(getLanguage(), currentCapacity);
            } else {
                neededCapacity = currentCapacity;
            }

            newAllocator = replaceNodes(newAllocator);

            final Object newStore = newAllocator.allocate(neededCapacity);
            stores.copyContents(state.store, 0, newStore, 0, index);
            stores.write(newStore, index, value);
            state.store = newStore;
            state.capacity = neededCapacity;
            state.nextIndex++;
        }

    }

    @ImportStatic(ArrayGuards.class)
    public abstract static class AppendArrayNode extends ArrayBuilderBaseNode {

        public static AppendArrayNode create() {
            return AppendArrayNodeGen.create();
        }


        public abstract void executeAppend(BuilderState state, int index, RubyArray value);

        @Specialization(
                guards = { "arrays.acceptsAllValues(state.store, other.getStore())" },
                limit = "storageStrategyLimit()")
        void appendCompatibleStrategy(BuilderState state, int index, RubyArray other,
                @Bind("state.store") Object store,
                @Bind("other.getStore()") Object otherStore,
                @CachedLibrary("store") ArrayStoreLibrary arrays,
                @CachedLibrary("otherStore") ArrayStoreLibrary others,
                @Cached BranchProfile growProfile) {
            assert state.nextIndex == index;
            final int otherSize = other.size;
            final int neededSize = index + otherSize;

            int length = arrays.capacity(state.store);
            if (neededSize > length) {
                growProfile.enter();
                final int capacity = ArrayUtils.capacity(getLanguage(), length, neededSize);
                state.store = arrays.expand(state.store, capacity);
                state.capacity = capacity;
            }

            others.copyContents(otherStore, 0, state.store, index, otherSize);
            state.nextIndex = state.nextIndex + otherSize;
        }

        @Specialization(
                guards = { "!arrayLibrary.acceptsAllValues(state.store, other.getStore())" },
                limit = "1")
        void appendNewStrategy(BuilderState state, int index, RubyArray other,
                @Bind("state.store") Object store,
                @CachedLibrary("store") ArrayStoreLibrary arrayLibrary) {
            assert state.nextIndex == index;
            final int otherSize = other.size;
            if (otherSize != 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final ArrayStoreLibrary newArrayLibrary = ArrayStoreLibrary.getUncached();
                final int neededSize = index + otherSize;

                final Object newStore;

                final int currentCapacity = state.capacity;
                final int neededCapacity;
                if (neededSize > currentCapacity) {
                    neededCapacity = ArrayUtils.capacity(getLanguage(), currentCapacity, neededSize);
                } else {
                    neededCapacity = currentCapacity;
                }

                ArrayAllocator allocator = replaceNodes(
                        newArrayLibrary.generalizeForStore(state.store, other.getStore()));
                newStore = allocator.allocate(neededCapacity);

                newArrayLibrary.copyContents(state.store, 0, newStore, 0, index);

                final Object otherStore = other.getStore();
                newArrayLibrary.copyContents(otherStore, 0, newStore, index, otherSize);

                state.store = newStore;
                state.capacity = neededCapacity;
                state.nextIndex = state.nextIndex + otherSize;
            }
        }
    }

}
