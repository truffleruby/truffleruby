/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.collections;

import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.TStringWithEncoding;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ByteArrayBuilder {

    private static final byte[] EMPTY_BYTES = ArrayUtils.EMPTY_BYTES;

    private byte[] bytes = EMPTY_BYTES;
    private int length;

    public ByteArrayBuilder() {
    }

    public ByteArrayBuilder(int size) {
        bytes = new byte[size];
    }

    public static ByteArrayBuilder create(InternalByteArray bytes) {
        final ByteArrayBuilder builder = new ByteArrayBuilder(bytes.getLength());
        builder.append(bytes.getArray(), bytes.getOffset(), bytes.getLength());
        return builder;
    }

    public static ByteArrayBuilder createUnsafeBuilder(byte[] wrap) {
        final ByteArrayBuilder builder = new ByteArrayBuilder();
        builder.unsafeReplace(wrap, wrap.length);
        return builder;
    }

    public int getLength() {
        return length;
    }

    public void append(int b) {
        append((byte) b);
    }

    public void append(byte b) {
        ensureSpace(1);
        bytes[length] = b;
        length++;
    }

    public void append(byte b, int count) {
        if (count > 0) {
            ensureSpace(count);
            Arrays.fill(bytes, length, length + count, b);
            length += count;
        }
    }

    public void append(int b, int count) {
        append((byte) b, count);
    }

    public void append(byte[] appendBytes) {
        append(appendBytes, 0, appendBytes.length);
    }

    public void append(byte[] appendBytes, int appendStart, int appendLength) {
        ensureSpace(appendLength);
        System.arraycopy(appendBytes, appendStart, bytes, length, appendLength);
        length += appendLength;
    }

    public void append(InternalByteArray bytes) {
        append(bytes.getArray(), bytes.getOffset(), bytes.getLength());
    }

    public void append(InternalByteArray bytes, int appendStart, int appendLength) {
        append(bytes.getArray(), bytes.getOffset() + appendStart, appendLength);
    }

    public void append(TStringWithEncoding other) {
        append(other.getInternalByteArray());
    }

    public void append(TStringWithEncoding other, int appendStart, int appendLength) {
        append(other.getInternalByteArray(), appendStart, appendLength);
    }

    public void append(TruffleString other, RubyEncoding enc) {
        append(other.getInternalByteArrayUncached(enc.tencoding));
    }

    public void unsafeReplace(byte[] bytes, int size) {
        this.bytes = bytes;
        this.length = size;
    }

    private void ensureSpace(int space) {
        if (length + space > bytes.length) {
            bytes = Arrays.copyOf(bytes, (bytes.length + space) * 2);
        }
    }

    public byte get(int n) {
        return bytes[n];
    }

    public void set(int n, int b) {
        bytes[n] = (byte) b;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, length);
    }

    public void clear() {
        bytes = EMPTY_BYTES;
        length = 0;
    }

    @Override
    public String toString() {
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    public TruffleString toTString(RubyEncoding encoding) {
        return TStringUtils.fromByteArray(getBytes(), encoding);
    }

    public TStringWithEncoding toTStringWithEnc(RubyEncoding encoding) {
        return new TStringWithEncoding(TStringUtils.fromByteArray(getBytes(), encoding), encoding);
    }

    // TODO CS 14-Feb-17 review all uses of this method
    public byte[] getUnsafeBytes() {
        return bytes;
    }

    // TODO CS 14-Feb-17 review all uses of this method
    public void setLength(int length) {
        this.length = length;
    }

    // TODO CS 14-Feb-17 review all uses of this method
    public void unsafeEnsureSpace(int space) {
        ensureSpace(space);
    }

}
