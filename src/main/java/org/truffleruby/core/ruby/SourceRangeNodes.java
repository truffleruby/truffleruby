/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.ruby;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.thread.ThreadBacktraceLocationNodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

/** Similar to {@link org.truffleruby.interop.SourceLocationNodes} but there are several differences including method
 * names and return values for columns */
@CoreModule(value = "Ruby::SourceRange", isClass = true)
public abstract class SourceRangeNodes {

    @CoreMethod(names = "absolute_path")
    public abstract static class AbsolutePathNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object absolutePath(RubySourceRange sourceRange) {
            final SourceSection sourceSection = sourceRange.sourceSection;
            return ThreadBacktraceLocationNodes.AbsolutePathNode.getAbsolutePath(sourceSection, true, this);
        }
    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyString path(RubySourceRange sourceRange) {
            final SourceSection sourceSection = sourceRange.sourceSection;
            var path = getLanguage().getPathToTStringCache().getCachedPath(sourceSection.getSource());
            return createString(path, Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "start_line")
    public abstract static class StartLineNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int startLine(RubySourceRange sourceRange) {
            return getLanguage().getStartLineAdjusted(sourceRange.sourceSection);
        }
    }

    @CoreMethod(names = "end_line")
    public abstract static class EndLineNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int endLine(RubySourceRange sourceRange) {
            return getLanguage().getEndLineAdjusted(sourceRange.sourceSection);
        }
    }

    @CoreMethod(names = "start_column")
    public abstract static class StartColumnNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int startColumn(RubySourceRange sourceRange) {
            // The 0-indexed byte column, so we must - 1
            return sourceRange.sourceSection.getStartColumn() - 1;
        }
    }

    @CoreMethod(names = "end_column")
    public abstract static class EndColumnNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int endColumn(RubySourceRange sourceRange) {
            // The 0-indexed byte column (so -1), exclusive (+1)
            return sourceRange.sourceSection.getEndColumn();
        }
    }

}
