/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2019-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.cext.ValueWrapper;
import org.truffleruby.core.MarkingService.ExtensionCallStack;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class MarkingServiceNodes {

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    public abstract static class KeepAliveNode extends RubyBaseNode {

        public abstract void execute(Node node, ValueWrapper object);

        @Specialization
        static void keepObject(Node node, ValueWrapper object,
                @Bind("getStack(node)") ExtensionCallStack stack,
                @Cached InlinedConditionProfile sameObjectProfile,
                @Cached InlinedConditionProfile growProfile) {
            final MarkingService.ExtensionCallStackEntry entry = stack.current;
            final ValueWrapper[] preservedObjects = entry.preservedObjects;
            final int count = entry.preservedObjectsCount;
            if (sameObjectProfile.profile(node, count > 0 && preservedObjects[count - 1] == object)) {
                return; // the common case of keeping the same wrapper alive repeatedly during a call
            }
            if (growProfile.profile(node, count == preservedObjects.length)) {
                grow(entry, object);
            } else {
                preservedObjects[count] = object;
                entry.preservedObjectsCount = count + 1;
            }
        }

        @TruffleBoundary
        private static void grow(MarkingService.ExtensionCallStackEntry entry, ValueWrapper object) {
            final ValueWrapper[] grown = Arrays.copyOf(entry.preservedObjects, entry.preservedObjects.length * 2);
            grown[entry.preservedObjectsCount] = object;
            entry.preservedObjects = grown;
            entry.preservedObjectsCount++;
        }

        @NonIdempotent
        protected static ExtensionCallStack getStack(Node node) {
            return getLanguage(node).getCurrentThread().getCurrentFiber().extensionCallStack;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class RunMarkOnExitNode extends RubyBaseNode {

        public abstract void execute(Node node, ExtensionCallStack stack);

        @Specialization(guards = "!stack.hasMarkObjects()")
        static void nothingToMark(ExtensionCallStack stack) {
            // Do nothing.
        }

        @Specialization(guards = "stack.hasSingleMarkObject()")
        static void markSingleObject(Node node, ExtensionCallStack stack,
                @Cached(inline = false) @Shared DispatchNode callNode) {
            ValueWrapper value = stack.getSingleMarkObject();
            callNode.call(getContext(node).getCoreLibrary().truffleCExtModule, "run_marker", value.getObject());
        }

        @TruffleBoundary
        @Specialization(guards = { "stack.hasMarkObjects()", "!stack.hasSingleMarkObject()" })
        static void marksToRun(Node node, ExtensionCallStack stack,
                @Cached(inline = false) @Shared DispatchNode callNode) {
            // Run the markers...
            var valuesForMarking = stack.getMarkOnExitObjects();
            // Push a new stack frame because we should
            // mutate the list while iterating, and we
            // don't know what the mark routine might do.
            stack.push(false, nil, nil);
            try {
                for (var value : valuesForMarking) {
                    callNode.call(getContext(node).getCoreLibrary().truffleCExtModule, "run_marker", value.getObject());
                }
            } finally {
                stack.pop();
            }
        }
    }
}
