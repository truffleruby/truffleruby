/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.string;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.SuppressFBWarnings;
import org.truffleruby.cext.CExtNodes;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.support.DetailedInspectingSupport;
import org.truffleruby.language.ImmutableRubyObjectCopyable;
import org.truffleruby.extra.ffi.Pointer;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;

import java.util.Objects;

/** All ImmutableRubyString are interned and must be created through {@link ImmutableStrings#get}. */
@ExportLibrary(InteropLibrary.class)
public final class ImmutableRubyString extends ImmutableRubyObjectCopyable
        implements TruffleObject, DetailedInspectingSupport {

    public final TruffleString tstring;
    public final RubyEncoding encoding;
    private Pointer nativeString = null;

    ImmutableRubyString(TruffleString tstring, RubyEncoding encoding) {
        assert tstring.isCompatibleToUncached(encoding.tencoding);
        assert tstring.isManaged();
        this.tstring = Objects.requireNonNull(tstring);
        this.encoding = Objects.requireNonNull(encoding);
    }

    /** should only be used for debugging */
    @Override
    public String toString() {
        return tstring.toString();
    }

    @Override
    public String toStringWithDetails() {
        return "\"" + tstring + "\" (" + encoding.name + ")";
    }

    public TruffleString asTruffleStringUncached() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        assert !tstring.isNative();
        return tstring;
    }

    public String getJavaString() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return TStringUtils.toJavaStringOrThrow(tstring, getEncodingUncached());
    }

    @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
    public boolean isNative() {
        return nativeString != null;
    }

    @SuppressFBWarnings("IS2_INCONSISTENT_SYNC")
    public Pointer getNativeString(RubyLanguage language, RubyContext context) {
        if (nativeString == null) {
            return createNativeString(language, context);
        }
        return nativeString;
    }

    @TruffleBoundary
    private synchronized Pointer createNativeString(RubyLanguage language, RubyContext context) {
        if (nativeString == null) {
            var tencoding = getEncodingUncached().tencoding;
            int byteLength = tstring.byteLength(tencoding);
            nativeString = CExtNodes.StringToNativeNode.allocateAndCopyToNative(language, context, tstring, tencoding,
                    byteLength, TruffleString.CopyToNativeMemoryNode.getUncached());
        }
        return nativeString;
    }

    public RubyEncoding getEncodingUncached() {
        CompilerAsserts.neverPartOfCompilation("Only behind @TruffleBoundary");
        return encoding;
    }

    public RubyEncoding getEncodingUnprofiled() {
        return encoding;
    }

    // region InteropLibrary messages
    @ExportMessage
    protected Object toDisplayString(boolean allowSideEffects,
            @Cached DispatchNode dispatchNode,
            @Cached KernelNodes.ToSNode kernelToSNode) {
        if (allowSideEffects) {
            return dispatchNode.call(this, "inspect");
        } else {
            return kernelToSNode.execute(this);
        }
    }

    @ExportMessage
    protected boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    protected RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().stringClass;
    }
    // endregion

    // region String messages
    @ExportMessage
    protected boolean isString() {
        return true;
    }

    @ExportMessage
    protected TruffleString asTruffleString() {
        assert !tstring.isNative();
        return tstring;
    }

    @ExportMessage
    @ReportPolymorphism // inline cache
    @ImportStatic(RubyBaseNode.class)
    public static final class AsString {
        @Specialization(
                guards = "equalNode.execute(string.tstring, libString.getEncoding(node, string), cachedTString, cachedEncoding)",
                limit = "getLimit()")
        static String asStringCached(ImmutableRubyString string,
                @Bind Node node,
                @Cached @Shared RubyStringLibrary libString,
                @Cached("string.asTruffleStringUncached()") TruffleString cachedTString,
                @Cached("string.getEncodingUncached()") RubyEncoding cachedEncoding,
                @Cached("string.getJavaString()") String javaString,
                @Cached StringHelperNodes.EqualNode equalNode) {
            return javaString;
        }

        @Specialization(replaces = "asStringCached")
        static String asStringUncached(ImmutableRubyString string,
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
        private static String getJavaStringBoundary(ImmutableRubyString string) {
            return string.getJavaString();
        }

        protected static int getLimit() {
            return RubyLanguage.getCurrentLanguage().options.INTEROP_CONVERT_CACHE;
        }
    }
    // endregion

}
