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

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;

public final class TBytesKey {

    private final byte[] bytes;
    private final int offset;
    private final int length;
    private RubyEncoding encoding;
    private final int bytesHashCode;

    public TBytesKey(
            byte[] bytes,
            int offset,
            int length,
            int bytesHashCode,
            RubyEncoding encoding) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.bytesHashCode = bytesHashCode;
        this.encoding = encoding;
    }

    public TBytesKey(byte[] bytes, RubyEncoding encoding) {
        this(bytes, 0, bytes.length, Arrays.hashCode(bytes), encoding);
    }

    /** Supports the creation of a cache key using a subset of bytes. This key *must* be used for lookups only. If you
     * want to insert into the cache, you *must* use the result of {@link #makeCacheable(boolean)}.
     *
     * @param byteArray A byte array retrieved from a {@link TruffleString}
     * @param encoding The Ruby encoding object needed to properly decode the associated byte array */
    public TBytesKey(InternalByteArray byteArray, RubyEncoding encoding) {
        this(
                byteArray.getArray(),
                byteArray.getOffset(),
                byteArray.getLength(),
                hashCode(byteArray),
                encoding);
    }

    @Override
    public int hashCode() {
        return bytesHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TBytesKey) {
            final TBytesKey other = (TBytesKey) o;
            if (encoding == null) {
                if (equalBytes(this, other)) {
                    // For getMatchedEncoding()
                    this.encoding = Objects.requireNonNull(other.encoding);
                    return true;
                } else {
                    return false;
                }
            } else {
                return encoding == other.encoding && equalBytes(this, other);
            }
        }

        return false;
    }

    public RubyEncoding getMatchedEncoding() {
        return encoding;
    }

    @Override
    public String toString() {
        var encoding = this.encoding != null ? this.encoding.tencoding : TruffleString.Encoding.BYTES;
        return TruffleString.fromByteArrayUncached(bytes, encoding, false).toString();
    }

    private static int hashCode(InternalByteArray byteArray) {
        return hashCode(byteArray.getArray(), byteArray.getOffset(), byteArray.getLength());
    }

    /** A variant of {@link Arrays#hashCode(byte[])} that allows for selecting a range within the array. */
    private static int hashCode(byte[] bytes, int offset, int length) {
        int result = 1;
        for (int i = offset; i < offset + length; i++) {
            result = 31 * result + bytes[i];
        }

        return result;
    }

    private boolean equalBytes(TBytesKey a, TBytesKey b) {
        return Arrays.equals(a.bytes, a.offset, a.offset + a.length, b.bytes, b.offset, b.offset + b.length);
    }

    private boolean isPerfectFit() {
        return offset == 0 && length == bytes.length;
    }

    /** Returns a cache key suitable for insertion into the string cache. It's quite common that we want to cache a
     * substring. Since we don't want to retain the entire original string, we resolve the substring by making a copy of
     * the byte range that we need. However, that is a costly operation and that work is discarded in the event of a
     * cache hit. To avoid incurring that cost unnecessarily, we allow cache keys to refer to a subset of a byte array.
     * While that saves computation during a cache lookup, it means such keys are unsuitable for insertion into the
     * cache. This method makes a key we can use safely for insertion.
     * <p>
     * If we know that the key refers to an immutable byte array and the key does not refer to a substring, we can
     * safely refer to the original byte array without needing to make an additional copy.
     *
     * @param isImmutable whether the key's byte array is immutable
     * @return a cache key suitable for insertion */
    public TBytesKey makeCacheable(boolean isImmutable) {
        if (isImmutable && isPerfectFit()) {
            return this;
        }

        // Make a copy of the substring's bytes so we can cache them without retaining the larger original byte array.
        var resolvedSubstring = ArrayUtils.extractRange(this.bytes, this.offset, this.offset + this.length);

        return new TBytesKey(resolvedSubstring, encoding);
    }

    public TBytesKey withNewEncoding(RubyEncoding encoding) {
        return new TBytesKey(bytes, offset, length, bytesHashCode, encoding);
    }

    public TruffleString toTruffleString() {
        return TStringUtils.fromByteArray(bytes, offset, length, encoding.tencoding);
    }

}
