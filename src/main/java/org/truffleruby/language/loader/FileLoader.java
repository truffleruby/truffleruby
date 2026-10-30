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

import java.io.File;
import java.io.IOException;

import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.MagicCommentParser;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;

/*
 * Loads normal Ruby source files from the file system.
 */
public final class FileLoader {

    private final RubyContext context;
    private final RubyLanguage language;

    public FileLoader(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
    }

    public static void ensureReadable(RubyContext context, TruffleFile file, Node currentNode) {
        if (!file.exists()) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().loadError(
                            "No such file or directory -- " + file,
                            file.getPath(),
                            currentNode));
        }

        if (!file.isReadable()) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().loadError("Permission denied -- " + file, file.getPath(), currentNode));
        }
    }

    public static byte[] readFile(RubyLanguage language, RubyContext context, String path) throws IOException {
        if (context.getOptions().LOG_LOAD) {
            RubyLanguage.LOGGER.info("loading " + path);
        }

        final TruffleFile file = getSafeTruffleFile(language, context, path);
        ensureReadable(context, file, null);

        return file.readAllBytes();
    }

    public RubySource loadFile(String path) throws IOException {
        if (context.getOptions().LOG_LOAD) {
            RubyLanguage.LOGGER.info("loading " + path);
        }

        final TruffleFile file = getSafeTruffleFile(language, context, path);
        ensureReadable(context, file, null);

        /* We read the file's bytes ourselves because the lexer works on bytes and Truffle only gives us a CharSequence.
         * We could convert the CharSequence back to bytes, but that's more expensive than just reading the bytes once
         * and pass them down to the lexer and to the Source. */

        final byte[] sourceBytes = file.readAllBytes();

        var sourceTString = MagicCommentParser.createSourceTStringBasedOnMagicEncodingComment(sourceBytes,
                Encodings.UTF_8);

        final Source source = buildSource(file, path, sourceTString, isInternal(path), false);
        return new RubySource(source, path, sourceTString);
    }

    public static TruffleFile getSafeTruffleFile(RubyLanguage language, RubyContext context, String path) {
        final Env env = context.getEnv();
        if (env.isFileIOAllowed()) {
            return env.getPublicTruffleFile(path);
        }

        final TruffleFile file;
        try {
            file = env.getInternalTruffleFile(path).getCanonicalFile();
        } catch (IOException e) {
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().loadError("Failed to canonicalize -- " + path, path, null));
        }

        final TruffleFile home = language.getRubyHomeTruffleFile();
        if (file.startsWith(home.resolve("lib")) && isStdLibRubyOrCExtFile(file.getPath())) {
            return file;
        } else {
            try {
                return env.getPublicTruffleFile(path);
            } catch (SecurityException e) {
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().loadError(
                                "Permission denied (" + e.getMessage() + ") -- " + path,
                                path,
                                null));
            }
        }
    }

    private static boolean isStdLibRubyOrCExtFile(String path) {
        return path.endsWith(TruffleRuby.EXTENSION) || path.endsWith(RubyLanguage.CEXT_EXTENSION);
    }

    Source buildSource(TruffleFile file, String path, TStringWithEncoding sourceTStringWithEncoding, boolean internal,
            boolean mainSource) {
        /* I'm not sure why we need to explicitly set a MIME type here - we say it's Ruby and this is the only and
         * default MIME type that Ruby supports.
         *
         * But if you remove setting the MIME type you get the following failures:
         *
         * - test/truffle/compiler/stf-optimises.sh (I think the value is different, not the compilation that fails) -
         * test/truffle/integration/tracing.sh (again, probably the values, and I'm not sure we were correct before,
         * it's just changed) */

        assert file.getPath().equals(path);

        /* Do not cache the Source->AST if coverage is enabled. Coverage.result has strange semantics where it stops
         * coverage for all files, but then when the same file is loaded later it somehow needs to start reporting
         * coverage again if Coverage.running? (most likely due to CRuby not having any parse caching). Other files
         * which are not reloaded since Coverage.result should not report coverage, so it seems really difficult to do
         * any caching when coverage is enabled. */
        final boolean coverageEnabled = language.coverageManager.isEnabled();

        final Source source = Source
                .newBuilder(TruffleRuby.LANGUAGE_ID, file)
                .canonicalizePath(false)
                .content(new ByteBasedCharSequence(sourceTStringWithEncoding))
                .internal(internal)
                .cached(!coverageEnabled)
                .option("ruby.MainScript", Boolean.toString(mainSource))
                .option("ruby.Coverage", Boolean.toString(coverageEnabled))
                .build();

        assert source.getPath().equals(path) : "Source#getPath() = " + source.getPath() + " is not the same as " + path;
        return source;
    }

    private boolean isInternal(String path) {
        final String canonicalPath;
        try {
            canonicalPath = new File(path).getCanonicalPath();
        } catch (IOException e) {
            return false;
        }

        // If the file is part of the standard library then we may consider it internal
        if (canonicalPath.startsWith(language.getRubyHome() + "/lib/") &&
                !canonicalPath.startsWith(language.getRubyHome() + "/lib/gems/")) {
            return language.options.STDLIB_AS_INTERNAL;
        } else {
            return false;
        }
    }

}
