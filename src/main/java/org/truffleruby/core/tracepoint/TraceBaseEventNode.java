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
package org.truffleruby.core.tracepoint;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.source.Source;

public class TraceBaseEventNode extends ExecutionEventNode {

    protected final RubyContext context;
    private final RubyLanguage language;
    protected final EventContext eventContext;

    @CompilationFinal private RubyString file;
    @CompilationFinal private int line;

    @Child private CallBlockNode yieldNode;

    public TraceBaseEventNode(RubyContext context, RubyLanguage language, EventContext eventContext) {
        this.context = context;
        this.language = language;
        this.eventContext = eventContext;
    }

    protected RubyString getFile() {
        if (file == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            final Source source = eventContext.getInstrumentedSourceSection().getSource();
            file = new RubyString(context.getCoreLibrary().stringClass, language.stringShape, false,
                    language.getPathToTStringCache().getCachedPath(source), Encodings.UTF_8);
        }
        return file;
    }

    protected int getLine() {
        if (line == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            line = language.getStartLineAdjusted(eventContext.getInstrumentedSourceSection());
        }
        return line;
    }

    protected Object callBlock(RubyProc block, Object... arguments) {
        if (yieldNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            yieldNode = insert(CallBlockNode.create());
        }

        return yieldNode.yieldCached(block, arguments);
    }

}
