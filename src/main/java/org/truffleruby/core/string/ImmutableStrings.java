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
package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.collections.WeakValueCache;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ImmutableStrings {

    private static final List<ImmutableRubyString> STRINGS_TO_CACHE = new ArrayList<>();

    private final TStringCache tstringCache;
    private final WeakValueCache<TStringWithEncoding, ImmutableRubyString> values = new WeakValueCache<>();

    public ImmutableStrings(TStringCache tStringCache) {
        this.tstringCache = tStringCache;
        for (ImmutableRubyString name : STRINGS_TO_CACHE) {
            addToCache(name);
        }
    }

    @TruffleBoundary
    public ImmutableRubyString get(TruffleString tstring, RubyEncoding encoding) {
        return get(
                tstring.getInternalByteArrayUncached(encoding.tencoding),
                TStringUtils.hasImmutableInternalByteArray(tstring),
                encoding);
    }

    @TruffleBoundary
    public ImmutableRubyString get(InternalByteArray byteArray, boolean isLookupKeyImmutable,
            RubyEncoding encoding) {
        // Ensure all ImmutableRubyString have a TruffleString from the TStringCache
        var cachedTString = tstringCache.getTString(byteArray, isLookupKeyImmutable, encoding);
        var tstringWithEncoding = new TStringWithEncoding(cachedTString, encoding);

        final ImmutableRubyString string = values.get(tstringWithEncoding);
        if (string != null) {
            return string;
        } else {
            return values.addInCacheIfAbsent(tstringWithEncoding, new ImmutableRubyString(cachedTString, encoding));
        }
    }

    @TruffleBoundary
    public ImmutableRubyString get(byte[] bytes, RubyEncoding encoding) {
        // Ensure all ImmutableRubyString have a TruffleString from the TStringCache
        var cachedTString = tstringCache.getTString(bytes, encoding);
        var tstringWithEncoding = new TStringWithEncoding(cachedTString, encoding);

        final ImmutableRubyString string = values.get(tstringWithEncoding);
        if (string != null) {
            return string;
        } else {
            return values.addInCacheIfAbsent(tstringWithEncoding, new ImmutableRubyString(cachedTString, encoding));
        }
    }

    public static ImmutableRubyString createAndCacheLater(TruffleString name,
            RubyEncoding encoding) {
        final ImmutableRubyString string = new ImmutableRubyString(name, encoding);
        assert !STRINGS_TO_CACHE.contains(string);
        STRINGS_TO_CACHE.add(string);
        return string;
    }

    private void addToCache(ImmutableRubyString string) {
        var encoding = string.getEncodingUncached();
        var cachedTString = tstringCache.getTString(string.tstring, encoding);
        assert cachedTString == string.tstring;
        var tstringWithEncoding = new TStringWithEncoding(cachedTString, encoding);
        final ImmutableRubyString existing = values.addInCacheIfAbsent(tstringWithEncoding, string);
        if (existing != string) {
            throw CompilerDirectives
                    .shouldNotReachHere("Duplicate ImmutableRubyString in ImmutableStrings: " + existing);
        }
    }

    @TruffleBoundary
    public Collection<ImmutableRubyString> all() {
        return values.values();
    }

}
