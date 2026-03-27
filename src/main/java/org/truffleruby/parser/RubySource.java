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
package org.truffleruby.parser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyFileTypeDetector;
import org.truffleruby.RubyLanguage;

import com.oracle.truffle.api.source.Source;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.TStringWithEncoding;

/** A RubySource object contains extra data for a Source. RubySource objects are created just before parsing, so they
 * are just a convenient to pass extra data together with the Source and short-lived (not stored anywhere). It notably
 * contains the source as a byte[] or TruffleString to avoid extra byte[]->java.lang.String->byte[] conversions. */
public final class RubySource {

    private final Source source;
    private final TruffleString code;
    private byte[] bytes;
    private final RubyEncoding encoding;
    private final boolean isEval;
    private final int lineOffset;

    public RubySource(Source source, String sourcePath) {
        this(source, sourcePath, null, false);
    }

    public RubySource(Source source, String sourcePath, TStringWithEncoding code) {
        this(source, sourcePath, code, false);
    }

    private RubySource(Source source, String sourcePath, TStringWithEncoding code, boolean isEval) {
        this(source, sourcePath, code, isEval, 0);
    }

    /** @param sourcePath The path that will be used by the parser for __FILE__, warnings and syntax errors. Currently,
     *            the same as {@link RubyLanguage#getPath(Source)}. Kept separate as we might want to change
     *            Source#getName() for non-file Sources in the future (but then we'll need to still use this path in
     *            Ruby backtraces). */
    public RubySource(Source source, String sourcePath, TStringWithEncoding code, boolean isEval, int lineOffset) {
        assert RubyLanguage.getPath(source).equals(sourcePath) : RubyLanguage.getPath(source) + " vs " + sourcePath;

        this.source = Objects.requireNonNull(source);

        if (code == null) {
            // We only have the Source, which only contains a java.lang.String.
            // The sourcePath might not exist, so we cannot reread from the filesystem.
            // So we look for the magic encoding comment and if not found use UTF-8.
            var sourceString = source.getCharacters().toString();
            var encoding = RubyFileTypeDetector.findEncoding(new BufferedReader(new StringReader(sourceString)));
            if (encoding == null) {
                encoding = Encodings.UTF_8;
            }
            code = new TStringWithEncoding(TStringUtils.fromJavaString(sourceString, encoding), encoding);
        }
        assert checkMagicEncoding(code);

        this.code = code.tstring;
        this.encoding = code.encoding;
        this.isEval = isEval;
        this.lineOffset = lineOffset;
    }

    private static boolean checkMagicEncoding(TStringWithEncoding code) {
        var magicEncoding = MagicCommentParser.parseMagicEncodingComment(code);
        assert magicEncoding == null || magicEncoding == code.encoding;
        return true;
    }

    public Source getSource() {
        return source;
    }

    public String getSourcePath() {
        // NOTE: we avoid storing the source path in RubySource because that is problematic for pre-initialization
        return RubyLanguage.getPath(source);
    }

    public TruffleString getTruffleString() {
        return code;
    }

    public TStringWithEncoding getTStringWithEncoding() {
        return new TStringWithEncoding(code, encoding);
    }

    public byte[] getBytes() {
        if (bytes != null) {
            return bytes;
        } else {
            return bytes = TStringUtils.getBytesOrCopy(code, encoding);
        }
    }

    public RubyEncoding getEncoding() {
        return encoding;
    }

    public boolean isEval() {
        return isEval;
    }

    public int getLineOffset() {
        return lineOffset;
    }

}
