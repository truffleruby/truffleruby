/*
 * Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is modified from org.jruby.runtime.encoding.EncodingService,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 */
package org.truffleruby.core.encoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.graalvm.shadowed.org.jcodings.EncodingDB;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.EncodingUtils;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.platform.NativeConfiguration;
import org.truffleruby.platform.TruffleNFIPlatform;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import static org.truffleruby.core.encoding.Encodings.INITIAL_NUMBER_OF_ENCODINGS;
import static org.truffleruby.core.encoding.Encodings.getBuiltInEncoding;

/** Always use {@link Encoding#getIndex()} for encoding indices. Never use
 * {@link org.jcodings.EncodingDB.Entry#getIndex()}. */
public final class EncodingManager {

    private RubyEncoding[] ENCODING_LIST_BY_ENCODING_INDEX = new RubyEncoding[INITIAL_NUMBER_OF_ENCODINGS];
    private final Map<String, RubyEncoding> LOOKUP = new ConcurrentHashMap<>();
    private final RubyContext context;
    private final RubyLanguage language;

    @CompilationFinal private RubyEncoding localeEncoding;
    private RubyEncoding defaultExternalEncoding;
    private RubyEncoding defaultInternalEncoding;

    public EncodingManager(RubyContext context, RubyLanguage language) {
        this.context = context;
        this.language = language;
    }

    public void defineEncodings() {
        final RubyClass encodingClass = context.getCoreLibrary().encodingClass;
        initializeEncodings(encodingClass);
        initializeEncodingAliases(encodingClass);
    }

    private void initializeEncodings(RubyClass encodingClass) {
        for (RubyEncoding encoding : Encodings.BUILT_IN_ENCODINGS) {
            defineBuiltInEncoding(encoding);
            byte[] name = encoding.getNameByteArray();
            for (String constName : EncodingUtils.encodingNames(name, 0, name.length)) {
                encodingClass.fields.setConstant(context, null, constName, encoding);
            }
        }
    }

