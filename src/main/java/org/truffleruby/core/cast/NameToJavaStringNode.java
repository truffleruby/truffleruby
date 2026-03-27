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
package org.truffleruby.core.cast;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

/** Converts a method name to a Java String. The exception message below assumes this conversion is done for a method
 * name. */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class NameToJavaStringNode extends RubyBaseNode {

    public abstract String execute(Node node, Object name);

    public static String executeUncached(Object name) {
        return NameToJavaStringNodeGen.getUncached().execute(null, name);
    }

    @Specialization(guards = "libString.isRubyString(this, value)", limit = "1")
    static String stringNameToJavaString(Node node, Object value,
            @Cached @Exclusive RubyStringLibrary libString,
            @Cached @Shared ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.execute(node, value);
    }

    @Specialization
    static String symbolNameToJavaString(Node node, RubySymbol value,
            @Cached @Shared ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.execute(node, value);
    }

    @Specialization
    static String nameToJavaString(String value) {
        return value;
    }

    @Specialization(guards = { "!isString(object)", "!isRubySymbol(object)", "isNotRubyString(object)" })
    static String nameToJavaString(Node node, Object object,
            @Cached InlinedBranchProfile errorProfile,
            @Cached(inline = false) DispatchNode toStr,
            @Cached @Exclusive RubyStringLibrary libString,
            @Cached @Exclusive ToJavaStringNode toJavaStringNode) {
        final Object coerced;
        try {
            coerced = toStr.call(object, "to_str");
        } catch (RaiseException e) {
            errorProfile.enter(node);
            if (e.getException().getLogicalClass() == coreLibrary(node).noMethodErrorClass) {
                throw new RaiseException(getContext(node), coreExceptions(node).typeError(
                        Utils.concat(object, " is not a symbol nor a string"),
                        node));
            } else {
                throw e;
            }
        }

        if (libString.isRubyString(node, coerced)) {
            return toJavaStringNode.execute(node, coerced);
        } else {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).typeErrorBadCoercion(
                    object,
                    "String",
                    "to_str",
                    coerced,
                    node));
        }
    }
}
