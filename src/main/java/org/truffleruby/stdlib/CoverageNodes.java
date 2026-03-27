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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;

@CoreModule("Truffle::Coverage")
public abstract class CoverageNodes {

    @CoreMethod(names = "enable", onSingleton = true)
    public abstract static class CoverageEnableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object enable() {
            getLanguage().coverageManager.enable(getLanguage());
            return nil;
        }

    }

    @CoreMethod(names = "disable", onSingleton = true)
    public abstract static class CoverageDisableNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object disable() {
            getLanguage().coverageManager.disable();
            return nil;
        }

    }

    @CoreMethod(names = "result_array", onSingleton = true)
    public abstract static class CoverageResultNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @TruffleBoundary
        @Specialization
        RubyArray resultArray() {

            final Map<Source, long[]> counts = getLanguage().coverageManager.getCounts();

            if (counts == null) {
                throw new RaiseException(getContext(), coreExceptions().runtimeErrorCoverageNotEnabled(this));
            }

            Map<String, RubyArray> results = new HashMap<>();
            for (Map.Entry<Source, long[]> source : counts.entrySet()) {
                final long[] countsArray = source.getValue();

                final Object[] countsStore = new Object[countsArray.length];

                for (int n = 0; n < countsArray.length; n++) {
                    if (countsArray[n] == CoverageManager.NO_CODE) {
                        countsStore[n] = nil;
                    } else {
                        countsStore[n] = countsArray[n];
                    }
                }

                final String path = RubyLanguage.getPath(source.getKey());
                assert !results.containsKey(path) : "path already exists in coverage results";
                results.put(path, createArray(new Object[]{
                        createString(fromJavaStringNode,
                                path,
                                Encodings.UTF_8),
                        createArray(countsStore)
                }));
            }

            return createArray(results.values().toArray());
        }

    }

    @CoreMethod(names = "enabled?", onSingleton = true)
    public abstract static class CoverageEnabledNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean coverageEnabled() {
            return getLanguage().coverageManager.isEnabled();
        }
    }

}
