/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.function.Function;
import java.util.function.Predicate;

import org.truffleruby.RubyContext;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.collections.Memo;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.backtrace.InternalRootNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public final class CallStackManager {

    private final RubyContext context;

    public CallStackManager(RubyContext context) {
        this.context = context;
    }

    // Frame

    @TruffleBoundary
    public Frame getCurrentFrame(FrameAccess frameAccess) {
        return iterateFrames(0, f -> true, f -> f.getFrame(frameAccess));
    }

    @TruffleBoundary
    public Frame getCurrentRubyFrame(FrameAccess frameAccess) {
        return iterateFrames(0, f -> isRubyFrame(f.getFrame(FrameAccess.READ_ONLY)), f -> f.getFrame(frameAccess));
    }

    @TruffleBoundary
    public Frame getCallerFrame(FrameAccess frameAccess) {
        // System.err.printf("Getting a caller frame...\n");
        // new Error().printStackTrace();
        return getCallerFrame(f -> isRubyFrame(f.getFrame(FrameAccess.READ_ONLY)), frameAccess);
    }

    @TruffleBoundary
    public Frame getNonJavaCoreCallerFrame(FrameAccess frameAccess) {
        return getCallerFrame(f -> {
            final Frame frame = f.getFrame(FrameAccess.READ_ONLY);
            return isRubyFrame(frame) && !isJavaCore(tryGetMethod(frame));
        }, frameAccess);
    }

    @TruffleBoundary
    public <R> R iterateFrameNotInModules(Object[] modules, Function<FrameInstance, R> action) {
        final Memo<Boolean> skippedFirstFrameFound = new Memo<>(false);

        return iterateFrames(1, frameInstance -> {
            final InternalMethod method = tryGetMethod(frameInstance.getFrame(FrameAccess.READ_ONLY));
            if (method != null && !ArrayUtils.contains(modules, method.getDeclaringModule())) {
                if (skippedFirstFrameFound.get()) {
                    return true;
                }
                skippedFirstFrameFound.set(true);
            }
            return false;
        }, action);
    }

    // Node

    @TruffleBoundary
    public Node getCallerNode() {
        return getCallerNode(1, true);
    }

    @TruffleBoundary
    public Node getCallerNode(int skip, boolean onlyRubyFrames) {
        return iterateFrames(skip, frameInstance -> {
            if (onlyRubyFrames) {
                return isRubyFrame(frameInstance.getFrame(FrameAccess.READ_ONLY));
            } else {
                return true;
            }
        }, f -> f.getCallNode());
    }

    // Method

    @TruffleBoundary
    public InternalMethod getCallingMethod() {
        return tryGetMethod(getCallerFrame(FrameAccess.READ_ONLY));
    }

    // SourceSection

    @TruffleBoundary
    public SourceSection getTopMostUserSourceSection(SourceSection encapsulatingSourceSection) {
        if (BacktraceFormatter.isUserSourceSection(encapsulatingSourceSection)) {
            return encapsulatingSourceSection;
        } else {
            return getTopMostUserSourceSection();
        }
    }

    @TruffleBoundary
    public SourceSection getTopMostUserSourceSection() {
        return Truffle.getRuntime().iterateFrames(frameInstance -> {
            final Node callNode = frameInstance.getCallNode();
            if (callNode == null) {
                return null; // Go to the next frame
            }

            final SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
            if (BacktraceFormatter.isUserSourceSection(sourceSection)) {
                return sourceSection;
            } else {
                return null; // Go to the next frame
            }
        });
    }

    // Internals

    private Frame getCallerFrame(Predicate<FrameInstance> filter, FrameAccess frameAccess) {
        return iterateFrames(1, filter, f -> f.getFrame(frameAccess));
    }

    /** Does the same filtering as {@link Backtrace#getStackTrace(Throwable)} */
    @TruffleBoundary
    public void iterateFrameBindings(int skip, Function<FrameInstance, Object> action) {
        iterateFrames(skip, frameInstance -> {
            Node callNode = frameInstance.getCallNode();
            var callTarget = (RootCallTarget) frameInstance.getCallTarget();
            if (ignoreFrame(callNode, callTarget)) {
                return false;
            }
            final RootNode rootNode = callTarget.getRootNode();
            return rootNode instanceof RubyRootNode || callNode != null;
        }, action);
    }

    /** Returns action() for the first frame matching the filter, and null if none matches.
     * <p>
     * skip=0 starts at the current frame and skip=1 starts at the caller frame. */
    private <R> R iterateFrames(int skip, Predicate<FrameInstance> filter, Function<FrameInstance, R> action) {
        return Truffle.getRuntime().iterateFrames(new FilterApplyVisitor<>(skip, filter, action));
    }

    private static final class FilterApplyVisitor<R> implements FrameInstanceVisitor<R> {
        private final int skip;
        private final Predicate<FrameInstance> filter;
        private final Function<FrameInstance, R> action;
        private int skipped = 0;

        private FilterApplyVisitor(int skip, Predicate<FrameInstance> filter, Function<FrameInstance, R> action) {
            this.skip = skip;
            this.filter = filter;
            this.action = action;
        }

        @Override
        public R visitFrame(FrameInstance frameInstance) {
            if (skipped < skip) {
                skipped++;
                return null; // Go to the next frame
            }

            if (filter.test(frameInstance)) {
                return action.apply(frameInstance);
            } else {
                return null; // Go to the next frame
            }
        }
    }

    public static boolean isRubyFrame(Frame frame) {
        return tryGetMethod(frame) != null;
    }

    private static InternalMethod tryGetMethod(Frame frame) {
        return RubyArguments.tryGetMethod(frame);
    }

    public static boolean isJavaCore(InternalMethod method) {
        return method != null &&
                method.getSharedMethodInfo().getSourceSection() == CoreLibrary.JAVA_CORE_SOURCE_SECTION;
    }

    // Backtraces

    public Backtrace getBacktrace(Node currentNode) {
        return getBacktrace(currentNode, 0, null);
    }

    public Backtrace getBacktrace(Node currentNode, int omit) {
        return getBacktrace(currentNode, omit, null);
    }

    public Backtrace getBacktrace(Node currentNode, int omit, Throwable javaThrowable) {
        if (context.getOptions().EXCEPTIONS_STORE_JAVA || context.getOptions().BACKTRACES_INTERLEAVE_JAVA) {
            if (javaThrowable == null) {
                javaThrowable = newException();
            }
        }

        var adoptableNode = RubyNode.getAdoptedNode(currentNode);
        return new Backtrace(adoptableNode, omit, javaThrowable);
    }

    @SuppressFBWarnings("ES")
    public boolean ignoreFrame(Node callNode, RootCallTarget callTarget) {
        // Nodes with no call node are top-level or require, which *should* appear in the backtrace.
        if (callNode == null) {
            return false;
        }

        final RootNode rootNode = callNode.getRootNode();

        if (rootNode instanceof RubyRootNode) {
            final SharedMethodInfo sharedMethodInfo = ((RubyRootNode) rootNode).getSharedMethodInfo();

            // Ignore Truffle::Boot.main and its caller
            if (context.getCoreLibrary().isTruffleBootMainMethod(sharedMethodInfo)) {
                return true;
            }
            final SourceSection sourceSection = sharedMethodInfo.getSourceSection();
            if (sourceSection != null && sourceSection.getSource().getName() == TruffleRuby.BOOT_SOURCE_NAME) {
                return true;
            }
        }

        return rootNode instanceof InternalRootNode || callNode.getEncapsulatingSourceSection() == null;

    }

    @TruffleBoundary
    private Exception newException() {
        return new Exception();
    }

}
