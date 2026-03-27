/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Contains code modified from JRuby's RubyConverter.java and licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1
 * used throughout.
 *
 * Contains code modified jcodings's TranscoderDB.java and EConv.java, which is licensed under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.truffleruby.core.encoding;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.shadowed.org.jcodings.transcode.TranscoderDB;
import org.graalvm.shadowed.org.jcodings.unicode.UnicodeCodeRange;
import org.graalvm.shadowed.org.jcodings.unicode.UnicodeEncoding;
import org.graalvm.shadowed.org.jcodings.util.CaseInsensitiveBytesHash;
import org.graalvm.shadowed.org.jcodings.util.Hash;

import com.oracle.truffle.api.TruffleOptions;
import org.truffleruby.core.string.StringUtils;

/** This class computes all direct transcoder paths for both JVM and Native Image as a convenient-to-access Map. On
 * Native Image, it also loads eagerly everything that would need the tables/*.bin resources, so they are not needed at
 * runtime */
public final class TranscodingManager {

    static final Map<String, Set<String>> allDirectTranscoderPaths = new HashMap<>();

    static {
        for (CaseInsensitiveBytesHash<TranscoderDB.Entry> sourceEntry : TranscoderDB.transcoders) {
            for (Hash.HashEntry<TranscoderDB.Entry> destinationEntry : sourceEntry.entryIterator()) {
                final TranscoderDB.Entry e = destinationEntry.value;

                final String sourceName = StringUtils.toUpperCase(new String(e.getSource(), StandardCharsets.US_ASCII));
                final String destinationName = StringUtils
                        .toUpperCase(new String(e.getDestination(), StandardCharsets.US_ASCII));

                if (TruffleOptions.AOT) {
                    // Load the classes eagerly
                    e.getTranscoder();
                }

                allDirectTranscoderPaths.putIfAbsent(sourceName, new HashSet<>());
                final Set<String> fromSource = allDirectTranscoderPaths.get(sourceName);
                fromSource.add(destinationName);
            }
        }

        if (TruffleOptions.AOT) {
            loadTablesEagerly();
        }
    }

    private static void loadTablesEagerly() {
        for (UnicodeCodeRange unicodeCodeRange : UnicodeCodeRange.values()) {
            // To call package-private org.jcodings.unicode.UnicodeCodeRange#getRange()
            UnicodeEncoding.isInCodeRange(unicodeCodeRange, 0);
        }
    }

}
