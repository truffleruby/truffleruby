/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import java.lang.ref.WeakReference;
import java.lang.ref.Cleaner.Cleanable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressFBWarnings("VO")
public final class ValueWrapperManager {

    static final long UNSET_HANDLE = -2L;

    /* These constants are taken from lib/cext/include/ruby/internal/special_consts.h with USE_FLONUM=false */

    public static final int FALSE_HANDLE = 0b0000;
    public static final int TRUE_HANDLE = 0b0110;
    public static final int NIL_HANDLE = 0b0010;
    public static final int UNDEF_HANDLE = 0b1010;
    public static final long IMMEDIATE_MASK = 0b0011;

    public static final long LONG_TAG = 1;
    public static final long OBJECT_TAG = 0;

    public static final long MIN_FIXNUM_VALUE = -(1L << 62);
    public static final long MAX_FIXNUM_VALUE = (1L << 62) - 1;

    public final ValueWrapper trueWrapper = new ValueWrapper(true, TRUE_HANDLE, null);
    public final ValueWrapper falseWrapper = new ValueWrapper(false, FALSE_HANDLE, null);
    public final ValueWrapper undefWrapper = new ValueWrapper(NotProvided.INSTANCE, UNDEF_HANDLE, null);

    private volatile HandleBlockWeakReference[] blockMap = new HandleBlockWeakReference[0];

    public static HandleBlockHolder getBlockHolder(RubyLanguage language) {
        return language.getCurrentFiber().handleData;
    }

    @TruffleBoundary
    public synchronized HandleBlock addToBlockMap(RubyLanguage language) {
        HandleBlock block = new HandleBlock(language, this);
        int blockIndex = block.getIndex();
        HandleBlockWeakReference[] map = growMapIfRequired(blockMap, blockIndex);
        blockMap = map;
        map[blockIndex] = new HandleBlockWeakReference(block);

        return block;
    }

    @TruffleBoundary
    public HandleBlock addToSharedBlockMap(RubyLanguage language) {
        synchronized (language) {
            HandleBlock block = new HandleBlock(language, this);
            int blockIndex = block.getIndex();
            HandleBlockWeakReference[] map = growMapIfRequired(language.handleBlockSharedMap, blockIndex);
            language.handleBlockSharedMap = map;
            map[blockIndex] = new HandleBlockWeakReference(block);
            return block;
        }
    }

    private static HandleBlockWeakReference[] growMapIfRequired(HandleBlockWeakReference[] map, int blockIndex) {
        if (blockIndex + 1 > map.length) {
            final HandleBlockWeakReference[] copy = new HandleBlockWeakReference[blockIndex + 1];
            System.arraycopy(map, 0, copy, 0, map.length);
            map = copy;
        }
        return map;
    }

    public ValueWrapper getWrapperFromHandleMap(long handle, RubyLanguage language) {
        final int index = HandleBlock.getHandleIndex(handle);
        final HandleBlock block = getBlockFromMap(index, language);
        if (block == null) {
            return null;
        }
        return block.getWrapper(handle);
    }

    private HandleBlock getBlockFromMap(int index, RubyLanguage language) {
        final HandleBlockWeakReference[] blockMap = this.blockMap;
        final HandleBlockWeakReference[] sharedMap = language.handleBlockSharedMap;
        HandleBlockWeakReference ref = null;
        // First try getting the block from the context's map
        if (index >= 0 && index < blockMap.length) {
            ref = blockMap[index];
        }
        // If no block was found in the context's map then look in the
        // shared map. If there is a block in a context's map then the
        // same block will not be in the shared map and vice versa.
        if (ref == null && index >= 0 && index < sharedMap.length) {
            ref = sharedMap[index];
        }
        if (ref == null) {
            return null;
        }
        return ref.get();
    }

    public void freeAllBlocksInMap() {
        HandleBlockWeakReference[] map = blockMap;

        for (HandleBlockWeakReference ref : map) {
            if (ref == null) {
                continue;
            }
            HandleBlock block = ref.get();
            if (block != null) {
                block.cleanable.clean();
            }
        }
    }

    public void cleanup(HandleBlockHolder holder) {
        holder.handleBlock = null;
    }

    protected static final class FreeHandleBlock {
        public final long start;
        public final FreeHandleBlock next;

