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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.method.RubyUnboundMethod;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.language.loader.ByteBasedCharSequence;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;

@GenerateInline
@GenerateCached(false)
public abstract class ToCallTargetNode extends RubyBaseNode {

    public abstract RootCallTarget execute(Node node, Object executable);

    @Specialization
    static RootCallTarget boundMethod(RubyMethod method) {
        return method.method.getCallTarget();
    }

    @Specialization
    static RootCallTarget unboundMethod(RubyUnboundMethod method) {
        return method.method.getCallTarget();
    }

    @Specialization
    static RootCallTarget proc(RubyProc proc) {
        return proc.callTarget;
    }

    @TruffleBoundary
    @Specialization
    static RootCallTarget string(Node node, Object string) {
        var code = new TStringWithEncoding(StringOperations.asTruffleStringUncached(string),
                RubyStringLibrary.getEncodingUncached(string));
        Source source = Source.newBuilder("ruby", new ByteBasedCharSequence(code), "<parse_ast>").build();
        var parserContext = ParserContext.TOP_LEVEL;

        return getContext(node).getCodeLoader().parse(
                new RubySource(source, source.getName()),
                parserContext,
                null,
                getContext(node).getRootLexicalScope(),
                node);
    }

}
