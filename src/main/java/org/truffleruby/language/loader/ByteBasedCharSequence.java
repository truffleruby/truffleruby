/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.loader;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.string.TStringWithEncoding;

/** YARP gives us byte offsets but Truffle wants a CharSequence, this tries to integrate both as much as possible. Using
 * a java.lang.String instead would mean computing char offsets, which is prohibitively expensive. */
public final class ByteBasedCharSequence implements CharSequence {

    private final byte[] bytes;
    private final int offset;
    private final int length;
    private final RubyEncoding encoding;

    public ByteBasedCharSequence(TStringWithEncoding tstringWithEnc) {
        this(tstringWithEnc.getBytesOrCopy(), 0, tstringWithEnc.byteLength(), tstringWithEnc.encoding);
    }

    private ByteBasedCharSequence(byte[] bytes, int offset, int length, RubyEncoding encoding) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.encoding = encoding;
    }

    @Override
    public int length() {
        return bytes.length;
    }

    @Override
    public char charAt(int index) {
        assert index >= offset && index < offset + length;
        return (char) bytes[offset + index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new ByteBasedCharSequence(bytes, start, end - start, encoding);
    }

    @Override
    public String toString() {
        return TruffleString.fromByteArrayUncached(bytes, offset, length, encoding.tencoding, false)
                .toJavaStringUncached();
    }
}
