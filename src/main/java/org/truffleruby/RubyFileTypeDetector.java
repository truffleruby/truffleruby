/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.parser.MagicCommentParser;

import com.oracle.truffle.api.TruffleFile;

public final class RubyFileTypeDetector implements TruffleFile.FileTypeDetector {

    private static final String[] KNOWN_RUBY_FILES = new String[]{ "Gemfile", "Rakefile" };
    private static final String[] KNOWN_RUBY_SUFFIXES = new String[]{ ".rb", ".rake", ".gemspec" };
    private static final Pattern SHEBANG_REGEXP = Pattern.compile("^#! ?/usr/bin/(env +ruby|ruby).*");

    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        final String fileName = file.getName();

        if (fileName == null) {
            return null;
        }

        final String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);

        for (String candidate : KNOWN_RUBY_SUFFIXES) {
            if (lowerCaseFileName.endsWith(candidate)) {
                return RubyLanguage.MIME_TYPE;
            }
        }

        for (String candidate : KNOWN_RUBY_FILES) {
            if (fileName.equals(candidate)) {
                return RubyLanguage.MIME_TYPE;
            }
        }

        try (BufferedReader fileContent = file.newBufferedReader(StandardCharsets.ISO_8859_1)) {
            final String firstLine = fileContent.readLine();
            if (firstLine != null && SHEBANG_REGEXP.matcher(firstLine).matches()) {
                return RubyLanguage.MIME_TYPE;
            }
        } catch (IOException | SecurityException e) {
            // Reading random files could cause all sorts of errors
        }
        return null;
    }

    @Override
    public Charset findEncoding(TruffleFile file) {
        // We use ISO-8859-1 because every byte is valid in that encoding and
        // we only care about US-ASCII characters for magic encoding comments.
        try (BufferedReader fileContent = file.newBufferedReader(StandardCharsets.ISO_8859_1)) {
            var encoding = findEncoding(fileContent);
            if (encoding != null) {
                // Unfortunately here we have no choice to return a Charset. Not every Ruby encoding has a Charset.
                // Checkstyle: stop
                return encoding.jcoding.getCharset();
                // Checkstyle: resume
            }
        } catch (IOException | SecurityException e) {
            // Reading random files could cause all sorts of errors
        }
        return null; // no magic encoding comment
    }

    public static RubyEncoding findEncoding(BufferedReader reader) {
        try {
            final String firstLine = reader.readLine();
            if (firstLine != null) {
                final String encodingCommentLine;
                if (SHEBANG_REGEXP.matcher(firstLine).matches()) {
                    encodingCommentLine = reader.readLine();
                } else {
                    encodingCommentLine = firstLine;
                }

                if (encodingCommentLine != null) {
                    var encodingComment = new TStringWithEncoding(
                            TStringUtils.fromJavaString(encodingCommentLine, Encodings.BINARY), Encodings.BINARY);
                    var encoding = MagicCommentParser.parseMagicEncodingComment(encodingComment);
                    if (encoding != null) {
                        return encoding;
                    }
                }
            }
        } catch (IOException e) {
            // Use the default encoding if reading failed somehow
        }

        return null;
    }
}
