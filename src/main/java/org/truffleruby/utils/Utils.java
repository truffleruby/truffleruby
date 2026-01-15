/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.utils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/** General purpose utility functions that do not fit in other utility classes. */
public final class Utils {

    public static long longAdd(int a, int b) {
        return (long) a + (long) b;
    }

    /** Build a {@link UnsupportedOperationException} behind a {@link TruffleBoundary} so as to avoid the
     * SVM-blacklisted {@link Throwable#fillInStackTrace()}. */
    @TruffleBoundary
    public static UnsupportedOperationException unsupportedOperation(String msg) {
        return new UnsupportedOperationException(msg);
    }

    /** Build a {@link UnsupportedOperationException} behind a {@link TruffleBoundary} so as to avoid the
     * SVM-blacklisted {@link Throwable#fillInStackTrace()} and the implicit {@link String} methods. */
    @TruffleBoundary
    public static UnsupportedOperationException unsupportedOperation(Object... msgParts) {
        return new UnsupportedOperationException(concat(msgParts));
    }

    public static VarHandle getVarHandle(MethodHandles.Lookup lookup, Class<?> klass, String name, Class<?> type) {
        try {
            return lookup.findVarHandle(klass, name, type);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    /** Performs {@link Objects#equals(Object, Object)} behind a {@link TruffleBoundary} so as to avoid performance
     * warnings, since {@link Object#equals} is blacklisted. */
    @TruffleBoundary
    public static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /** Performs {@link Objects#toString(Object)} behind a {@link TruffleBoundary} so as to avoid performance warnings,
     * since {@link Object#toString()} is blacklisted. */
    @TruffleBoundary
    public static String toString(Object object) {
        return Objects.toString(object);
    }

    /** Converts the arguments to strings and concatenate them behind a {@link TruffleBoundary}, so as to avoid the
     * implicit calls to SVM-blacklisted {@link String} methods. */
    @TruffleBoundary
    public static String concat(Object one, Object two) {
        return String.valueOf(one) + two;
    }

    /** Converts the {@code parts} to strings and concatenate them behind a {@link TruffleBoundary}, so as to avoid the
     * implicit calls to SVM-blacklisted {@link String} methods. */
    @TruffleBoundary
    public static String concat(Object... parts) {
        StringBuilder builder = new StringBuilder();
        for (Object part : parts) {
            builder.append(part);
        }
        return builder.toString();
    }
}
