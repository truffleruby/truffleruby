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
package org.truffleruby.core.regexp;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.AsTruffleStringNode;
import org.truffleruby.core.cast.ToSNode;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.regexp.InterpolatedRegexpNodeGen.RegexpBuilderNodeGen;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.language.PerformanceWarningNode;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.DeferredRaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.language.library.RubyStringLibrary;

public abstract class InterpolatedRegexpNode extends RubyContextSourceNode {
    private final @Children ToSNode[] children;
    /** initial encoding to start encodings negotiation */
    public final RubyEncoding encoding;
    public final RegexpOptions options;

    public InterpolatedRegexpNode(RubyEncoding encoding, RegexpOptions options, ToSNode[] children) {
        this.encoding = encoding;
        this.options = options;
        this.children = children;
    }

    @Specialization
    RubyRegexp interpolatedRegexp(VirtualFrame frame,
            @Bind Node node,
            @Cached("create(encoding, options)") RegexpBuilderNode builderNode,
            @Cached AsTruffleStringNode asTruffleStringNode,
            @Cached RubyStringLibrary libString) {
        return builderNode.execute(childrenToStrings(frame, node, libString, asTruffleStringNode));
    }

    @ExplodeLoop
    protected TStringWithEncoding[] childrenToStrings(VirtualFrame frame, Node node, RubyStringLibrary libString,
            AsTruffleStringNode asTruffleStringNode) {
        TStringWithEncoding[] values = new TStringWithEncoding[children.length];
        for (int i = 0; i < children.length; i++) {
            final Object value = children[i].execute(frame);
            values[i] = new TStringWithEncoding(asTruffleStringNode,
                    libString.getTString(node, value),
                    libString.getEncoding(node, value));
        }
        return values;
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = InterpolatedRegexpNodeGen.create(
                encoding,
                options,
                cloneUninitialized(children));
        return copy.copyFlags(this);
    }

    protected static ToSNode[] cloneUninitialized(ToSNode[] nodes) {
        ToSNode[] copies = new ToSNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copies[i] = (ToSNode) nodes[i].cloneUninitialized();
        }
        return copies;
    }

    public abstract static class RegexpBuilderNode extends RubyBaseNode {

        @Child private TruffleString.EqualNode equalNode = TruffleString.EqualNode.create();
        private final RubyEncoding encoding;
        private final RegexpOptions options;

        @NeverDefault
        public static RegexpBuilderNode create(RubyEncoding encoding, RegexpOptions options) {
            return RegexpBuilderNodeGen.create(encoding, options);
        }

        public RegexpBuilderNode(RubyEncoding encoding, RegexpOptions options) {
            this.encoding = encoding;
            this.options = options;
        }

        public abstract RubyRegexp execute(TStringWithEncoding[] parts);

        @Specialization(guards = "tstringsWithEncodingsMatch(cachedParts, parts)", limit = "getDefaultCacheLimit()")
        RubyRegexp fast(TStringWithEncoding[] parts,
                @Cached(value = "parts", dimensions = 1) TStringWithEncoding[] cachedParts,
                @Cached("createRegexp(cachedParts)") RubyRegexp regexp) {
            return regexp;
        }

        @Specialization(replaces = "fast")
        RubyRegexp slow(TStringWithEncoding[] parts,
                @Cached PerformanceWarningNode performanceWarningNode) {
            performanceWarningNode.warn(
                    "unstable interpolated regexps cause deoptimization loops which hurt performance significantly, avoid creating regexps dynamically where possible or cache them to fix this");
            return createRegexp(parts);
        }

        @ExplodeLoop
        protected boolean tstringsWithEncodingsMatch(TStringWithEncoding[] a, TStringWithEncoding[] b) {
            for (int i = 0; i < a.length; i++) {
                var aEncoding = a[i].encoding;
                if (aEncoding != b[i].encoding) {
                    return false;
                }
                if (!equalNode.execute(a[i].tstring, b[i].tstring, aEncoding.tencoding)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        protected RubyRegexp createRegexp(TStringWithEncoding[] strings) {
            // initial encoding is represented as a leading "" string in this encoding
            TStringWithEncoding[] stringsWithPrefix = new TStringWithEncoding[1 + strings.length];
            stringsWithPrefix[0] = new TStringWithEncoding(encoding.tencoding.getEmpty(), encoding);
            System.arraycopy(strings, 0, stringsWithPrefix, 1, strings.length);

            try {
                var preprocessed = ClassicRegexp.preprocessDRegexp(getContext(), stringsWithPrefix, options);
                return RubyRegexp.create(getLanguage(), preprocessed.tstring, preprocessed.encoding, options, this);
            } catch (DeferredRaiseException dre) {
                throw dre.getException(getContext());
            }
        }

    }

}
