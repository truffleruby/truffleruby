/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.TriState;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.cext.ValueWrapper;
import org.truffleruby.interop.ForeignToRubyArgumentsNode;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.methods.GetMethodObjectNode;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;

/** A subset of messages from {@link org.truffleruby.language.RubyDynamicObject} for immutable objects. Such objects
 * have no instance variables, so the logic is simpler. We cannot easily reuse RubyDynamicObject messages since the
 * superclass differs.
 * <p>
 * Remember to implement toDisplayString, hasMetaObject and getMetaObject for each leaf class. And also add an object of
 * that class to {@code spec/truffle/interop/matrix_spec.rb}'s subjects.
 * <p>
 * Should not be subclassed directly, instead subclass {@link ImmutableRubyObjectCopyable} and
 * {@link ImmutableRubyObjectNotCopyable}. */
@ExportLibrary(InteropLibrary.class)
public abstract class ImmutableRubyObject implements TruffleObject {

    protected ValueWrapper valueWrapper;
    protected long objectId;

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public ValueWrapper getValueWrapper() {
        return valueWrapper;
    }

    public void setValueWrapper(ValueWrapper valueWrapper) {
        this.valueWrapper = valueWrapper;
    }

    // region InteropLibrary messages
    // Specs for these messages are in spec/truffle/interop/matrix_spec.rb
    @ExportMessage
    public boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    public Class<RubyLanguage> getLanguage() {
        return RubyLanguage.class;
    }

    @TruffleBoundary
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        throw new AbstractMethodError(); // Must be implemented by subclasses
    }

    // region Identity
    @ExportMessage
    public int identityHashCode() {
        return System.identityHashCode(this);
    }

    @ExportMessage
    public TriState isIdenticalOrUndefined(Object other,
            @Cached @Exclusive ConditionProfile immutableRubyObjectProfile) {
        if (immutableRubyObjectProfile.profile(other instanceof ImmutableRubyObject)) {
            return TriState.valueOf(this == other);
        } else {
            return TriState.UNDEFINED;
        }
    }
    // endregion

    // region Members
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public Object getMembers(boolean internal,
            @Bind Node node,
            @Cached @Exclusive DispatchNode dispatchNode) {
        return dispatchNode.call(
                RubyContext.get(node).getCoreLibrary().truffleInteropModule,
                // language=ruby prefix=Truffle::Interop.
                "get_members_implementation",
                this,
                internal);
    }

    @ExportMessage
    public boolean isMemberReadable(String name,
            @Cached @Shared InternalRespondToNode definedNode) {
        return definedNode.execute(null, this, name);
    }

    @ExportMessage
    public Object readMember(String name,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached GetMethodObjectNode getMethodObjectNode,
            @Cached @Shared BranchProfile errorProfile)
            throws UnknownIdentifierException {
        if (definedNode.execute(null, this, name)) {
            return getMethodObjectNode.execute(null, this, name, PRIVATE);
        } else {
            errorProfile.enter();
            throw UnknownIdentifierException.create(name);
        }
    }

    @ExportMessage
    public boolean isMemberInvocable(String name,
            @Cached @Shared InternalRespondToNode definedNode) {
        return definedNode.execute(null, this, name);
    }

    @ExportMessage
    public Object invokeMember(String name, Object[] arguments,
            @Cached @Exclusive DispatchNode dispatchMember,
            @Cached @Exclusive ForeignToRubyArgumentsNode foreignToRubyArgumentsNode,
            @Cached @Shared BranchProfile errorProfile,
            @Bind Node node)
            throws UnknownIdentifierException {
        Object[] convertedArguments = foreignToRubyArgumentsNode.executeConvert(node, arguments);
        Object result = dispatchMember.call(PRIVATE_RETURN_MISSING, this, name, convertedArguments);
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    @ExportMessage
    public boolean isMemberInternal(String name,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached(parameters = "PUBLIC") @Exclusive InternalRespondToNode definedPublicNode) {
        // defined but not publicly
        return definedNode.execute(null, this, name) &&
                !definedPublicNode.execute(null, this, name);
    }
    // endregion
    // endregion

}