    private void initializeEncodingAliases(RubyClass encodingClass) {
        var iterator = EncodingDB.getAliases().entryIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            final EncodingDB.Entry encodingEntry = entry.value;

            // The alias name should be exactly the one in the encodings DB.
            final Encoding encoding = encodingEntry.getEncoding();
            final RubyEncoding rubyEncoding = getBuiltInEncoding(encoding);
            defineAlias(rubyEncoding,
                    new String(entry.bytes, entry.p, entry.end - entry.p, StandardCharsets.US_ASCII));

            // The constant names must be treated by the <code>encodingNames</code> helper.
            for (String constName : EncodingUtils.encodingNames(entry.bytes, entry.p, entry.end)) {
                encodingClass.fields.setConstant(context, null, constName, rubyEncoding);
            }
        }
    }

    public void initializeDefaultEncodings(TruffleNFIPlatform nfi, NativeConfiguration nativeConfiguration) {
        initializeLocaleEncoding(nfi, nativeConfiguration);

        // External should always have a value, but Encoding.external_encoding{,=} will lazily setup
        final String externalEncodingName = context.getOptions().EXTERNAL_ENCODING;
        if (!externalEncodingName.isEmpty()) {
            final RubyEncoding loadedEncoding = getRubyEncoding(externalEncodingName);
            if (loadedEncoding == null) {
                // TODO (nirvdrum 28-Oct-16): This should just print a nice error message and exit
                // with a status code of 1 -- it's essentially an input validation error -- no need
                // to show the user a full trace.
                throw new RuntimeException("unknown encoding name - " + externalEncodingName);
            } else {
                setDefaultExternalEncoding(loadedEncoding);
            }
        } else {
            setDefaultExternalEncoding(getLocaleEncoding());
        }

        // The internal encoding is nil by default
        final String internalEncodingName = context.getOptions().INTERNAL_ENCODING;
        if (!internalEncodingName.isEmpty()) {
            final RubyEncoding rubyEncoding = getRubyEncoding(internalEncodingName);
            if (rubyEncoding == null) {
                // TODO (nirvdrum 28-Oct-16): This should just print a nice error message and exit
                // with a status code of 1 -- it's essentially an input validation error -- no need
                // to show the user a full trace.
                throw new RuntimeException("unknown encoding name - " + internalEncodingName);
            } else {
                setDefaultInternalEncoding(rubyEncoding);
            }
        }
    }

    private void initializeLocaleEncoding(TruffleNFIPlatform nfi, NativeConfiguration nativeConfiguration) {
        final String localeEncodingName;
        final String detector;
        if (nfi != null) {
            final int codeset = (int) nativeConfiguration.get("platform.langinfo.CODESET");

            // nl_langinfo() needs setlocale(LC_CTYPE, "") before, which is done in RubyLanguage#setupLocale()
            // char *nl_langinfo(nl_item item);
            // nl_item is int on at least Linux and macOS
            final Object nl_langinfo = nfi.getFunction(context, "nl_langinfo", "(sint32):string");

            final long address;
            try {
                address = nfi.asPointer(InteropLibrary.getUncached().execute(nl_langinfo, codeset));
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            final byte[] bytes = new Pointer(context, address).readZeroTerminatedByteArray(
                    context,
                    InteropLibrary.getUncached(),
                    0);
            detector = "nl_langinfo(CODESET)";
            localeEncodingName = new String(bytes, StandardCharsets.US_ASCII);
        } else {
            detector = "Charset.defaultCharset()";
            localeEncodingName = Charset.defaultCharset().name();
        }

        RubyEncoding rubyEncoding = getRubyEncoding(localeEncodingName);
        if (rubyEncoding == null) {
            rubyEncoding = Encodings.US_ASCII;
        }

        if (context.getOptions().WARN_LOCALE && rubyEncoding == Encodings.US_ASCII) {
            String firstLine = "Encoding.find('locale') is US-ASCII (due to " + detector + " which returned " +
                    localeEncodingName + "), this often indicates that the system locale is not set properly. ";
            if ("C".equals(System.getenv("LANG")) && "C".equals(System.getenv("LC_ALL"))) {
                // The parent process seems to explicitly want a C locale (e.g. EnvUtil#invoke_ruby in the MRI test harness), so only warn at config level in this case.
                RubyLanguage.LOGGER.config(firstLine + "Warning at level=CONFIG because LANG=C and LC_ALL=C are set. " +
                        "Set LANG=en_US.UTF-8 and see https://www.graalvm.org/dev/reference-manual/ruby/UTF8Locale/ for details.");
            } else {
                RubyLanguage.LOGGER.warning(firstLine +
                        "Set LANG=en_US.UTF-8 and see https://www.graalvm.org/dev/reference-manual/ruby/UTF8Locale/ for details.");
            }
        }

        localeEncoding = rubyEncoding;
    }

    public synchronized Object[] getEncodingList() {
        return ArrayUtils.copyOf(ENCODING_LIST_BY_ENCODING_INDEX, ENCODING_LIST_BY_ENCODING_INDEX.length);
    }

    public int getNumberOfEncodings() {
        return ENCODING_LIST_BY_ENCODING_INDEX.length;
    }

    @TruffleBoundary
    public RubyEncoding getRubyEncoding(String name) {
        final String normalizedName = name.toLowerCase(Locale.ENGLISH);
        final RubyEncoding encoding;

        switch (normalizedName) {
            case "internal":
                encoding = getDefaultInternalEncoding();
                return encoding == null ? Encodings.BINARY : encoding;
            case "external":
            case "filesystem":
                encoding = getDefaultExternalEncoding();
                return encoding == null ? Encodings.BINARY : encoding;
            case "locale":
                encoding = getLocaleEncoding();
                return encoding == null ? Encodings.BINARY : encoding;
            default:
                return LOOKUP.get(normalizedName);
        }
    }

    // Should only be used by Primitive.encoding_get_encoding_by_index
    @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
    RubyEncoding getRubyEncoding(int encodingIndex) {
        return ENCODING_LIST_BY_ENCODING_INDEX[encodingIndex];
    }

    @TruffleBoundary
    public void defineBuiltInEncoding(RubyEncoding rubyEncoding) {
        final int encodingIndex = rubyEncoding.index;

        assert ENCODING_LIST_BY_ENCODING_INDEX[encodingIndex] == null;
        ENCODING_LIST_BY_ENCODING_INDEX[encodingIndex] = rubyEncoding;

        addToLookup(rubyEncoding.toString(), rubyEncoding);
    }

    @TruffleBoundary
    public synchronized RubyEncoding defineDummyEncoding(String name) {
        final int encodingIndex = ENCODING_LIST_BY_ENCODING_INDEX.length;

        final RubyEncoding rubyEncoding = Encodings.newDummyEncoding(language, encodingIndex, name);

        ENCODING_LIST_BY_ENCODING_INDEX = Arrays.copyOf(ENCODING_LIST_BY_ENCODING_INDEX, encodingIndex + 1);
        ENCODING_LIST_BY_ENCODING_INDEX[encodingIndex] = rubyEncoding;

        addToLookup(rubyEncoding.name.getJavaString(), rubyEncoding);
        return rubyEncoding;

    }

    @TruffleBoundary
    public void defineAlias(RubyEncoding encoding, String name) {
        addToLookup(name, encoding);
    }

    @TruffleBoundary
    private void addToLookup(String name, RubyEncoding rubyEncoding) {
        LOOKUP.put(name.toLowerCase(Locale.ENGLISH), rubyEncoding);
    }

    @TruffleBoundary
    public synchronized RubyEncoding createDummyEncoding(String name) {
        if (getRubyEncoding(name) != null) {
            return null;
        }

        return defineDummyEncoding(name);
    }

    public RubyEncoding getLocaleEncoding() {
        return localeEncoding;
    }

    public void setDefaultExternalEncoding(RubyEncoding defaultExternalEncoding) {
        this.defaultExternalEncoding = defaultExternalEncoding;
    }

    public RubyEncoding getDefaultExternalEncoding() {
        return defaultExternalEncoding;
    }

    public void setDefaultInternalEncoding(RubyEncoding defaultInternalEncoding) {
        this.defaultInternalEncoding = defaultInternalEncoding;
    }

    public RubyEncoding getDefaultInternalEncoding() {
        return defaultInternalEncoding;
    }

}
