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
package org.truffleruby.signal;

public abstract class LibRubySignal {

    @SuppressWarnings("restricted")
    public static void loadLibrary(String rubyHome, String libSuffix) {
        final String path = rubyHome + "/lib/cext/librubysignal" + libSuffix;
        System.load(path);
    }

    public static native void warnStackOverflowError();

    public static native void warnOutOfMemoryError();

    public static native void setupLocale();

    public static native void setupLocaleOnlyCTYPE();

    public static native int setupSIGVTALRMEmptySignalHandler();

    public static native long threadID();

    public static native int sendSIGVTALRMToThread(long thread);

    public static native long getNativeThreadID();

    public static native void restoreSystemHandlerAndRaise(int signalNumber);

    public static native void executeUnblockFunction(long function, long argument);

}
