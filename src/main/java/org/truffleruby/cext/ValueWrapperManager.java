/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.MarkingServiceNodes.KeepAliveNode;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@SuppressFBWarnings("VO")
public final class ValueWrapperManager {

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
        HandleBlock block = new HandleBlock(language, this, false);
        int blockIndex = block.getIndex();
        HandleBlockWeakReference[] map = growMapIfRequired(blockMap, blockIndex);
        blockMap = map;
        map[blockIndex] = new HandleBlockWeakReference(block);

        return block;
    }

    @TruffleBoundary
    public HandleBlock addToSharedBlockMap(RubyLanguage language) {
        synchronized (RubyLanguage.handleBlockAllocator) {
            HandleBlock block = new HandleBlock(language, this, true);
            int blockIndex = block.getIndex();
            HandleBlockWeakReference[] map = growMapIfRequired(RubyLanguage.handleBlockSharedMap, blockIndex);
            RubyLanguage.handleBlockSharedMap = map;
            map[blockIndex] = new HandleBlockWeakReference(block);
            RubyLanguage.keepSharedHandleBlockAlive(block);
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

    public ValueWrapper getWrapperFromHandleMap(long handle, boolean allowUnregisteredHandle, RubyLanguage language) {
        assert isTaggedObject(handle);
        final int index = HandleBlock.getBlockIndex(handle);

        final HandleBlock block = getBlockFromMap(index, language);
        if (block == null) {
            return null;
        }

        return block.getWrapper(handle, allowUnregisteredHandle);
    }

    private HandleBlock getBlockFromMap(int index, RubyLanguage language) {
        assert index >= 0;
        final HandleBlockWeakReference[] blockMap = this.blockMap;
        final HandleBlockWeakReference[] sharedMap = RubyLanguage.handleBlockSharedMap;
        HandleBlockWeakReference ref = null;

        // First try getting the block from the context's map
        if (index < blockMap.length) {
            ref = blockMap[index];
        }

        // If no block was found in the context's map then look in the
        // shared map. If there is a block in a context's map then the
        // same block will not be in the shared map and vice versa.
        if (ref == null && index < sharedMap.length) {
            ref = sharedMap[index];
        }

        return ref == null ? null : ref.get();
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

    /** A valid handle is of the form, bits (MSB first):
     * <ul>
     * <li>0-20: "0bade" in hexadecimal, "00001011101011011110" in binary. The sign bit is 0 to stay positive</li>
     * <li>20-49: 29-bit block index</li>
     * <li>49-61: 12-bit offset within block</li>
     * <li>61-64: all 0, to differentiate from tagged fixnums, etc</li>
     * </ul>
     *
     * We use an address > 2^48 because those are not valid memory pointers on 64-bit machines. Trying to dereference
     * them would immediately segfault, which is good because it would be an error as we don't actually use memory at
     * those addresses. */
    private static final long ADDRESS_ALIGN_BITS = 3;
    private static final int BLOCK_BITS = 15;
    private static final int BLOCK_SIZE = 1 << (BLOCK_BITS - ADDRESS_ALIGN_BITS);
    private static final int BLOCK_BYTE_SIZE = BLOCK_SIZE << ADDRESS_ALIGN_BITS;
    private static final long BLOCK_MASK = -1L << BLOCK_BITS;
    private static final long OFFSET_MASK = ~BLOCK_MASK;
    public static final long ALLOCATION_BASE = 0x0badeL << 44;
    private static final long MAX_HANDLE = (0x0badfL << 44) - 1;

    private static final long OBJECT_HANDLE_MASK = (-1L << 44) | 0b111;
    private static final long OBJECT_HANDLE_MASK_EXPECTED = ALLOCATION_BASE | OBJECT_TAG;

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

    /** A block of handles. A HandleBlock is only ever mutated ({@link #registerWrapper(ValueWrapper)}) by the single
     * fiber which currently has it in its {@link HandleBlockHolder} (fibers run on one thread at a time), so
     * {@code count} and {@code wrappers} need no synchronization. Once a full block is replaced by a fresh one in the
     * holder it is never mutated again. Other fibers only read already-registered wrappers through
     * {@link #getWrapper(long, boolean)}. This holds for {@code HandleBlockHolder#sharedHandleBlock} too: "shared"
     * refers to the wrapped objects being shared (immutable) and to the block being registered in the process-wide
     * {@code RubyLanguage#handleBlockSharedMap}, not to the block being filled by multiple fibers.
     *
     * The block references its wrappers strongly: each {@link ValueWrapper} is itself the weak reference to its object,
     * so the block does not keep wrapped objects alive. Live objects keep their wrapper alive (they reference it
     * strongly), the wrapper keeps its block alive, and once all objects of a block die the block and its wrappers form
     * an unreachable cycle collected together, which frees the handle range through the block's cleaner. */
    public static final class HandleBlock {

        private final long base;
        private final ValueWrapper[] wrappers;
        private int count;

        @SuppressWarnings("unused") private Cleanable cleanable;

        public HandleBlock(RubyLanguage language, ValueWrapperManager manager, boolean shared) {
            HandleBlockAllocator allocator = RubyLanguage.handleBlockAllocator;
            long base = allocator.getFreeBlock();
            this.base = base;
            this.wrappers = new ValueWrapper[BLOCK_SIZE];
            this.count = 0;
            /* Blocks for shared (immutable) objects are process-wide and immortal (see
             * RubyLanguage#keepSharedHandleBlockAlive), so no cleaner for them: it would never run, and its Runnable
             * would keep the per-context ValueWrapperManager alive. */
            this.cleanable = shared
                    ? null
                    : language.cleaner.register(this, HandleBlock.makeCleaner(manager, base, allocator));
        }

        private static Runnable makeCleaner(ValueWrapperManager manager, long base, HandleBlockAllocator allocator) {
            return () -> {
                manager.blockMap[getBlockIndex(base)] = null;
                allocator.addFreeBlock(base);
            };
        }

        public long getBase() {
            return base;
        }

        public int getIndex() {
            return getBlockIndex(base);
        }

        public ValueWrapper getWrapper(long handle, boolean allowUnregisteredHandle) {
            int offset = (int) (handle & OFFSET_MASK) >> ADDRESS_ALIGN_BITS;
            ValueWrapper wrapper = wrappers[offset];
            if (!allowUnregisteredHandle && wrapper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw unregisteredHandle(handle);
            }
            return wrapper;
        }

        @TruffleBoundary
        private static RuntimeException unregisteredHandle(long handle) {
            return CompilerDirectives.shouldNotReachHere("unregistered handle 0x" + Long.toHexString(handle));
        }

        public boolean isFull() {
            return count == BLOCK_SIZE;
        }

        /** The handle the next wrapper registered in this block will get, passed to the ValueWrapper constructor before
         * {@link #registerWrapper(ValueWrapper)} so {@code ValueWrapper#handle} can be final */
        public long nextHandle() {
            return base + count * Pointer.SIZE;
        }

        public void registerWrapper(ValueWrapper wrapper) {
            assert wrapper.handle == nextHandle();
            wrappers[count] = wrapper;
            count++;
        }

        public static int getBlockIndex(long handle) {
            assert handle >= ALLOCATION_BASE && handle <= MAX_HANDLE : handle;
            assert isTaggedObject(handle) : handle;
            return (int) ((handle - ALLOCATION_BASE) >> BLOCK_BITS);
        }
    }

    public static final class HandleBlockWeakReference extends WeakReference<HandleBlock> {
        HandleBlockWeakReference(HandleBlock referent) {
            super(referent);
        }
    }

    public static final class HandleBlockHolder {
        private HandleBlock handleBlock = null;
        private HandleBlock sharedHandleBlock = null;
    }

    /** Returns the handle of the wrapper, and keeps tagged object handles (and their object) alive until the end of the
     * current C extension call. The object must be passed in by the caller, from a strong reference it holds (or null
     * if it only has the wrapper): re-reading it from the wrapper's weak reference here would leave a window where, if
     * nothing else references the object strongly anymore, the GC could collect it before the keep-alive list
     * references it, leaving a dead handle. */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class WrapperToHandleNode extends RubyBaseNode {

        public abstract long execute(Node node, Object object, ValueWrapper wrapper);

        @Specialization
        static long wrapperToHandle(Node node, Object object, ValueWrapper wrapper,
                @Cached KeepAliveNode keepAliveNode,
                @Cached InlinedConditionProfile taggedObjectProfile) {
            final long handle = wrapper.handle;
            if (taggedObjectProfile.profile(node, isTaggedObject(handle))) {
                keepAliveNode.execute(node, object, wrapper);
            }
            return handle;
        }
    }

    /** Creates the ValueWrapper for an object which needs a tagged object handle, allocating the handle eagerly so
     * {@code ValueWrapper#handle} can be final. */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CreateWrapperNode extends RubyBaseNode {

        /** {@link ValueWrapper#keepAliveObject()} tokens, which keep both the object and its handle alive. */
        private static final Set<Object> keepAlive = ConcurrentHashMap.newKeySet();

        public abstract ValueWrapper execute(Node node, Object object);

        @Specialization(guards = "!isSharedObject(object)")
        static ValueWrapper createWrapperOnKnownThread(Node node, Object object) {
            return createWrapper(object, getContext(node), getLanguage(node), false);
        }

        @Specialization(guards = "isSharedObject(object)")
        static ValueWrapper createSharedWrapperOnKnownThread(Node node, Object object) {
            return createWrapper(object, getContext(node), getLanguage(node), true);
        }

        @TruffleBoundary
        protected static void keepAlive(Object keepAliveObject) {
            keepAlive.add(keepAliveObject);
        }

        protected static ValueWrapper createWrapper(Object object, RubyContext context,
                RubyLanguage language, boolean shared) {
            final HandleBlockHolder holder = getBlockHolder(language);
            HandleBlock block;
            if (shared) {
                block = holder.sharedHandleBlock;
            } else {
                block = holder.handleBlock;
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

            final ValueWrapper wrapper = new ValueWrapper(object, block.nextHandle(), block);
            block.registerWrapper(wrapper);
            if (context.getOptions().CEXTS_KEEP_HANDLES_ALIVE) {
                keepAlive(wrapper.keepAliveObject(object));
            }
            return wrapper;
        }

        protected static boolean isSharedObject(Object object) {
            return object instanceof ImmutableRubyObject;
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
        return (handle & OBJECT_HANDLE_MASK) == OBJECT_HANDLE_MASK_EXPECTED;
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

}
