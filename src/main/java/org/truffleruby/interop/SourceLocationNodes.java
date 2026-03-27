/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.string.RubyString;

import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.core.thread.ThreadBacktraceLocationNodes;

@CoreModule(value = "Truffle::Interop::SourceLocation", isClass = true)
public abstract class SourceLocationNodes {

    @CoreMethod(names = "absolute_path")
    public abstract static class AbsolutePathNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        Object absolutePath(RubySourceLocation location) {
            final SourceSection sourceSection = location.sourceSection;
            if (!sourceSection.isAvailable()) {
                return coreStrings().UNKNOWN.createInstance(getContext());
            }

            return ThreadBacktraceLocationNodes.AbsolutePathNode.getAbsolutePath(sourceSection, this);
        }
    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyString path(RubySourceLocation location) {
            final SourceSection sourceSection = location.sourceSection;

            if (!sourceSection.isAvailable()) {
                return coreStrings().UNKNOWN.createInstance(getContext());
            } else {
                var path = getLanguage().getPathToTStringCache().getCachedPath(sourceSection.getSource());
                return createString(path, Encodings.UTF_8);
            }
        }
    }

    @CoreMethod(names = { "first_lineno", "lineno" })
    public abstract static class LinenoNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int lineno(RubySourceLocation location) {
            return location.sourceSection.getStartLine();
        }
    }

    @CoreMethod(names = "last_lineno")
    public abstract static class LastLinenoNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int lastLine(RubySourceLocation location) {
            return location.sourceSection.getEndLine();
        }
    }

    @CoreMethod(names = "first_column")
    public abstract static class FirstColumnNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int firstCol(RubySourceLocation location) {
            return location.sourceSection.getStartColumn();
        }
    }

    @CoreMethod(names = "last_column")
    public abstract static class LastColumnNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        int lastCol(RubySourceLocation location) {
            return location.sourceSection.getEndColumn();
        }
    }

    @CoreMethod(names = "available?")
    public abstract static class IsAvailableNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        boolean isAvailable(RubySourceLocation location) {
            return location.sourceSection.isAvailable();
        }
    }

    @CoreMethod(names = "internal?")
    public abstract static class IsInternalNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        boolean isInternal(RubySourceLocation location) {
            return location.sourceSection.getSource().isInternal();
        }
    }

    @CoreMethod(names = "language")
    public abstract static class LanguageNode extends CoreMethodArrayArgumentsNode {
        @TruffleBoundary
        @Specialization
        RubyString language(RubySourceLocation location,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return createString(fromJavaStringNode, location.sourceSection.getSource().getLanguage(),
                    Encodings.UTF_8);
        }
    }

}
