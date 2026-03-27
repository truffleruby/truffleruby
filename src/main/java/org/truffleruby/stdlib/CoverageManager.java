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
package org.truffleruby.stdlib;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

import org.truffleruby.RubyLanguage;
import org.truffleruby.RubyLanguage.RubySourceOptions;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.collections.ConcurrentOperations;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class CoverageManager {

    public static final class LineTag extends Tag {
    }

    public static final long NO_CODE = -1;

    private final Instrumenter instrumenter;
    private EventBinding<?> binding;
    private final Map<Source, AtomicLongArray> counters = new ConcurrentHashMap<>();

    private volatile boolean enabled;

    public CoverageManager(RubyLanguage language, Instrumenter instrumenter) {
        this.instrumenter = instrumenter;

        if (language.options.COVERAGE_GLOBAL) {
            enable(language);
        }
    }

    private int lineToIndex(int line) {
        return line - 1;
    }

    public void setLineHasCode(Source source, int line) {
        final AtomicLongArray counters = getCounters(source);
        counters.set(lineToIndex(line), 0);
    }

    private boolean getLineHasCode(Source source, int line) {
        final AtomicLongArray counters = getCounters(source);
        return counters.get(lineToIndex(line)) != NO_CODE;
    }

    @TruffleBoundary
    public synchronized void enable(RubyLanguage language) {
        if (enabled) {
            return;
        }

        binding = instrumenter.attachExecutionEventFactory(
                SourceSectionFilter
                        .newBuilder()
                        .mimeTypeIs(RubyLanguage.MIME_TYPES)
                        .sourceIs(source -> source.getOptions(language).get(RubySourceOptions.Coverage))
                        .tagIs(LineTag.class)
                        .build(),
                eventContext -> new ExecutionEventNode() {

                    @CompilationFinal private int lineNumber = -2;
                    @CompilationFinal private AtomicLongArray counters;

                    @Override
                    protected void onEnter(VirtualFrame frame) {
                        if (lineNumber == -2) { // not yet initialized
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            final SourceSection sourceSection = eventContext.getInstrumentedSourceSection();

                            if (getLineHasCode(sourceSection.getSource(), sourceSection.getStartLine())) {
                                lineNumber = lineToIndex(sourceSection.getStartLine());
                                counters = getCounters(sourceSection.getSource());
                            } else {
                                lineNumber = -1;
                            }
                            assert lineNumber != -2;
                        }

                        if (counters != null) {
                            incrementAndGet();
                        }
                    }

                    @SuppressFBWarnings("UwF")
                    @TruffleBoundary
                    private void incrementAndGet() {
                        counters.incrementAndGet(lineNumber);
                    }

                });

        enabled = true;
    }

    @TruffleBoundary
    public synchronized void disable() {
        if (!enabled) {
            return;
        }

        binding.dispose();
        counters.clear();

        enabled = false;
    }

    private AtomicLongArray getCounters(Source source) {
        return ConcurrentOperations.getOrCompute(counters, source, s -> {
            long[] initialValues = new long[s.getLineCount()];
            Arrays.fill(initialValues, NO_CODE);
            return new AtomicLongArray(initialValues);
        });
    }

    public synchronized Map<Source, long[]> getCounts() {
        if (!enabled) {
            return null;
        }

        final Map<Source, long[]> counts = new HashMap<>();

        for (Map.Entry<Source, AtomicLongArray> entry : counters.entrySet()) {
            final long[] array = new long[entry.getValue().length()];

            for (int n = 0; n < array.length; n++) {
                array[n] = entry.getValue().get(n);
            }

            counts.put(entry.getKey(), array);
        }

        return counts;
    }

    public synchronized void print(PrintStream out) {
        final int maxCountDigits = Long.toString(getMaxCount()).length();

        final String countFormat = "%" + maxCountDigits + "d";

        final char[] noCodeChars = new char[maxCountDigits];
        Arrays.fill(noCodeChars, ' ');
        noCodeChars[maxCountDigits - 1] = '-';
        final String noCodeString = new String(noCodeChars);

        for (Map.Entry<Source, AtomicLongArray> entry : counters.entrySet()) {
            out.println(RubyLanguage.getPath(entry.getKey()));

            for (int n = 0; n < entry.getValue().length(); n++) {
                // TODO CS 5-Sep-17 can we keep the line as a CharSequence rather than using toString?
                String line = entry.getKey().getCharacters(n + 1).toString();

                if (line.length() > 60) {
                    line = line.substring(0, 60);
                }

                out.print("  ");
                final long c = entry.getValue().get(n);
                if (c == NO_CODE) {
                    out.print(noCodeString);
                } else {
                    out.printf(countFormat, c);
                }
                out.printf("  %s%n", line);
            }
        }
    }

    private long getMaxCount() {
        long max = 0;

        for (Map.Entry<Source, AtomicLongArray> entry : counters.entrySet()) {
            for (int n = 0; n < entry.getValue().length(); n++) {
                max = Math.max(max, entry.getValue().get(n));
            }
        }

        return max;
    }

    public boolean isEnabled() {
        return enabled;
    }

}
