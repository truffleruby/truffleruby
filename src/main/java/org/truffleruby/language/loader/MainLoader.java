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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.RubyEncoding;
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
    private final RubyEncoding mainScriptEncoding;

    public MainLoader(RubyContext context, RubyLanguage language, RubyEncoding mainScriptEncoding) {
        this.context = context;
        this.language = language;
        this.mainScriptEncoding = mainScriptEncoding;
    }

    public RubySource loadFromCommandLineArgument(Node currentNode, String code) {
        byte[] sourceBytes = code.getBytes(StandardCharsets.UTF_8);
        return loadFromBytes(currentNode, "-e", sourceBytes);
    }

    public RubySource loadFromStandardIn(Node currentNode) throws IOException {
        byte[] sourceBytes = System.in.readAllBytes();
        return loadFromBytes(currentNode, "-", sourceBytes);
    }

    private RubySource loadFromBytes(Node currentNode, String path, byte[] sourceBytes) {
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

        return MagicCommentParser.createSourceTStringBasedOnMagicEncodingComment(sourceBytes, mainScriptEncoding);
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
