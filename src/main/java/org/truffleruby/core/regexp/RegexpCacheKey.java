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
package org.truffleruby.core.regexp;

import java.util.Objects;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.TStringBuilder;
import org.truffleruby.core.string.TStringWithEncoding;
import org.truffleruby.language.control.DeferredRaiseException;

public final class RegexpCacheKey {

    public static RegexpCacheKey calculate(TStringWithEncoding source, RegexpOptions options)
            throws DeferredRaiseException {
        RubyEncoding[] fixedEnc = new RubyEncoding[]{ null };
        TStringBuilder processed = ClassicRegexp.preprocess(source, source.getEncoding(), fixedEnc,
                RegexpSupport.ErrorMode.RAISE);
        RegexpOptions[] optionsArray = new RegexpOptions[]{ options };
        RubyEncoding enc = ClassicRegexp.computeRegexpEncoding(optionsArray, source.getEncoding(), fixedEnc);

        return new RegexpCacheKey(processed.toTString(), enc, optionsArray[0]);
    }

    public final TruffleString tstring;
    public final RubyEncoding encoding;
    public final RegexpOptions options;

    private RegexpCacheKey(TruffleString tstring, RubyEncoding encoding, RegexpOptions options) {
        this.tstring = tstring;
        this.encoding = encoding;
        this.options = options;
    }

    public RegexpOptions getOptions() {
        return options;
    }

    public RubyEncoding getEncoding() {
        return encoding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoding, tstring, options);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegexpCacheKey) {
            final RegexpCacheKey other = (RegexpCacheKey) o;
            return encoding == other.encoding && tstring.equals(other.tstring) && options.equals(other.options);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return '/' + tstring.toString() + '/' + options.toOptionsString() + " -- " + encoding.name;
    }
}
