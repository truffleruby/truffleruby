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
package org.truffleruby.core.string;

import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.shadowed.org.jcodings.Encoding;
import org.truffleruby.collections.ByteArrayBuilder;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;

public final class TStringBuilder extends ByteArrayBuilder {

    private RubyEncoding encoding = Encodings.BINARY;

    public TStringBuilder() {
        super();
    }

    public TStringBuilder(int size) {
        super(size);
    }

    public static TStringBuilder create(int size) {
        return new TStringBuilder(size);
    }

    public static TStringBuilder create(byte[] bytes, RubyEncoding encoding) {
        final TStringBuilder builder = new TStringBuilder(bytes.length);
        builder.append(bytes);
        builder.setEncoding(encoding);
        return builder;
    }

    public static TStringBuilder create(RubyString rubyString) {
        final RubyEncoding enc = rubyString.getEncodingUncached();
        return create(rubyString.tstring.getInternalByteArrayUncached(enc.tencoding), enc);
    }

    public static TStringBuilder create(byte[] bytes) {
        final TStringBuilder builder = new TStringBuilder(bytes.length);
        builder.append(bytes);
        return builder;
    }

    public static TStringBuilder create(byte[] bytes, int index, int len) {
        final TStringBuilder builder = new TStringBuilder(len);
        builder.append(bytes, index, len);
        return builder;
    }

    public static TStringBuilder create(InternalByteArray bytes) {
        return create(bytes.getArray(), bytes.getOffset(), bytes.getLength());
    }

    public static TStringBuilder create(InternalByteArray bytes, RubyEncoding encoding) {
        var builder = create(bytes);
        builder.setEncoding(encoding);
        return builder;
    }

    public RubyEncoding getRubyEncoding() {
        return encoding;
    }

    public Encoding getEncoding() {
        return encoding.jcoding;
    }

    public void setEncoding(RubyEncoding encoding) {
        this.encoding = encoding;
    }

    public TruffleString toTString() {
        return TStringUtils.fromByteArray(getBytes(), encoding);
    }

    /** All callers of this method must guarantee that the builder's byte array cannot change after this call, otherwise
     * the TruffleString built from the builder will end up in an inconsistent state. */
    public TruffleString toTStringUnsafe(TruffleString.FromByteArrayNode fromByteArrayNode) {
        return fromByteArrayNode.execute(getUnsafeBytes(), 0, getLength(), encoding.tencoding, false);
    }

    public TStringWithEncoding toTStringWithEnc() {
        return new TStringWithEncoding(TStringUtils.fromByteArray(getBytes(), encoding), encoding);
    }

}
