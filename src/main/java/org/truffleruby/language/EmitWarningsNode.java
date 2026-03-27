/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.RubyContext;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.parser.RubyDeferredWarnings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class EmitWarningsNode extends RubyContextSourceNode {

    public final RubyDeferredWarnings warnings;

    public EmitWarningsNode(RubyDeferredWarnings warnings) {
        this.warnings = warnings;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyContext context = getContext();
        printWarnings(context);
        return nil;
    }

    public void printWarnings(RubyContext context) {
        printWarnings(context, warnings);
    }

    @TruffleBoundary
    public static void printWarnings(RubyContext context, RubyDeferredWarnings warnings) {
        if (context == null) {
            for (RubyDeferredWarnings.WarningMessage warningMessage : warnings.warnings) {
                System.err.println(warningMessage.getWarningMessage());
            }
        } else {
            boolean isVerbose = context.getCoreLibrary().isVerbose();
            boolean warningsEnabled = context.getCoreLibrary().warningsEnabled();
            for (RubyDeferredWarnings.WarningMessage warningMessage : warnings.warnings) {
                if (warningMessage.verbosity == RubyDeferredWarnings.Verbosity.VERBOSE) {
                    if (isVerbose) {
                        printWarning(context, warningMessage.getWarningMessage());
                    }
                } else {
                    if (warningsEnabled) {
                        printWarning(context, warningMessage.getWarningMessage());
                    }
                }
            }
        }
    }

    private static void printWarning(RubyContext context, String message) {
        if (context.getCoreLibrary().isLoaded()) {
            final Object warning = context.getCoreLibrary().warningModule;
            final RubyString messageString = StringOperations.createUTF8String(context, context.getLanguageSlow(),
                    message);

            DispatchNode.getUncached().call(warning, "warn", messageString);
        } else {
            try {
                context.getEnvErrStream().write(message.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RaiseException(context, context.getCoreExceptions().ioError(e, null));
            }
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new EmitWarningsNode(warnings);
        return copy.copyFlags(this);
    }

}
