/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.platform;

import org.truffleruby.RubyContext;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.interop.TranslateInteropExceptionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public final class TruffleNFIPlatform {

    final Object defaultLibrary;

    private final String size_t;
    private final Object strlen;
    private final Object strnlen;

    public TruffleNFIPlatform(RubyContext context) {
        defaultLibrary = context
                .getEnv()
                .parseInternal(Source.newBuilder("nfi", "default", "native").build())
                .call();

        size_t = resolveType(context.getNativeConfiguration(), "size_t");
        strlen = getFunction(context, "strlen", String.format("(pointer):%s", size_t));
        strnlen = getFunction(context, "strnlen", String.format("(pointer,%s):%s", size_t, size_t));
    }

    public Object getDefaultLibrary() {
        return defaultLibrary;
    }

    public Object lookup(Object library, String name) {
        try {
            return InteropLibrary.getFactory().getUncached(library).readMember(library, name);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw TranslateInteropExceptionNode.executeUncached(e);
        }
    }

    public static Object bind(RubyContext context, Object function, String signature) {
        Object parsedSignature = context
                .getEnv()
                .parseInternal(Source.newBuilder("nfi", signature, "native").build())
                .call();
        return SignatureLibrary.getUncached().bind(parsedSignature, function);
    }

    public long asPointer(Object object) {
        try {
            return InteropLibrary.getFactory().getUncached(object).asPointer(object);
        } catch (UnsupportedMessageException e) {
            throw TranslateInteropExceptionNode.executeUncached(e);
        }
    }

    public Object resolveTypeRaw(NativeConfiguration nativeConfiguration, String type) {
        final Object typedef = nativeConfiguration.get("platform.typedef." + type);
        if (typedef == null) {
            throw CompilerDirectives.shouldNotReachHere("Type " + type + " is not defined in the native configuration");
        }
        return typedef;
    }

    public String resolveType(NativeConfiguration nativeConfiguration, String type) {
        final Object typedef = resolveTypeRaw(nativeConfiguration, type);
        return toNFIType(StringOperations.getJavaString(typedef));
    }

    private String toNFIType(String type) {
        switch (type) {
            case "uint":
                return "uint32";
            case "ulong":
                return "uint64";
            default:
                return type;
        }
    }

    public Object getFunction(RubyContext context, String functionName, String signature) {
        final Object symbol = lookup(defaultLibrary, functionName);
        return bind(context, symbol, signature);
    }

    public String size_t() {
        return size_t;
    }

    public Object getStrlen() {
        return strlen;
    }

    public Object getStrnlen() {
        return strnlen;
    }

}
