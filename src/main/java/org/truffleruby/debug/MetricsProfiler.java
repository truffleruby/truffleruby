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
package org.truffleruby.debug;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.shared.options.Profile;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;

public final class MetricsProfiler {

    public enum MetricKind {
        SEARCHING("searching", false),
        PARSING("parsing", false),
        TRANSLATING("translating", false),
        REQUIRE("require", true),
        EXECUTE("execute", true);

        private final String name;
        public final boolean nested;

        MetricKind(String name, boolean nested) {
            this.name = name;
            this.nested = nested;
        }

        @Override
        public String toString() {
            return name;
        }

        public static MetricKind[] VALUES = values();
    }

    private final RubyLanguage language;
    private final RubyContext context;
    /** We need to use the same CallTarget for the same name to appear as one entry to the profiler */
    private final Map<String, RootCallTarget> summaryCallTargets = new ConcurrentHashMap<>();
    public final AtomicLongArray totals = new AtomicLongArray(MetricKind.VALUES.length);

    public MetricsProfiler(RubyLanguage language, RubyContext context) {
        this.language = language;
        this.context = context;
    }

    @TruffleBoundary
    public <T> T callWithMetrics(MetricKind metricKind, String feature, Supplier<T> supplier) {
        Profile metricsProfileRequire = context.getOptions().METRICS_PROFILE_REQUIRE;
        if (metricsProfileRequire != Profile.NONE) {
            // Ignore nested metrics as their totals wouldn't make sense as they would sum overlapping durations
            if (metricsProfileRequire == Profile.TOTAL && !metricKind.nested) {
                T result;
                long before = System.nanoTime();
                try {
                    result = supplier.get();
                } finally {
                    long after = System.nanoTime();
                    long duration = after - before;
                    totals.addAndGet(metricKind.ordinal(), duration);
                }
                return result;
            }

            final RootCallTarget callTarget = getCallTarget(metricKind, feature);
            return callAndCast(callTarget, supplier);
        } else {
            return supplier.get();
        }
    }

    private <T> RootCallTarget getCallTarget(MetricKind metricKind, String feature) {
        final String name;
        if (context.getOptions().METRICS_PROFILE_REQUIRE == Profile.DETAIL) {
            name = "metrics " + metricKind + " " + language.getPathRelativeToHome(feature);
            return newCallTarget(name);
        } else {
            name = "metrics " + metricKind;
            return ConcurrentOperations.getOrCompute(summaryCallTargets, name, this::newCallTarget);
        }
    }

    private <T> RootCallTarget newCallTarget(String name) {
        final MetricsBodyNode<T> body = new MetricsBodyNode<>();
        final MetricsInternalRootNode rootNode = new MetricsInternalRootNode(language, name, body);
        return rootNode.getCallTarget();
    }

    @SuppressWarnings("unchecked")
    private static <T> T callAndCast(RootCallTarget callTarget, Supplier<T> supplier) {
        return (T) callTarget.call(supplier);
    }

}
