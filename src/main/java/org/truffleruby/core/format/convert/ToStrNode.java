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
package org.truffleruby.core.format.convert;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.exceptions.NoImplicitConversionException;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.library.RubyStringLibrary;

@NodeChild("value")
public abstract class ToStrNode extends FormatNode {

    public abstract Object execute(Object object);

    @Specialization
    RubyString coerceRubyString(RubyString string) {
        return string;
    }

    @Specialization
    ImmutableRubyString coerceImmutableRubyString(ImmutableRubyString string) {
        return string;
    }

    @Specialization(guards = "isNotRubyString(object)")
    Object coerceObject(Object object,
            @Cached InlinedBranchProfile errorProfile,
            @Cached DispatchNode toStrNode,
            @Cached RubyStringLibrary libString) {
        final Object coerced;

        try {
            coerced = toStrNode.call(object, "to_str");
        } catch (RaiseException e) {
            errorProfile.enter(this);
            if (e.getException().getLogicalClass() == coreLibrary().noMethodErrorClass) {
                throw new NoImplicitConversionException(object, "String");
            } else {
                throw e;
            }
        }

        if (libString.isRubyString(this, coerced)) {
            return coerced;
        } else {
            errorProfile.enter(this);
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeErrorBadCoercion(object, "String", "to_str", coerced, this));
        }
    }

}
