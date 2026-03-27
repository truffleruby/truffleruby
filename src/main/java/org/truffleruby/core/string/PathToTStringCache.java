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

import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.TStringUtils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

/** A cache from {@link RubyLanguage#getPath(Source) the Source path} to a TruffleString. The TruffleString is kept
 * alive as long as the Source is reachable. */
public final class PathToTStringCache {

    private final RubyLanguage language;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final WeakHashMap<String, TruffleString> javaStringToTString = new WeakHashMap<>();

    public PathToTStringCache(RubyLanguage language) {
        this.language = language;
    }

    @TruffleBoundary
    public TruffleString getCachedPath(Source source) {
        final String path = RubyLanguage.getPath(source);

        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            var tstring = javaStringToTString.get(path);
            if (tstring != null) {
                return tstring;
            }
        } finally {
            readLock.unlock();
        }

        final TruffleString cachedString = language.tstringCache.getTString(TStringUtils.utf8TString(path),
                Encodings.UTF_8);

        final Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            javaStringToTString.putIfAbsent(path, cachedString);
        } finally {
            writeLock.unlock();
        }

        return cachedString;
    }

}
