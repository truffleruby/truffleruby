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
package org.truffleruby.language.backtrace;

import java.util.ArrayList;
import java.util.List;

public final class BacktraceInterleaver {

    public static List<String> interleave(List<String> rubyBacktrace, StackTraceElement[] javaStacktrace, int omitted) {
        final List<String> interleaved = new ArrayList<>();

        int javaIndex = 0;
        int rubyIndex = 0;
        int toOmit = omitted;

        while (javaIndex < javaStacktrace.length || rubyIndex < rubyBacktrace.size()) {
            if (javaIndex >= javaStacktrace.length ||
                    (isCallBoundary(javaStacktrace[javaIndex]) && rubyIndex < rubyBacktrace.size())) {
                if (toOmit == 0) {
                    interleaved.add(rubyBacktrace.get(rubyIndex));
                    rubyIndex++;
                } else {
                    toOmit--;
                }
            }

            if (javaIndex < javaStacktrace.length) {
                interleaved.add(String.format("\t\t%s", javaStacktrace[javaIndex]));

                if (isIntoRuby(javaStacktrace, javaIndex)) {
                    interleaved.add("\t\t\tforeign call goes into Ruby");
                }

                if (isForeignCall(javaStacktrace[javaIndex])) {
                    interleaved.add("\t\t\tforeign call being made");
                }

                javaIndex++;
            }
        }

        return interleaved;
    }


    public static boolean isCallBoundary(StackTraceElement element) {
        // GR-47041: we need com.oracle.truffle.api.impl.Accessor.RuntimeSupport.isGuestCallStackFrame but it's not public
        String className = element.getClassName();
        String methodName = element.getMethodName();
        return (className.equals("com.oracle.truffle.runtime.OptimizedCallTarget") &&
                methodName.equals("executeRootNode")) ||
                (className.equals("com.oracle.truffle.api.impl.DefaultCallTarget") &&
                        (methodName.equals("callDirectOrIndirect") || methodName.equals("call")));
    }

    private static boolean isIntoRuby(StackTraceElement[] elements, int index) {
        if (index + 1 >= elements.length) {
            return false;
        }

        return elements[index].toString().startsWith("org.truffleruby.interop.RubyMessageResolutionForeign") &&
                !elements[index + 1].toString().startsWith("org.truffleruby.interop.RubyMessageResolutionForeign");
    }

    private static boolean isForeignCall(StackTraceElement element) {
        return element.toString().startsWith("com.oracle.truffle.api.interop.ForeignAccess.send");
    }

}
