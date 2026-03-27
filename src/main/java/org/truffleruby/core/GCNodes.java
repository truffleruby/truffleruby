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
package org.truffleruby.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.Arrays;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.collections.WeakValueCache;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.language.control.RaiseException;

@CoreModule("GC")
public abstract class GCNodes {

    @Primitive(name = "gc_start")
    public abstract static class GCStartPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object vmGCStart() {
            System.gc();
            return nil;
        }

    }

    /** Attempts to run the garbage collector. This cannot be guaranteed in general, but calling this method should be
     * much more likely to actually trigger GC than calling {@link System#gc()} or Ruby's {@link GCStartPrimitiveNode}.
     *
     * <p>
     * In particular, this attempts to trigger the GC by waiting until a weak reference has been cleared.
     *
     * <p>
     * Note that even when GC is triggered, there is not guarantee that the all the garbage has been cleared or all the
     * memory reclaimed. */
    @Primitive(name = "gc_force")
    public abstract static class GCForce extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object force() {
            // NOTE(norswap, 16 Apr 20): We could have used a WeakReference here, but the hope is that the extra
            // indirection will prevent the compiler to optimize this method away (assuming JIT compilation, and
            // the fact that such an optimizaton is permitted and possible, both of which are unlikely).
            WeakValueCache<Object, Object> cache = new WeakValueCache<>();
            Object key = new Object();

            // NOTE(norswap, 02 Feb 21): The split into two methods here is another way to try to outwit optimizations
            // by making sure the method that allocates the weak value is exited before we run the GC loop;
            initCache(cache, key);
            gcLoop(cache, key);
            return nil;
        }

        @SuppressFBWarnings("DLS")
        private void initCache(WeakValueCache<Object, Object> cache, Object key) {
            Object value = new Object();
            cache.put(key, value);
            value = null;
        }

        private void gcLoop(WeakValueCache<Object, Object> cache, Object key) {
            final long duration = Duration.ofSeconds(60).toNanos();
            final long start = System.nanoTime();
            do {
                if (System.nanoTime() - start > duration) {
                    throw new RaiseException(
                            getContext(),
                            getContext().getCoreExceptions().runtimeError(
                                    "gc_force exceeded its 60 seconds timeout",
                                    this));
                }
                System.gc();
                TruffleSafepoint.poll(this);
            } while (cache.get(key) != null);
        }
    }


    @CoreMethod(names = "count", onSingleton = true)
    public abstract static class CountNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long count() {
            long count = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                count += bean.getCollectionCount();
            }
            return count;
        }

    }

    @Primitive(name = "gc_time")
    public abstract static class TimeNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        long time() {
            long time = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                time += bean.getCollectionTime();
            }
            return time;
        }

    }

    @Primitive(name = "gc_stat")
    public abstract static class GCStatPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        RubyArray stat() {
            final long[] data = getGCData();
            return createArray(data);
        }

        @TruffleBoundary
        private long[] getGCData() {
            long time = 0;
            long count = 0;
            long minorCount = 0;
            long majorCount = 0;
            long unknownCount = 0;

            // Get GC time and counts from GarbageCollectorMXBean
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                time += bean.getCollectionTime();
                count += bean.getCollectionCount();
                switch (bean.getName()) {
                    case "G1 Young Generation": // during 'jvm' and 'jvm-ce'
                    case "PS Scavenge": // during 'jvm --vm.XX:+UseParallelGC'
                    case "young generation scavenger": // during 'native'
                        minorCount += bean.getCollectionCount();
                        break;
                    case "G1 Old Generation": // during 'jvm' and 'jvm-ce'
                    case "PS MarkSweep": // during 'jvm --vm.XX:+UseParallelGC'
                    case "complete scavenger": // during 'native'
                        majorCount += bean.getCollectionCount();
                        break;
                    default:
                        unknownCount += bean.getCollectionCount();
                        break;
                }
            }

            final MemoryUsage total = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

            return new long[]{
                    time,
                    count,
                    minorCount,
                    majorCount,
                    unknownCount,
                    total.getUsed(),
                    total.getCommitted(),
                    total.getInit(),
                    total.getMax(),
            };
        }

    }

    @Primitive(name = "gc_heap_stats")
    public abstract static class GCHeapStatsNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyArray heapStats(
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            String[] memoryPoolNames = new String[0];
            Object[] memoryPools;

            // Get GC time and counts from GarbageCollectorMXBean
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                // Get MemoryPoolName relevant to GC
                if (bean.getMemoryPoolNames().length > memoryPoolNames.length) {
                    // Since old generation memory pools are a superset of young generation memory pools,
                    // it suffices to check that we have the longer list of memory pools
                    memoryPoolNames = bean.getMemoryPoolNames();
                }
            }

            // Get memory usage values from relevant memory pools (2-3 / ~8 are relevant)
            memoryPools = new Object[memoryPoolNames.length];
            // On Native Image, ManagementFactory.getMemoryPoolMXBeans() is empty
            Arrays.fill(memoryPools, nil);
            for (int i = 0; i < memoryPoolNames.length; i++) {
                String memoryPoolName = memoryPoolNames[i];
                for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
                    if (bean.getName().equals(memoryPoolName)) {
                        memoryPools[i] = beanToArray(bean);
                    }
                }
            }

            Object[] memoryPoolNamesCast = new Object[memoryPoolNames.length];
            for (int i = 0; i < memoryPoolNames.length; i++) {
                memoryPoolNamesCast[i] = createString(fromJavaStringNode, memoryPoolNames[i], Encodings.UTF_8);
            }


            return createArray(new Object[]{
                    createArray(memoryPoolNamesCast),
                    createArray(memoryPools) });
        }

        private RubyArray beanToArray(MemoryPoolMXBean bean) {
            MemoryUsage usage = bean.getUsage();
            MemoryUsage peak = bean.getPeakUsage();
            MemoryUsage last = bean.getCollectionUsage();
            return createArray(
                    new long[]{
                            usage.getUsed(),
                            usage.getCommitted(),
                            usage.getInit(),
                            usage.getMax(),
                            peak.getUsed(),
                            peak.getCommitted(),
                            peak.getInit(),
                            peak.getMax(),
                            last.getUsed(),
                            last.getCommitted(),
                            last.getInit(),
                            last.getMax(),
                    });
        }

    }

}
