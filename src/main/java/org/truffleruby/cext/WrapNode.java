/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.cext;

import static org.truffleruby.cext.ValueWrapperManager.LONG_TAG;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.objects.ObjectIDOperations;

import org.truffleruby.cext.ValueWrapperManager.CreateWrapperNode;

@ImportStatic(ObjectIDOperations.class)
@GenerateUncached
public abstract class WrapNode extends RubyBaseNode {

    @NeverDefault
    public static WrapNode create() {
        return WrapNodeGen.create();
    }

    public abstract ValueWrapper execute(Object value);

    @Specialization(guards = "isSmallFixnum(value)")
    ValueWrapper wrapFixnum(long value) {
        long val = (value << 1) | LONG_TAG;
        return new ValueWrapper(null, val, null);
    }

    @Specialization(guards = "!isSmallFixnum(value)")
    static ValueWrapper wrapNonFixnum(long value,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Bind Node node) {
        return createWrapperNode.execute(node, value);
    }

    @Specialization
    static ValueWrapper wrapDouble(double value,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Bind Node node) {
        return createWrapperNode.execute(node, value);
    }

    @Specialization
    ValueWrapper wrapBoolean(boolean value) {
        return value
                ? getContext().getValueWrapperManager().trueWrapper
                : getContext().getValueWrapperManager().falseWrapper;
    }

    @Specialization
    ValueWrapper wrapUndef(NotProvided value) {
        return getContext().getValueWrapperManager().undefWrapper;
    }

    @Specialization
    ValueWrapper wrapWrappedValue(ValueWrapper value,
            @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        var message = createString(fromJavaStringNode, "Wrapping wrapped object", Encodings.UTF_8);
        throw new RaiseException(getContext(), coreExceptions().argumentError(message, this, null));
    }

    @Specialization
    ValueWrapper wrapNil(Nil value) {
        return value.getValueWrapper();
    }

    @Specialization(guards = "!isNil(value)")
    static ValueWrapper wrapImmutable(ImmutableRubyObject value,
            @Cached @Shared InlinedBranchProfile noHandleProfile,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Bind Node node) {
        ValueWrapper wrapper = value.getValueWrapper();
        if (wrapper == null) {
            noHandleProfile.enter(node);
            /* The racy initial read is safe because ValueWrapper only has final fields, so it sees either null or a
             * fully-initialized ValueWrapper. If two threads race, one wrapper wins and the other remains unused in its
             * HandleBlock, which is harmless. */
            wrapper = value.setValueWrapperIfAbsent(createWrapperNode.execute(node, value));
        }
        return wrapper;
    }

    @Specialization
    static ValueWrapper wrapValue(RubyDynamicObject value,
            @Cached @Shared InlinedBranchProfile noHandleProfile,
            @Cached @Shared CreateWrapperNode createWrapperNode,
            @Bind Node node) {
        ValueWrapper wrapper = value.getValueWrapper();
        if (wrapper == null) {
            noHandleProfile.enter(node);
            /* The racy initial read is safe because ValueWrapper only has final fields, so it sees either null or a
             * fully-initialized ValueWrapper. If two threads race, one wrapper wins and the other remains unused in its
             * HandleBlock, which is harmless. */
            wrapper = value.setValueWrapperIfAbsent(createWrapperNode.execute(node, value));
        }
        return wrapper;
    }

    @Specialization(guards = "isForeignObject(value)")
    ValueWrapper wrapNonRubyObject(Object value) {
        throw new RaiseException(
                getContext(),
                coreExceptions().argumentError("Attempt to wrap something that isn't an Ruby object", this));
    }
}
