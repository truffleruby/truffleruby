/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.regexp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.RubyContext;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.regexp.TruffleRegexpNodes.TRegexCompileNode;
import org.truffleruby.core.string.CannotConvertBinaryRubyStringToJavaString;
import org.truffleruby.core.string.TStringBuilder;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.interop.InteropNodes;
import org.truffleruby.interop.TranslateInteropExceptionNodeGen;
import org.truffleruby.language.Nil;
import org.truffleruby.language.control.DeferredRaiseException;

public final class TRegexCache {

    // onlyMatchAtStart=false
    private Object usAsciiRegex;
    private Object latin1Regex;
    private Object utf8Regex;
    private Object binaryRegex;

    // onlyMatchAtStart=true
    private Object usAsciiRegexAtStart;
    private Object latin1RegexAtStart;
    private Object utf8RegexAtStart;
    private Object binaryRegexAtStart;

    public Object getUSASCIIRegex(boolean onlyMatchAtStart) {
        return onlyMatchAtStart ? usAsciiRegexAtStart : usAsciiRegex;
    }

    public Object getLatin1Regex(boolean onlyMatchAtStart) {
        return onlyMatchAtStart ? latin1RegexAtStart : latin1Regex;
    }

    public Object getUTF8Regex(boolean onlyMatchAtStart) {
        return onlyMatchAtStart ? utf8RegexAtStart : utf8Regex;
    }

    public Object getBinaryRegex(boolean onlyMatchAtStart) {
        return onlyMatchAtStart ? binaryRegexAtStart : binaryRegex;
    }

    @TruffleBoundary
    public Object compile(RubyContext context, RubyRegexp regexp, boolean onlyMatchAtStart, RubyEncoding encoding,
            TRegexCompileNode node) {
        Object tregex = compileTRegex(context, regexp, onlyMatchAtStart, encoding);
        if (tregex == null) {
            tregex = Nil.INSTANCE;
            if (context.getOptions().WARN_TRUFFLE_REGEX_COMPILE_FALLBACK) {
                node.getWarnOnFallbackNode().call(
                        context.getCoreLibrary().truffleRegexpOperationsModule,
                        "warn_fallback_regex",
                        regexp,
                        onlyMatchAtStart,
                        encoding);
            }
        } else if (isBacktracking(tregex)) {
            if (context.getOptions().WARN_TRUFFLE_REGEX_COMPILE_FALLBACK) {
                node.getWarnOnFallbackNode().call(
                        context.getCoreLibrary().truffleRegexpOperationsModule,
                        "warn_backtracking",
                        regexp,
                        onlyMatchAtStart,
                        encoding);
            }
        }

        if (encoding == Encodings.US_ASCII) {
            if (onlyMatchAtStart) {
                usAsciiRegexAtStart = tregex;
            } else {
                usAsciiRegex = tregex;
            }
        } else if (encoding == Encodings.ISO_8859_1) {
            if (onlyMatchAtStart) {
                latin1RegexAtStart = tregex;
            } else {
                latin1Regex = tregex;
            }
        } else if (encoding == Encodings.UTF_8) {
            if (onlyMatchAtStart) {
                utf8RegexAtStart = tregex;
            } else {
                utf8Regex = tregex;
            }
        } else if (encoding == Encodings.BINARY) {
            if (onlyMatchAtStart) {
                binaryRegexAtStart = tregex;
            } else {
                binaryRegex = tregex;
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }

        return tregex;
    }

    private static boolean isBacktracking(Object tregex) {
        return (boolean) InteropNodes.readMember(
                null,
                InteropLibrary.getUncached(),
                tregex,
                "isBacktracking",
                TranslateInteropExceptionNodeGen.getUncached());
    }

    public static String toTRegexEncoding(RubyEncoding encoding) {
        if (encoding == Encodings.UTF_8) {
            return "UTF-8";
        } else if (encoding == Encodings.US_ASCII) {
            return "ASCII";
        } else if (encoding == Encodings.ISO_8859_1) {
            return "LATIN-1";
        } else if (encoding == Encodings.BINARY) {
            return "BYTES";
        } else {
            return null;
        }
    }

    @TruffleBoundary
    private static Object compileTRegex(RubyContext context, RubyRegexp regexp, boolean onlyMatchAtStart,
            RubyEncoding enc) {
        String tRegexEncoding = TRegexCache.toTRegexEncoding(enc);
        if (tRegexEncoding == null) {
            return null;
        }

        String processedRegexpSource;
        RubyEncoding[] fixedEnc = new RubyEncoding[]{ null };
        final TStringBuilder tstringBuilder;
        try {
            tstringBuilder = ClassicRegexp
                    .preprocess(
                            new TStringWithEncoding(regexp.source, regexp.encoding),
                            enc,
                            fixedEnc,
                            RegexpSupport.ErrorMode.RAISE);
        } catch (DeferredRaiseException dre) {
            throw dre.getException(context);
        }
        var tstring = tstringBuilder.toTString();
        try {
            processedRegexpSource = TStringUtils.toJavaStringOrThrow(tstring, tstringBuilder.getRubyEncoding());
        } catch (CannotConvertBinaryRubyStringToJavaString e) {
            // A BINARY regexp with non-US-ASCII bytes, pass it as "raw bytes" instead.
            // TRegex knows how to interpret those bytes correctly as we pass the encoding name as well.
            var latin1string = tstring.forceEncodingUncached(Encodings.BINARY.tencoding,
                    Encodings.ISO_8859_1.tencoding);
            processedRegexpSource = TStringUtils.toJavaStringOrThrow(latin1string, Encodings.ISO_8859_1);
        }

        String flags = optionsToFlags(regexp.options, onlyMatchAtStart);

        String ignoreAtomicGroups = context.getOptions().TRUFFLE_REGEX_IGNORE_ATOMIC_GROUPS
                ? ",IgnoreAtomicGroups=true"
                : "";

        String regex = "Flavor=Ruby,Encoding=" + tRegexEncoding + ignoreAtomicGroups + "/" + processedRegexpSource +
                "/" + flags;
        Source regexSource = Source
                .newBuilder("regex", regex, "Regexp")
                .mimeType("application/tregex")
                .internal(true)
                .build();
        Object compiledRegex = context.getEnv().parseInternal(regexSource).call();
        if (InteropLibrary.getUncached().isNull(compiledRegex)) {
            return null;
        } else {
            return compiledRegex;
        }
    }

    public static String optionsToFlags(RegexpOptions options, boolean onlyMatchAtStart) {
        StringBuilder flags = new StringBuilder(4);
        if (options.isMultiline()) {
            flags.append('m');
        }
        if (options.isIgnorecase()) {
            flags.append('i');
        }
        if (options.isExtended()) {
            flags.append('x');
        }
        if (onlyMatchAtStart) {
            flags.append('y');
        }
        return flags.toString();
    }
}
