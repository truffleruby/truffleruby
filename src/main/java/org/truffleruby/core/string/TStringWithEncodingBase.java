/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

import java.util.Objects;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CreateCodePointIteratorNode;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleStringIterator;

abstract class TStringWithEncodingBase {

    public final AbstractTruffleString tstring;
    public final RubyEncoding encoding;

    protected TStringWithEncodingBase(AbstractTruffleString tstring, RubyEncoding encoding) {
        assert tstring.isCompatibleToUncached(encoding.tencoding);
        this.tstring = tstring;
        this.encoding = encoding;
    }

    public abstract TStringWithEncoding asImmutable();

    public final RubyEncoding getEncoding() {
        return encoding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        TStringWithEncodingBase that = (TStringWithEncodingBase) o;
        return encoding == that.encoding && tstring.equals(that.tstring);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tstring, encoding);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + tstring.toStringDebug();
    }

    public int byteLength() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.byteLength(encoding.tencoding);
    }

    public int characterLength() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.codePointLengthUncached(encoding.tencoding);
    }

    public InternalByteArray getInternalByteArray() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.getInternalByteArrayUncached(encoding.tencoding);
    }

    public TruffleString.CodeRange getCodeRange() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.getByteCodeRangeUncached(encoding.tencoding);
    }

    public TStringWithEncoding forceEncoding(RubyEncoding newEncoding) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return new TStringWithEncoding(tstring.forceEncodingUncached(encoding.tencoding, newEncoding.tencoding),
                newEncoding);
    }

    public boolean isAsciiOnly() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return StringGuards.is7BitUncached(tstring, encoding);
    }

    public int get(int index) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.readByteUncached(index, encoding.tencoding);
    }

    public byte getByte(int index) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return (byte) tstring.readByteUncached(index, encoding.tencoding);
    }

    public TStringWithEncoding substring(int byteOffset, int length) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return new TStringWithEncoding(tstring.substringByteIndexUncached(byteOffset, length, encoding.tencoding, true),
                encoding);
    }

    public TruffleString substringAsTString(int byteOffset, int length) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.substringByteIndexUncached(byteOffset, length, encoding.tencoding, true);
    }

    public String toJavaString() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.toJavaStringUncached();
    }

    public String toJavaStringOrThrow() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return TStringUtils.toJavaStringOrThrow(tstring, encoding);
    }

    public TruffleStringIterator createCodePointIterator() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return CreateCodePointIteratorNode.getUncached().execute(tstring, encoding.tencoding,
                ErrorHandling.RETURN_NEGATIVE);
    }

    public boolean isSingleByteOptimizable() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return isAsciiOnly() || encoding.isSingleByte;
    }

    public byte[] getBytesCopy() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.copyToByteArrayUncached(encoding.tencoding);
    }

    public byte[] getBytesOrCopy() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return TStringUtils.getBytesOrCopy(tstring, encoding);
    }

    /** byteOffset is logical, recoverIfBroken=false */
    public int characterLength(int byteOffset) {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.byteLengthOfCodePointUncached(byteOffset, encoding.tencoding, ErrorHandling.RETURN_NEGATIVE);
    }

}
