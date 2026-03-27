/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.library.RubyStringLibrary;

import java.util.Objects;

@ExportLibrary(InteropLibrary.class)
@ImportStatic(RubyBaseNode.class)
public final class RubyString extends RubyDynamicObject {

    public boolean frozen;
    public boolean locked = false;
    public AbstractTruffleString tstring;
    private RubyEncoding encoding;

    public RubyString(
            RubyClass rubyClass,
            Shape shape,
            boolean frozen,
            AbstractTruffleString tstring,
            RubyEncoding rubyEncoding) {
        super(rubyClass, shape);
        assert tstring.isCompatibleToUncached(rubyEncoding.tencoding);
        this.frozen = frozen;
        this.tstring = Objects.requireNonNull(tstring);
        this.encoding = Objects.requireNonNull(rubyEncoding);
    }

    public void setTString(AbstractTruffleString tstring) {
        assert tstring.isCompatibleToUncached(getEncodingUncached().tencoding);
        this.tstring = Objects.requireNonNull(tstring);
    }

    public void setTString(AbstractTruffleString tstring, RubyEncoding encoding) {
        assert tstring.isCompatibleToUncached(encoding.tencoding);
        this.tstring = Objects.requireNonNull(tstring);
        this.encoding = Objects.requireNonNull(encoding);
    }

    public void clearCodeRange() {
        assert tstring.isNative();
        ((MutableTruffleString) tstring).notifyExternalMutation();
    }

    /** should only be used for debugging */
    @Override
    public String toString() {
        return tstring.toString();
    }

    public TruffleString asTruffleStringUncached() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.asTruffleStringUncached(getEncodingUncached().tencoding);
    }

    public String getJavaString() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return TStringUtils.toJavaStringOrThrow(tstring, getEncodingUncached());
    }

    public int byteLengthUncached() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return tstring.byteLength(getEncodingUncached().tencoding);
    }

    public RubyEncoding getEncodingUncached() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return encoding;
    }

    public RubyEncoding getEncodingUnprofiled() {
        return encoding;
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    // region String messages
    @ExportMessage
    protected boolean isString() {
        return true;
    }

    @ExportMessage
    protected TruffleString asTruffleString(
            @Cached TruffleString.AsTruffleStringNode asTruffleStringNode) {
        return asTruffleStringNode.execute(tstring, encoding.tencoding);
    }

    @ExportMessage
    @ReportPolymorphism // inline cache
    @ImportStatic(RubyBaseNode.class)
    public static final class AsString {
        @Specialization(
                guards = "equalNode.execute(string.tstring, libString.getEncoding(node, string), cachedTString, cachedEncoding)",
                limit = "getLimit()")
        static String asStringCached(RubyString string,
                @Bind Node node,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("string.asTruffleStringUncached()") TruffleString cachedTString,
                @Cached("string.getEncodingUncached()") RubyEncoding cachedEncoding,
                @Cached("string.getJavaString()") String javaString,
                @Cached StringHelperNodes.EqualNode equalNode) {
            return javaString;
        }

        @Specialization(replaces = "asStringCached")
        static String asStringUncached(RubyString string,
                @Cached @Shared RubyStringLibrary libString,
                @Cached TruffleString.GetByteCodeRangeNode codeRangeNode,
                @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                @Cached InlinedConditionProfile binaryNonAsciiProfile,
                @Bind Node node) {
            var encoding = libString.getEncoding(node, string);
            if (binaryNonAsciiProfile.profile(node, encoding == Encodings.BINARY &&
                    !StringGuards.is7Bit(string.tstring, encoding, codeRangeNode))) {
                return getJavaStringBoundary(string);
            } else {
                return toJavaStringNode.execute(string.tstring);
            }
        }

        @TruffleBoundary
        private static String getJavaStringBoundary(RubyString string) {
            return string.getJavaString();
        }

        protected static int getLimit() {
            return RubyLanguage.getCurrentLanguage().options.INTEROP_CONVERT_CACHE;
        }
    }
    // endregion

}
