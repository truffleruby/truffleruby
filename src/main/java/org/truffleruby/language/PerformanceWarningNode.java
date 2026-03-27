/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.language.globals.ReadSimpleGlobalVariableNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PerformanceWarningNode extends RubyBaseNode {

    @SuppressWarnings("serial")
    protected static final class Warned extends ControlFlowException {
    }

    @Child ReadSimpleGlobalVariableNode readVerboseNode = ReadSimpleGlobalVariableNode.create("$VERBOSE");

    public final void warn(String message) {
        executeWarn(message);
    }

    protected abstract void executeWarn(String message);

    @Specialization(rewriteOn = Warned.class)
    void warnOnce(String message) throws Warned {
        // The message should be a constant, because we don't want to do anything expensive to create it
        CompilerAsserts.partialEvaluationConstant(message);

        // Do not warn if $VERBOSE is nil
        if (readVerboseNode.execute() == nil) {
            return;
        }

        // Only warn if Warning[:performance] is true
        if (!getContext().getWarningCategoryPerformance().get()) {
            return;
        }

        log(getLanguage(), getContext(), message, this);
        throw new Warned();
    }

    @Specialization(replaces = "warnOnce")
    void doNotWarn(String message) {
        // do nothing
    }

    @TruffleBoundary
    public static void warn(RubyContext context, String message, Node currentNode) {
        // Do not warn if $VERBOSE is nil
        if (!context.getCoreLibrary().warningsEnabled()) {
            return;
        }

        // Only warn if Warning[:performance] is true
        if (!context.getWarningCategoryPerformance().get()) {
            return;
        }

        log(context.getLanguageSlow(), context, message, currentNode);
    }

    @TruffleBoundary
    private static void log(RubyLanguage language, RubyContext context, String message, Node currentNode) {
        // We want the topmost user source section, as otherwise lots of warnings will come from the same core methods
        final SourceSection userSourceSection = context.getCallStack()
                .getTopMostUserSourceSection(currentNode.getEncapsulatingSourceSection());

        final String displayedWarning = String.format(
                "%s: warning: %s%n",
                language.fileLine(userSourceSection),
                message);

        if (DISPLAYED_WARNINGS.add(displayedWarning)) {
            var messageString = createString(currentNode, TruffleString.FromJavaStringNode.getUncached(),
                    displayedWarning,
                    Encodings.US_ASCII);
            var warningModule = context.getCoreLibrary().truffleWarningOperationsModule;
            RubyContext.send(currentNode, warningModule, "performance_warning", messageString);
        }
    }

    // The remembered set of displayed warnings is global to the VM
    private static final Set<String> DISPLAYED_WARNINGS = ConcurrentHashMap.newKeySet();
}
