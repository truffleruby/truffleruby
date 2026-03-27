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
package org.truffleruby.core.string;

import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.collections.WeakValueCache;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.symbol.CoreSymbols;
import org.truffleruby.core.symbol.RubySymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/** This cache caches (byte[], encoding) to TruffleString. The main value is from using it for string literals in files
 * without {@code # frozen_string_literal: true}, so equivalent string literals are shared. For most other usages there
 * is another higher-level cache but this cache then helps to deduplicate TruffleString's across the different
 * higher-level caches. */
public final class TStringCache {

    private final WeakValueCache<TBytesKey, TruffleString> bytesToTString = new WeakValueCache<>();

    private int byteArrayReusedCount;
    private int tstringsReusedCount;
    private int tstringBytesSaved;

    public TStringCache(CoreSymbols coreSymbols) {
        addTStringConstants();
        addCoreSymbolTStrings(coreSymbols);
        addFrozenStrings();
    }

    private void addFrozenStrings() {
        for (var tstring : FrozenStrings.TSTRINGS) {
            register(tstring, Encodings.BINARY);
        }
    }

    private void addTStringConstants() {
        for (var tstring : TStringConstants.UTF8_SINGLE_BYTE) {
            register(tstring, Encodings.UTF_8);
        }
        for (var tstring : TStringConstants.US_ASCII_SINGLE_BYTE) {
            register(tstring, Encodings.US_ASCII);
        }
        for (var tstring : TStringConstants.BINARY_SINGLE_BYTE) {
            register(tstring, Encodings.BINARY);
        }
        for (var tstring : TStringConstants.TSTRING_CONSTANTS.values()) {
            register(tstring, Encodings.US_ASCII);
        }
    }

    private void addCoreSymbolTStrings(CoreSymbols coreSymbols) {
        for (RubySymbol symbol : coreSymbols.CORE_SYMBOLS) {
            register(symbol.tstring, symbol.encoding);
        }
    }

    private void register(TruffleString tstring, RubyEncoding encoding) {
        final TBytesKey key = new TBytesKey(TStringUtils.getBytesOrFail(tstring, encoding), encoding);
        final TruffleString existing = bytesToTString.put(key, tstring);
        if (existing != null && existing != tstring) {
            throw CompilerDirectives.shouldNotReachHere("Duplicate TruffleString in TStringCache: " + existing);
        }
    }

    @TruffleBoundary
    public TruffleString getTString(TruffleString string, RubyEncoding rubyEncoding) {
        assert rubyEncoding != null;

        var byteArray = string.getInternalByteArrayUncached(rubyEncoding.tencoding);
        final TBytesKey key = new TBytesKey(byteArray, rubyEncoding);

        return getTString(key, TStringUtils.hasImmutableInternalByteArray(string));
    }

    @TruffleBoundary
    public TruffleString getTString(InternalByteArray byteArray, boolean isImmutable, RubyEncoding rubyEncoding) {
        assert rubyEncoding != null;

        return getTString(new TBytesKey(byteArray, rubyEncoding), isImmutable);
    }

    @TruffleBoundary
    public TruffleString getTString(byte[] bytes, RubyEncoding rubyEncoding) {
        assert rubyEncoding != null;

        return getTString(new TBytesKey(bytes, rubyEncoding), true);
    }

    @TruffleBoundary
    private TruffleString getTString(TBytesKey lookupKey, boolean isLookupKeyImmutable) {
        final TruffleString tstring = bytesToTString.get(lookupKey);
        var rubyEncoding = lookupKey.getMatchedEncoding();

        if (tstring != null) {
            ++tstringsReusedCount;
            tstringBytesSaved += tstring.byteLength(lookupKey.getMatchedEncoding().tencoding);

            return tstring;
        }

        // At this point, we were unable to find a TruffleString with the same bytes and encoding (i.e., a direct match).
        // However, there may still be a TruffleString with the same byte[] and sharing a direct byte[] can still allow some
        // reference equality optimizations. So, do another search but with a marker encoding. The only guarantee
        // we can make about the resulting TruffleString is that it would have the same logical byte[], but that's good enough
        // for our purposes.
        TBytesKey keyNoEncoding = lookupKey.withNewEncoding(null);
        final TruffleString tstringWithSameBytesButDifferentEncoding = bytesToTString.get(keyNoEncoding);

        final TruffleString newTString;
        if (tstringWithSameBytesButDifferentEncoding != null) {
            var prevEncoding = keyNoEncoding.getMatchedEncoding().tencoding;
            newTString = tstringWithSameBytesButDifferentEncoding.forceEncodingUncached(prevEncoding,
                    rubyEncoding.tencoding);

            ++byteArrayReusedCount;
            tstringBytesSaved += newTString.byteLength(rubyEncoding.tencoding);
        } else {
            newTString = lookupKey.toTruffleString();
        }

        // Use the new TruffleString bytes in the cache, so we do not keep bytes alive unnecessarily.
        return bytesToTString.addInCacheIfAbsent(lookupKey.makeCacheable(isLookupKeyImmutable), newTString);
    }

    public boolean contains(TruffleString string, RubyEncoding encoding) {
        final TBytesKey key = new TBytesKey(TStringUtils.getBytesOrCopy(string, encoding), encoding);

        return bytesToTString.get(key) != null;
    }

    public int getByteArrayReusedCount() {
        return byteArrayReusedCount;
    }

    public int getTStringsReusedCount() {
        return tstringsReusedCount;
    }

    public int getTStringBytesSaved() {
        return tstringBytesSaved;
    }

    public int totalTStrings() {
        return bytesToTString.size();
    }

}
