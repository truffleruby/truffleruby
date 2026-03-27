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
package org.truffleruby.language.objects;

import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.objectspace.ObjectSpaceManager;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.methods.InternalMethod;

public abstract class AllocationTracing {

    public static void trace(RubyDynamicObject instance, Node currentNode) {
        final RubyLanguage language = RubyLanguage.get(currentNode);
        final RubyContext context = RubyContext.get(currentNode);

        truffleTracing(language, instance);

        if (context.getObjectSpaceManager().isTracing(language)) {
            traceBoundary(context, instance, currentNode);
        }
    }

    public static void traceInlined(RubyDynamicObject instance, String className, String allocatingMethod,
            AlwaysInlinedMethodNode node) {
        final RubyLanguage language = RubyLanguage.get(node);
        final RubyContext context = RubyContext.get(node);

        truffleTracing(language, instance);

        if (context.getObjectSpaceManager().isTracing(language)) {
            traceInlineBoundary(context, instance, className, allocatingMethod, node);
        }
    }

    private static void truffleTracing(RubyLanguage language, RubyDynamicObject instance) {
        CompilerAsserts.partialEvaluationConstant(language);

        final AllocationReporter allocationReporter = language.getAllocationReporter();
        if (allocationReporter.isActive()) {
            allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
            allocationReporter.onReturnValue(instance, 0, AllocationReporter.SIZE_UNKNOWN);
        }
    }

    @TruffleBoundary
    private static void traceBoundary(RubyContext context, RubyDynamicObject object, Node currentNode) {
        final ObjectSpaceManager objectSpaceManager = context.getObjectSpaceManager();
        if (!objectSpaceManager.isTracingPaused()) {
            objectSpaceManager.setTracingPaused(true);
            try {
                callTraceAllocation(context, object, currentNode);
            } finally {
                objectSpaceManager.setTracingPaused(false);
            }
        }
    }

    @TruffleBoundary
    private static void traceInlineBoundary(RubyContext context, RubyDynamicObject instance, String className,
            String allocatingMethod, Node node) {
        final ObjectSpaceManager objectSpaceManager = context.getObjectSpaceManager();
        if (!objectSpaceManager.isTracingPaused()) {
            objectSpaceManager.setTracingPaused(true);
            try {
                callTraceInlineAllocation(context, instance, className, allocatingMethod, node);
            } finally {
                objectSpaceManager.setTracingPaused(false);
            }
        }
    }

    @TruffleBoundary
    private static void callTraceAllocation(RubyContext context, RubyDynamicObject object, Node currentNode) {
        final SourceSection allocatingSourceSection = context
                .getCallStack()
                .getTopMostUserSourceSection(currentNode.getEncapsulatingSourceSection());

        final Frame allocatingFrame = context.getCallStack().getCurrentRubyFrame(FrameAccess.READ_ONLY);

        final InternalMethod method = RubyArguments.getMethod(allocatingFrame);
        final LexicalScope lexicalScope = method.getLexicalScope();
        final RubyModule module = lexicalScope.getLiveModule();
        final String className;
        if (lexicalScope == context.getRootLexicalScope()) {
            className = "";
        } else if (module == null) {
            className = "";
        } else {
            className = module.fields.getName();
        }

        storeAllocationTrace(context, object, allocatingSourceSection, className, method.getName());
    }

    @TruffleBoundary
    private static void callTraceInlineAllocation(RubyContext context, RubyDynamicObject instance, String className,
            String allocatingMethod, Node node) {
        final SourceSection allocatingSourceSection = context
                .getCallStack()
                .getTopMostUserSourceSection(node.getEncapsulatingSourceSection());
        storeAllocationTrace(context, instance, allocatingSourceSection, className, allocatingMethod);
    }

    public static final class AllocationTrace {
        public final String className;
        public final String allocatingMethod;
        public final SourceSection allocatingSourceSection;
        public final int gcGeneration;
        public final int tracingGeneration;

        private AllocationTrace(
                String className,
                String allocatingMethod,
                SourceSection allocatingSourceSection,
                int gcGeneration,
                int tracingGeneration) {
            this.className = className;
            this.allocatingMethod = allocatingMethod;
            this.allocatingSourceSection = allocatingSourceSection;
            this.gcGeneration = gcGeneration;
            this.tracingGeneration = tracingGeneration;
        }
    }

    private static void storeAllocationTrace(RubyContext context, RubyDynamicObject object,
            SourceSection allocatingSourceSection, String className, String allocatingMethod) {
        final AllocationTrace trace = new AllocationTrace(
                className,
                allocatingMethod,
                allocatingSourceSection,
                ObjectSpaceManager.getCollectionCount(),
                context.getObjectSpaceManager().getTracingGeneration());

        // The object was just allocated and is not published/shared yet
        DynamicObjectLibrary.getUncached().put(object, Layouts.ALLOCATION_TRACE_IDENTIFIER, trace);
    }

}
