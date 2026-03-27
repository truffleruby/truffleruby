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
package org.truffleruby.platform;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.truffleruby.RubyContext;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public final class Signals {

    /** This is used instead of {@link SignalHandler#SIG_IGN} as {@code Signal.handle(sig, anyHandler)} seems to no
     * longer work after {@code Signal.handle(sig, SIG_IGN)} on JVM (on Native Image it seems fine). See
     * https://bugs.openjdk.java.net/browse/JDK-8262905 */
    private static final SignalHandler IGNORE = sig -> {
    };

    // Use String and not Signal as key to work around SVM not allowing new Signal("PROF")
    /** Default SignalHandlers for the JVM */
    private static final ConcurrentMap<String, SignalHandler> DEFAULT_HANDLERS = new ConcurrentHashMap<>();

    public static void registerHandler(RubyContext context, SignalHandler newHandler, String signalName,
            boolean isRubyDefaultHandler) {
        final Signal signal = new Signal(signalName);
        final SignalHandler oldHandler = Signal.handle(signal, newHandler);
        DEFAULT_HANDLERS.putIfAbsent(signalName, oldHandler);
        if (isRubyDefaultHandler) {
            context.defaultRubySignalHandlers.putIfAbsent(signalName, newHandler);
        }
    }

    public static void registerIgnoreHandler(String signalName) {
        final Signal signal = new Signal(signalName);
        final SignalHandler oldHandler = Signal.handle(signal, IGNORE);
        DEFAULT_HANDLERS.putIfAbsent(signalName, oldHandler);
    }

    public static boolean restoreDefaultHandler(String signalName) {
        final SignalHandler defaultHandler = DEFAULT_HANDLERS.get(signalName);
        if (defaultHandler == null) {
            // it is already the default signal
            return false;
        } else {
            final Signal signal = new Signal(signalName);
            Signal.handle(signal, defaultHandler);
            return true;
        }
    }

    public static boolean restoreRubyDefaultHandler(RubyContext context, String signalName) {
        SignalHandler defaultHandler = context.defaultRubySignalHandlers.get(signalName);
        if (defaultHandler == null) {
            defaultHandler = DEFAULT_HANDLERS.get(signalName);
        }

        if (defaultHandler == null) {
            // it is already the default signal
            return false;
        } else {
            final Signal signal = new Signal(signalName);
            Signal.handle(signal, defaultHandler);
            return true;
        }
    }

    public static void restoreSystemHandler(String signalName) {
        final Signal signal = new Signal(signalName);
        Signal.handle(signal, SignalHandler.SIG_DFL);
    }

    public static void restoreDefaultHandlers() {
        for (String signalName : new ArrayList<>(DEFAULT_HANDLERS.keySet())) {
            restoreDefaultHandler(signalName);
        }
    }

}
