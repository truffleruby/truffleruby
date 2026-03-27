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
package org.truffleruby.shared;

import org.graalvm.nativeimage.ImageInfo;

import java.lang.management.ManagementFactory;

public class Metrics {

    // These system properties are used before outside the SDK option system
    private static boolean METRICS_TIME;
    private static final boolean METRICS_MEMORY_USED_ON_EXIT = Boolean
            .getBoolean("truffleruby.metrics.memory_used_on_exit");

    public static void printTime(String id) {
        if (METRICS_TIME) {
            final long millis = System.currentTimeMillis();
            System.err.println(id + " " + millis);
        }
    }

    private static void printMemory() {
        // Memory stats aren't available in native.
        if (!ImageInfo.inImageCode() && METRICS_MEMORY_USED_ON_EXIT) {
            for (int n = 0; n < 10; n++) {
                System.gc();
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.err.printf("allocated %d%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }
    }

    /** Assigned here so the property is read after processing the --vm.D... options on SVM. It needs to be called in
     * each classloader using the Metrics class. */
    public static void initializeOption() {
        METRICS_TIME = Boolean.getBoolean("truffleruby.metrics.time");
    }

    public static boolean getMetricsTime() {
        return METRICS_TIME;
    }

    public static void begin() {
        initializeOption();
        printTime("before-main");
    }

    public static void end() {
        printTime("after-main");
        printMemory();
    }

}
