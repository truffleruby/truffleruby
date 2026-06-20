/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.interop.TranslateInteropExceptionNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class ToPointerAddressNode extends RubyBaseNode {

    public static long executeUncached(Object value) {
        return ToPointerAddressNodeGen.getUncached().execute(null, value);
    }

    public abstract long execute(Node node, Object value);

    @Specialization
    static long doLong(Node node, long value) {
        return value;
    }

    @Specialization(guards = { "!isImplicitLong(value)", "interop.isPointer(value)" }, limit = "getInteropCacheLimit()")
    static long interopPointer(Node node, Object value,
            @CachedLibrary("value") InteropLibrary interop,
            @Cached TranslateInteropExceptionNode translateInteropException) {
        long addressValue;
        try {
            addressValue = interop.asPointer(value);
        } catch (UnsupportedMessageException e) {
            throw translateInteropException.execute(node, e);
        }
        return addressValue;
    }

}