        public FreeHandleBlock(long start, FreeHandleBlock next) {
            this.start = start;
            this.next = next;
        }
    }

    private final AtomicLong counter = new AtomicLong();

    protected void recordHandleAllocation() {
        counter.incrementAndGet();
    }

    public long totalHandleAllocations() {
        return counter.get();
    }

    private static final long ADDRESS_ALIGN_BITS = 3;
    private static final int BLOCK_BITS = 15;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - ADDRESS_ALIGN_BITS);
    private static final int BLOCK_BYTE_SIZE = BLOCK_SIZE << ADDRESS_ALIGN_BITS;
    private static final long BLOCK_MASK = -1L << BLOCK_BITS;
    private static final long OFFSET_MASK = ~BLOCK_MASK;
    public static final long ALLOCATION_BASE = 0x0badL << 48;

    public static final class HandleBlockAllocator {

        private long nextBlock = ALLOCATION_BASE;
        private FreeHandleBlock firstFreeBlock = null;

        public synchronized long getFreeBlock() {
            if (firstFreeBlock != null) {
                FreeHandleBlock block = firstFreeBlock;
                firstFreeBlock = block.next;
                return block.start;
            } else {
                long block = nextBlock;
                nextBlock = nextBlock + BLOCK_BYTE_SIZE;
                return block;
            }
        }

        public synchronized void addFreeBlock(long blockBase) {
            firstFreeBlock = new FreeHandleBlock(blockBase, firstFreeBlock);
        }
    }

    public static final class HandleBlock {

        private final long base;
        private final ValueWrapperWeakReference[] wrappers;
        private int count;

        @SuppressWarnings("unused") private Cleanable cleanable;

        public HandleBlock(RubyLanguage language, ValueWrapperManager manager) {
            HandleBlockAllocator allocator = language.handleBlockAllocator;
            long base = allocator.getFreeBlock();
            this.base = base;
            this.wrappers = new ValueWrapperWeakReference[BLOCK_SIZE];
            this.count = 0;
            this.cleanable = language.cleaner.register(this, HandleBlock.makeCleaner(manager, base, allocator));
        }

        private static Runnable makeCleaner(ValueWrapperManager manager, long base, HandleBlockAllocator allocator) {
            return () -> {
                manager.blockMap[(int) ((base - ALLOCATION_BASE) >> BLOCK_BITS)] = null;
                allocator.addFreeBlock(base);
            };
        }

        public long getBase() {
            return base;
        }

        public int getIndex() {
            return (int) ((base - ALLOCATION_BASE) >> BLOCK_BITS);
        }

        public ValueWrapper getWrapper(long handle) {
            int offset = (int) (handle & OFFSET_MASK) >> ADDRESS_ALIGN_BITS;
            return wrappers[offset].get();
        }

        public boolean isFull() {
            return count == BLOCK_SIZE;
        }

        public long setHandleOnWrapper(ValueWrapper wrapper) {
            long handle = getBase() + count * Pointer.SIZE;
            wrapper.setHandle(handle, this);
            wrappers[count] = new ValueWrapperWeakReference(wrapper);
            count++;
            return handle;
        }

        public static int getHandleIndex(long handle) {
            return (int) ((handle - ALLOCATION_BASE) >> BLOCK_BITS);
        }
    }

    public static final class HandleBlockWeakReference extends WeakReference<HandleBlock> {
        HandleBlockWeakReference(HandleBlock referent) {
            super(referent);
        }
    }

    public static final class ValueWrapperWeakReference extends WeakReference<ValueWrapper> {
        ValueWrapperWeakReference(ValueWrapper referent) {
            super(referent);
        }
    }

    public static final class HandleBlockHolder {
        private HandleBlock handleBlock = null;
        private HandleBlock sharedHandleBlock = null;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AllocateHandleNode extends RubyBaseNode {

        private static final Set<ValueWrapper> keepAlive = ConcurrentHashMap.newKeySet();

        public abstract long execute(Node node, ValueWrapper wrapper);

        @Specialization(guards = "!isSharedObject(wrapper)")
        static long allocateHandleOnKnownThread(Node node, ValueWrapper wrapper) {
            if (getContext(node).getOptions().CEXTS_KEEP_HANDLES_ALIVE) {
                keepAlive(wrapper);
            }
            return allocateHandle(
                    node,
                    wrapper,
                    getContext(node),
                    getLanguage(node),
                    getBlockHolder(getLanguage(node)),
                    false);
        }

        @Specialization(guards = "isSharedObject(wrapper)")
        static long allocateSharedHandleOnKnownThread(Node node, ValueWrapper wrapper) {
            if (getContext(node).getOptions().CEXTS_KEEP_HANDLES_ALIVE) {
                keepAlive(wrapper);
            }
            return allocateHandle(
                    node,
                    wrapper,
                    getContext(node),
                    getLanguage(node),
                    getBlockHolder(getLanguage(node)),
                    true);
        }

        @TruffleBoundary
        protected static void keepAlive(ValueWrapper wrapper) {
            keepAlive.add(wrapper);
        }

        protected static long allocateHandle(Node node, ValueWrapper wrapper, RubyContext context,
                RubyLanguage language, HandleBlockHolder holder, boolean shared) {
            HandleBlock block;
            if (shared) {
                block = holder.sharedHandleBlock;
            } else {
                block = holder.handleBlock;
            }

            if (context.getOptions().CEXTS_TO_NATIVE_COUNT) {
                context.getValueWrapperManager().recordHandleAllocation();
            }

            if (context.getOptions().BACKTRACE_ON_TO_NATIVE) {
                context.getDefaultBacktraceFormatter().printBacktraceOnEnvStderr("ValueWrapper#toNative: ", node);
            }

            if (block == null || block.isFull()) {
                if (shared) {
                    block = context.getValueWrapperManager().addToSharedBlockMap(language);
                    holder.sharedHandleBlock = block;
                } else {
                    block = context.getValueWrapperManager().addToBlockMap(language);
                    holder.handleBlock = block;
                }

            }
            return block.setHandleOnWrapper(wrapper);
        }

        protected static boolean isSharedObject(ValueWrapper wrapper) {
            return wrapper.getObject() instanceof ImmutableRubyObject;
        }
    }

    public static HandleBlock allocateNewBlock(RubyContext context, RubyLanguage language) {
        HandleBlockHolder holder = getBlockHolder(language);
        HandleBlock block = context.getValueWrapperManager().addToBlockMap(language);

        holder.handleBlock = block;
        return block;
    }

    public static boolean isTaggedLong(long handle) {
        return (handle & LONG_TAG) == LONG_TAG;
    }

    public static boolean isTaggedObject(long handle) {
        return handle != FALSE_HANDLE && (handle & IMMEDIATE_MASK) == OBJECT_TAG;
    }

    public static boolean isMallocAligned(long handle) {
        return handle != FALSE_HANDLE && (handle & 0b111) == 0;
    }

    public static boolean isWrapper(Object value) {
        return value instanceof ValueWrapper;
    }

    public static long untagTaggedLong(long handle) {
        return handle >> 1;
    }

    @ExportLibrary(InteropLibrary.class)
    @GenerateUncached
    public static final class UnwrapperFunction implements TruffleObject {

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                @Cached UnwrapNode unwrapNode,
                @Bind Node node) {
            return unwrapNode.execute(node, arguments[0]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @GenerateUncached
    public static final class ID2SymbolFunction implements TruffleObject {

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public RubySymbol execute(Object[] arguments,
                @Cached IDToSymbolNode unwrapIDNode) {
            return unwrapIDNode.execute(arguments[0]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @GenerateUncached
    public static final class Symbol2IDFunction implements TruffleObject {

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                @Cached UnwrapNode unwrapNode,
                @Cached SymbolToIDNode symbolTOIDNode,
                @Bind Node node) {
            return symbolTOIDNode.execute(unwrapNode.execute(node, arguments[0]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @GenerateUncached
    public static final class WrapperFunction implements TruffleObject {

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        protected ValueWrapper execute(Object[] arguments,
                @Cached WrapNode wrapNode) {
            return wrapNode.execute(arguments[0]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @GenerateUncached
    public static final class ToNativeObjectFunction implements TruffleObject {

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        protected long execute(Object[] arguments,
                @CachedLibrary(limit = "1") InteropLibrary values) throws UnsupportedMessageException {
            values.toNative(arguments[0]);
            return values.asPointer(arguments[0]);
        }
    }

}
