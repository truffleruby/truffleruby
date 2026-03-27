/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

/** Casts a value into a boolean. */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class BooleanCastNode extends RubyBaseNode {

    public static boolean executeUncached(Object value) {
        return BooleanCastNodeGen.getUncached().execute(null, value);
    }

    public abstract boolean execute(Node node, Object value);

    @Specialization
    static boolean doNil(Nil nil) {
        return false;
    }

    @Specialization
    static boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    static boolean doInt(int value) {
        return true;
    }

    @Specialization
    static boolean doLong(long value) {
        return true;
    }

    @Specialization
    static boolean doFloat(double value) {
        return true;
    }

    @Specialization
    static boolean doBasicObject(RubyDynamicObject object) {
        return true;
    }

    @Specialization(guards = "!isNil(object)")
    static boolean doImmutableObject(ImmutableRubyObject object) {
        return true;
    }

    @Specialization(guards = "isForeignObject(object)", limit = "getCacheLimit()")
    static boolean doForeignObject(Node node, Object object,
            @CachedLibrary("object") InteropLibrary objects,
            @Cached InlinedConditionProfile isNullProfile,
            @Cached InlinedConditionProfile isBooleanProfile,
            @Cached InlinedBranchProfile failed) {

        if (isNullProfile.profile(node, objects.isNull(object))) {
            return false;
        } else {
            if (isBooleanProfile.profile(node, objects.isBoolean(object))) {
                try {
                    return objects.asBoolean(object);
                } catch (UnsupportedMessageException e) {
                    failed.enter(node);
                    // it concurrently stopped being boolean
                    return true;
                }
            } else {
                return true;
            }
        }
    }

    protected int getCacheLimit() {
        return getLanguage().options.METHOD_LOOKUP_CACHE;
    }
}
