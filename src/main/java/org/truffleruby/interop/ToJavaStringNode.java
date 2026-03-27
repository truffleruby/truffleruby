/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.control.RaiseException;

@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class ToJavaStringNode extends RubyBaseNode {

    public abstract String execute(Node node, Object name);

    @Specialization(guards = "interopLibrary.isString(value)", limit = "getLimit()")
    static String interopString(Node node, Object value,
            @CachedLibrary("value") InteropLibrary interopLibrary,
            @Cached TranslateInteropExceptionNode translateInteropException) {
        try {
            return interopLibrary.asString(value);
        } catch (UnsupportedMessageException e) {
            throw translateInteropException.execute(node, e);
        }
    }

    @Specialization(guards = "!interopLibrary.isString(value)", limit = "getLimit()")
    static String notInteropString(Node node, Object value,
            @CachedLibrary("value") InteropLibrary interopLibrary) {
        throw new RaiseException(
                getContext(node),
                coreExceptions(node)
                        .typeError("This interop message requires a String or Symbol for the member name", node));
    }

    protected int getLimit() {
        return getLanguage().options.INTEROP_CONVERT_CACHE;
    }
}
