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
package org.truffleruby.language.loader;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.CannotConvertBinaryRubyStringToJavaString;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.MagicCommentParser;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

public abstract class EvalLoader {

    @TruffleBoundary
    public static RubySource createEvalSource(RubyContext context, AbstractTruffleString codeTString,
            RubyEncoding encoding, String method, String file, int line, Node currentNode) {
        var code = new TStringWithEncoding(codeTString.asTruffleStringUncached(encoding.tencoding), encoding);

        var sourceTString = MagicCommentParser.createSourceTStringBasedOnMagicEncodingComment(code, code.encoding);
        var sourceEncoding = sourceTString.encoding;

        if (!sourceEncoding.isAsciiCompatible) {
            throw new RaiseException(context, context.getCoreExceptions()
                    .argumentError(sourceEncoding + " is not ASCII compatible", currentNode));
        }

        try {
            sourceTString.toJavaStringOrThrow();
        } catch (CannotConvertBinaryRubyStringToJavaString e) {
            // In such a case, we have no way to build a Java String for the Truffle Source that
            // could accurately represent the source string, so we throw an error.
            final String message = file + ":" + line + ": cannot " + method +
                    "() a String with binary encoding, with no magic encoding comment and containing a non-US-ASCII character: \\x" +
                    String.format("%02X", e.getNonAsciiCharacter());
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().syntaxErrorAlreadyWithFileLine(
                            message,
                            currentNode,
                            currentNode.getEncapsulatingSourceSection()));
        }

        final Source source = Source.newBuilder(TruffleRuby.LANGUAGE_ID, new ByteBasedCharSequence(sourceTString), file)
                .option("ruby.LineOffset", Integer.toString(line - 1))
                .build();

        return new RubySource(source, file, sourceTString, true, line - 1);
    }

}
