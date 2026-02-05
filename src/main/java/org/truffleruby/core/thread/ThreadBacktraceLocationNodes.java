/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.backtrace.BacktraceFormatter;

@CoreModule(value = "Thread::Backtrace::Location", isClass = true)
public abstract class ThreadBacktraceLocationNodes {

    @TruffleBoundary
    private static SourceSection getAvailableSourceSection(RubyContext context,
            RubyBacktraceLocation threadBacktraceLocation) {
        final Backtrace backtrace = threadBacktraceLocation.backtrace;
        final int activationIndex = threadBacktraceLocation.activationIndex;

        return BacktraceFormatter.nextAvailableSourceSection(backtrace.getStackTrace(), activationIndex);
    }

    @CoreMethod(names = "absolute_path")
    public abstract static class AbsolutePathNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object absolutePath(RubyBacktraceLocation threadBacktraceLocation) {
            final SourceSection sourceSection = getAvailableSourceSection(getContext(), threadBacktraceLocation);

            return getAbsolutePath(sourceSection, this);
        }

        @TruffleBoundary
        public static Object getAbsolutePath(SourceSection sourceSection, Node node) {
            var context = RubyContext.get(node);
            var language = RubyLanguage.get(node);

            if (sourceSection == null) {
                return language.coreStrings.UNKNOWN.createInstance(context);
            } else {
                final Source source = sourceSection.getSource();
                if (BacktraceFormatter.isRubyCore(language, source)) {
                    return nil;
                } else if (source.getPath() != null) { // A normal file
                    final String path = language.getSourcePath(source);
                    final String canonicalPath = context.getFeatureLoader().canonicalize(path, source);
                    var cachedTString = language.tstringCache.getTString(TStringUtils.utf8TString(canonicalPath),
                            Encodings.UTF_8);
                    return createString(node, cachedTString, Encodings.UTF_8);
                } else { // eval()
                    var cachedPath = language.getPathToTStringCache().getCachedPath(source);
                    return createString(node, cachedPath, Encodings.UTF_8);
                }
            }
        }

    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyString path(RubyBacktraceLocation threadBacktraceLocation) {
            final SourceSection sourceSection = getAvailableSourceSection(getContext(), threadBacktraceLocation);

            if (sourceSection == null) {
                return coreStrings().UNKNOWN.createInstance(getContext());
            } else {
                var path = getLanguage().getPathToTStringCache().getCachedPath(sourceSection.getSource());
                return createString(path, Encodings.UTF_8);
            }
        }

    }

    @CoreMethod(names = "label")
    public abstract static class LabelNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyString label(RubyBacktraceLocation threadBacktraceLocation,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            TruffleStackTraceElement element = threadBacktraceLocation.getTruffleStackTraceElement();

            final String label = Backtrace.labelFor(element);
            return createString(fromJavaStringNode, label, Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "base_label")
    public abstract static class BaseLabelNode extends CoreMethodArrayArgumentsNode {
        @Specialization
        RubyString label(RubyBacktraceLocation threadBacktraceLocation,
                @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            TruffleStackTraceElement element = threadBacktraceLocation.getTruffleStackTraceElement();

            final String baseLabel = Backtrace.baseLabelFor(element);
            return createString(fromJavaStringNode, baseLabel, Encodings.UTF_8);
        }
    }

    @CoreMethod(names = "lineno")
    public abstract static class LinenoNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        int lineno(RubyBacktraceLocation threadBacktraceLocation) {
            final SourceSection sourceSection = getAvailableSourceSection(getContext(), threadBacktraceLocation);

            return getLanguage().getStartLineAdjusted(sourceSection);
        }

    }

    @CoreMethod(names = "to_s")
    public abstract static class ToSNode extends CoreMethodArrayArgumentsNode {

        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();

        @Specialization
        RubyString toS(RubyBacktraceLocation threadBacktraceLocation) {
            final Backtrace backtrace = threadBacktraceLocation.backtrace;
            final int index = threadBacktraceLocation.activationIndex;

            final String description = getContext()
                    .getUserBacktraceFormatter()
                    .formatLine(backtrace.getStackTrace(), index, null);
            return createString(fromJavaStringNode, description, Encodings.UTF_8);
        }

    }

}
