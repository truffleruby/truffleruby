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
import static org.truffleruby.cext.ValueWrapperManager.UNSET_HANDLE;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.Layouts;
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
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.language.objects.ObjectIDOperations;

import java.lang.invoke.VarHandle;

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
    ValueWrapper wrapNonFixnum(long value) {
        return new ValueWrapper(value, UNSET_HANDLE, null);
    }

    @Specialization
    ValueWrapper wrapDouble(double value) {
        return new ValueWrapper(value, UNSET_HANDLE, null);
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
    ValueWrapper wrapImmutable(ImmutableRubyObject value,
            @Cached @Shared InlinedBranchProfile noHandleProfile) {
        ValueWrapper wrapper = value.getValueWrapper();
        if (wrapper == null) {
            noHandleProfile.enter(this);
            synchronized (value) {
                wrapper = value.getValueWrapper();
                if (wrapper == null) {
                    /* This is double-checked locking, but it's safe because the object that we create, the
                     * ValueWrapper, is not published until after a memory store fence. */
                    wrapper = new ValueWrapper(value, UNSET_HANDLE, null);
                    VarHandle.storeStoreFence();
                    value.setValueWrapper(wrapper);
                }
            }
        }
        return wrapper;
    }

    @Specialization(limit = "getDynamicObjectCacheLimit()")
    static ValueWrapper wrapValue(RubyDynamicObject value,
            @CachedLibrary("value") DynamicObjectLibrary objectLibrary,
            @Cached @Shared InlinedBranchProfile noHandleProfile,
            @Bind Node node) {
        ValueWrapper wrapper = (ValueWrapper) objectLibrary.getOrDefault(value, Layouts.VALUE_WRAPPER_IDENTIFIER, null);
        if (wrapper == null) {
            noHandleProfile.enter(node);
            synchronized (value) {
                wrapper = (ValueWrapper) objectLibrary.getOrDefault(value, Layouts.VALUE_WRAPPER_IDENTIFIER, null);
                if (wrapper == null) {
                    /* This is double-checked locking, but it's safe because the object that we create, the
                     * ValueWrapper, is not published until after a memory store fence. */
                    wrapper = new ValueWrapper(value, UNSET_HANDLE, null);
                    VarHandle.storeStoreFence();
                    objectLibrary.put(value, Layouts.VALUE_WRAPPER_IDENTIFIER, wrapper);
                }
            }
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
