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
package org.truffleruby.language.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.parser.MagicCommentParser;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/*
 * Loads the main script, whether it comes from an argument, standard in, or a file.
 */
public final class MainLoader {

    private final RubyContext context;
    private final RubyLanguage language;

    public MainLoader(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
    }

    public RubySource loadFromCommandLineArgument(String code) {
        var sourceCode = new TStringWithEncoding(TStringUtils.fromJavaString(code, Encodings.UTF_8), Encodings.UTF_8);
        final Source source = Source
                .newBuilder(TruffleRuby.LANGUAGE_ID, new ByteBasedCharSequence(sourceCode), "-e")
                .option("ruby.MainScript", "true")
                .build();
        return new RubySource(source, "-e");
    }

    public RubySource loadFromStandardIn(Node currentNode, String path) throws IOException {
        byte[] sourceBytes = readAllOfStandardIn();
        var sourceTString = transformScript(currentNode, path, sourceBytes);

        final Source source = Source
                .newBuilder(TruffleRuby.LANGUAGE_ID, new ByteBasedCharSequence(sourceTString), path)
                .option("ruby.MainScript", "true")
                .build();
        return new RubySource(source, path, sourceTString);
    }

    private TStringWithEncoding transformScript(Node currentNode, String path, byte[] sourceBytes) {
        final EmbeddedScript embeddedScript = new EmbeddedScript(context);

        if (embeddedScript.shouldTransform(sourceBytes)) {
            sourceBytes = embeddedScript.transformForExecution(currentNode, sourceBytes, path);
        }

        return MagicCommentParser.createSourceTStringBasedOnMagicEncodingComment(sourceBytes, Encodings.UTF_8);
    }

    private byte[] readAllOfStandardIn() throws IOException {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(4096);

        final byte[] buffer = new byte[4096];

        while (true) {
            final int read = System.in.read(buffer);

            if (read == -1) {
                break;
            }

            byteStream.write(buffer, 0, read);
        }

        return byteStream.toByteArray();
    }

    public RubySource loadFromFile(Env env, Node currentNode, String mainPath) throws IOException {
        final FileLoader fileLoader = new FileLoader(context, language);

        final TruffleFile file = env.getPublicTruffleFile(mainPath);
        FileLoader.ensureReadable(context, file, currentNode);

        /* We read the file's bytes ourselves because the lexer works on bytes and Truffle only gives us a CharSequence.
         * We could convert the CharSequence back to bytes, but that's more expensive than just reading the bytes once
         * and pass them down to the lexer and to the Source. */

        byte[] sourceBytes = file.readAllBytes();
        var sourceTString = transformScript(currentNode, mainPath, sourceBytes);

        final Source mainSource = fileLoader.buildSource(file, mainPath, sourceTString, false, true);

        context.getFeatureLoader().setMainScript(mainSource, file.getCanonicalFile().getPath());

        return new RubySource(mainSource, mainPath, sourceTString);
    }

}
