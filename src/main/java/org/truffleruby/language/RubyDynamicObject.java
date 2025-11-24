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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.IntegerCastNode;
import org.truffleruby.core.cast.LongCastNode;
import org.truffleruby.core.cast.ToLongNode;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.interop.ForeignToRubyArgumentsNode;
import org.truffleruby.interop.TranslateInteropRubyExceptionNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import org.truffleruby.language.methods.GetMethodObjectNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.TriState;
import org.truffleruby.language.objects.shared.SharedObjects;

import java.lang.invoke.MethodHandles;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_RETURN_MISSING;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC_RETURN_MISSING;

/** All Ruby DynamicObjects extend this. */
@ExportLibrary(InteropLibrary.class)
public abstract class RubyDynamicObject extends DynamicObject {

    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private RubyClass metaClass;

    public RubyDynamicObject(RubyClass metaClass, Shape shape) {
        super(shape);
        assert metaClass != null;
        this.metaClass = metaClass;
    }

    protected RubyDynamicObject(Shape classShape, String constructorOnlyForClassClass) {
        super(classShape);
        this.metaClass = (RubyClass) this;
    }

    public final RubyClass getMetaClass() {
        return metaClass;
    }

    public void setMetaClass(RubyClass metaClass) {
        SharedObjects.assertPropagateSharing(this, metaClass);
        this.metaClass = metaClass;
    }

    public final RubyClass getLogicalClass() {
        return metaClass.nonSingletonClass;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        final String className = getLogicalClass().fields.getName();
        return StringUtils.format("%s@%x<%s>", getClass().getSimpleName(), System.identityHashCode(this), className);
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

    @ExportMessage
    public Object toDisplayString(boolean allowSideEffects,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached KernelNodes.ToSNode kernelToSNode) {
        if (allowSideEffects) {
            Object inspect = dispatchNode.call(this, "inspect");
            if (inspect instanceof RubyString || inspect instanceof ImmutableRubyString) {
                return inspect;
            } else {
                return kernelToSNode.execute(this);
            }
        } else {
            return kernelToSNode.execute(this);
        }

    }

    // region Identity
    @ExportMessage
    public int identityHashCode() {
        return System.identityHashCode(this);
    }

    @ExportMessage
    public TriState isIdenticalOrUndefined(Object other,
            @Cached @Exclusive ConditionProfile rubyObjectProfile) {
        if (rubyObjectProfile.profile(other instanceof RubyDynamicObject)) {
            return TriState.valueOf(this == other);
        } else {
            return TriState.UNDEFINED;
        }
    }
    // endregion

    // region MetaObject
    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @Cached LogicalClassNode classNode) {
        return classNode.execute(this);
    }
    // endregion

    // region Array elements
    @ExportMessage
    public boolean hasArrayElements(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_has_array_elements?");
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public long getArraySize(
            @Cached IntegerCastNode integerCastNode,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Bind Node node)
            throws UnsupportedMessageException {
        Object value;
        try {
            value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_array_size");
        } catch (RaiseException e) {
            throw translateRubyException.execute(e);
        }
        if (value == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return integerCastNode.execute(node, value);
    }

    @ExportMessage
    public Object readArrayElement(long index,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Exclusive DispatchNode dispatchNode)
            throws InvalidArrayIndexException, UnsupportedMessageException {
        try {
            Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_read_array_element", index);
            if (value == DispatchNode.MISSING) {
                errorProfile.enter();
                throw UnsupportedMessageException.create();
            }
            return value;
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, index);
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Exclusive DispatchNode dispatchNode)
            throws UnsupportedMessageException, InvalidArrayIndexException, UnsupportedTypeException {
        try {
            Object result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_write_array_element", index,
                    value);
            if (result == DispatchNode.MISSING) {
                errorProfile.enter();
                throw UnsupportedMessageException.create();
            }
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, index, value);
        }

    }

    @ExportMessage
    public void removeArrayElement(long index,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Exclusive DispatchNode dispatchNode)
            throws UnsupportedMessageException, InvalidArrayIndexException {
        try {
            Object result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_remove_array_element", index);
            if (result == DispatchNode.MISSING) {
                errorProfile.enter();
                throw UnsupportedMessageException.create();
            }
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, index);
        }
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_array_element_readable?", index);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_array_element_modifiable?", index);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_array_element_insertable?", index);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_array_element_removable?", index);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }
    // endregion

    // region Hash entries
    @ExportMessage
    public boolean hasHashEntries(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_has_hash_entries?");
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public long getHashSize(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive ToLongNode toInt,
            @Bind Node node) throws UnsupportedMessageException {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_size");
        if (value == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return toInt.execute(node, value);
    }

    @ExportMessage
    public boolean isHashEntryReadable(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_entry_readable?", key);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isHashEntryModifiable(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_entry_modifiable?", key);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isHashEntryInsertable(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_entry_insertable?", key);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public boolean isHashEntryRemovable(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        final Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_entry_removable?", key);
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public Object readHashValue(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile)
            throws UnsupportedMessageException, UnknownKeyException {
        final Object value;
        try {
            value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_read_hash_entry", key);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, key);
        }
        if (value == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return value;
    }

    @ExportMessage
    public void writeHashEntry(Object key, Object value,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile)
            throws UnsupportedMessageException, UnknownKeyException {
        final Object result;
        try {
            result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_write_hash_entry", key, value);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, key);
        }
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public void removeHashEntry(Object key,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile)
            throws UnsupportedMessageException, UnknownKeyException {
        final Object result;
        try {
            result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_remove_hash_entry", key);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, key);
        }
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public Object getHashEntriesIterator(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared BranchProfile errorProfile) throws UnsupportedMessageException {
        final Object result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_entries_iterator");
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return result;
    }

    @ExportMessage
    public Object getHashKeysIterator(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared BranchProfile errorProfile) throws UnsupportedMessageException {
        final Object result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_keys_iterator");
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return result;
    }

    @ExportMessage
    public Object getHashValuesIterator(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared BranchProfile errorProfile) throws UnsupportedMessageException {
        final Object result = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_hash_values_iterator");
        if (result == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return result;
    }

    // endregion

    // region Iterable Messages
    @ExportMessage
    public boolean hasIterator(
            @Bind Node node,
            @Cached @Exclusive IsANode isANode) {
        return isANode.executeIsA(this, RubyContext.get(node).getCoreLibrary().enumerableModule);
    }

    @ExportMessage
    public Object getIterator(
            @CachedLibrary("this") InteropLibrary interopLibrary,
            @Bind Node node,
            @Cached @Exclusive DispatchNode dispatchNode) throws UnsupportedMessageException {
        if (!interopLibrary.hasIterator(this)) {
            throw UnsupportedMessageException.create();
        }
        final RubyContext context = RubyContext.get(node);
        return dispatchNode.call(context.getCoreLibrary().truffleInteropOperationsModule, "get_iterator", this);
    }

    @ExportMessage
    public boolean isIterator(
            @Bind Node node,
            @Cached @Exclusive IsANode isANode) {
        return isANode.executeIsA(this, RubyContext.get(node).getCoreLibrary().enumeratorClass);
    }

    @ExportMessage
    public boolean hasIteratorNextElement(
            @CachedLibrary("this") InteropLibrary interopLibrary,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) throws UnsupportedMessageException {
        if (!interopLibrary.isIterator(this)) {
            throw UnsupportedMessageException.create();
        }
        return booleanCastNode.execute(node,
                dispatchNode.call(
                        RubyContext.get(node).getCoreLibrary().truffleInteropOperationsModule,
                        "enumerator_has_next?", this));
    }

    @ExportMessage
    public Object getIteratorNextElement(
            @CachedLibrary("this") InteropLibrary interopLibrary,
            @Bind Node node,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive IsANode isANode,
            @Cached @Exclusive ConditionProfile stopIterationProfile)
            throws UnsupportedMessageException, StopIterationException {
        if (!interopLibrary.isIterator(this)) {
            throw UnsupportedMessageException.create();
        }
        try {
            return dispatchNode.call(this, "next");
        } catch (RaiseException e) {
            final RubyContext context = RubyContext.get(node);
            if (stopIterationProfile
                    .profile(isANode.executeIsA(e.getException(), context.getCoreLibrary().stopIterationClass))) {
                throw StopIterationException.create(e);
            }
            throw e;
        }
    }
    // endregion

    // region Pointer
    @ExportMessage
    public boolean isPointer(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {

        Object value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_pointer?");
        return value != DispatchNode.MISSING && booleanCastNode.execute(node, value);
    }

    @ExportMessage
    public long asPointer(
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached LongCastNode longCastNode,
            @Bind Node node) throws UnsupportedMessageException {

        Object value;
        try {
            value = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_as_pointer");
        } catch (RaiseException e) {
            throw translateRubyException.execute(e);
        }
        if (value == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return longCastNode.executeCastLong(node, value);
    }

    @ExportMessage
    public void toNative(
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive DispatchNode dispatchNode) {

        dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_to_native");
        // we ignore the method missing, toNative never throws
    }
    // endregion

    // region Members
    @ExportMessage
    public boolean hasMembers(
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_has_members?");
        return dynamic == DispatchNode.MISSING || booleanCastNode.execute(node, dynamic);
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

    private static boolean isIVar(String name) {
        return !name.isEmpty() && name.charAt(0) == '@';
    }

    @ExportMessage
    public Object readMember(String name,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached GetMethodObjectNode getMethodObjectNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared ConditionProfile ivarFoundProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile)
            throws UnknownIdentifierException, UnsupportedMessageException {
        Object dynamic;
        try {
            dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_read_member", name);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, name);
        }

        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            Object iVar = objectLibrary.getOrDefault(this, name, null);
            if (ivarFoundProfile.profile(iVar != null)) {
                return iVar;
            } else if (definedNode.execute(null, this, name)) {
                return getMethodObjectNode.execute(null, this, name, PRIVATE);
            } else {
                errorProfile.enter();
                throw UnknownIdentifierException.create(name);
            }
        } else {
            return dynamic;
        }
    }

    @ExportMessage
    public void writeMember(String name, Object value,
            @Cached WriteObjectFieldNode writeObjectFieldNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile,
            @Bind Node node)
            throws UnknownIdentifierException, UnsupportedMessageException {
        Object dynamic;
        try {
            dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_write_member", name, value);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, name);
        }

        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            if (isFrozenNode.execute(this)) {
                errorProfile.enter();
                throw UnsupportedMessageException.create();
            }
            if (isIVar(name)) {
                writeObjectFieldNode.execute(node, this, name, value);
            } else {
                errorProfile.enter();
                throw UnknownIdentifierException.create(name);
            }
        }
    }

    @ExportMessage
    public void removeMember(String name,
            @Cached @Exclusive DispatchNode removeInstanceVariableNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile,
            @CachedLibrary("this") InteropLibrary interopLibrary)
            throws UnknownIdentifierException, UnsupportedMessageException {
        Object dynamic;
        try {
            dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_remove_member", name);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, name);
        }

        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            if (!interopLibrary.isMemberRemovable(this, name)) {
                errorProfile.enter();
                throw UnknownIdentifierException.create(name);
            }
            try {
                removeInstanceVariableNode.call(this, "remove_instance_variable", name);
            } catch (RaiseException e) { // raises only if the name is missing
                // concurrent change in whether the member is removable
                errorProfile.enter();
                throw UnknownIdentifierException.create(name, e);
            }
        }
    }

    @ExportMessage
    public Object invokeMember(String name, Object[] arguments,
            @Cached @Exclusive DispatchNode dispatchDynamic,
            @Cached @Exclusive DispatchNode dispatchMember,
            @Cached @Exclusive ForeignToRubyArgumentsNode foreignToRubyArgumentsNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared TranslateInteropRubyExceptionNode translateRubyException,
            @Cached @Shared BranchProfile errorProfile,
            @Bind Node node)
            throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException, ArityException {
        Object[] convertedArguments = foreignToRubyArgumentsNode.executeConvert(node, arguments);
        Object[] combinedArguments = ArrayUtils.unshift(convertedArguments, name);
        Object dynamic;
        try {
            dynamic = dispatchDynamic.call(PRIVATE_RETURN_MISSING, this, "polyglot_invoke_member", combinedArguments);
        } catch (RaiseException e) {
            throw translateRubyException.execute(e, name, arguments);
        }

        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            Object result = dispatchMember.call(PRIVATE_RETURN_MISSING, this, name, convertedArguments);
            if (result == DispatchNode.MISSING) {
                errorProfile.enter();
                throw UnknownIdentifierException.create(name);
            }
            return result;
        }
        return dynamic;
    }

    @ExportMessage
    public boolean isMemberReadable(String name,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared ConditionProfile ivarFoundProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_readable?", name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            if (ivarFoundProfile.profile(objectLibrary.containsKey(this, name))) {
                return true;
            } else {
                return definedNode.execute(null, this, name);
            }
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean isMemberModifiable(String name,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_modifiable?", name);
        return isMemberModifiableRemovable(
                node,
                dynamic,
                name,
                isFrozenNode,
                objectLibrary,
                booleanCastNode,
                dynamicProfile);
    }

    @ExportMessage
    public boolean isMemberRemovable(String name,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_removable?", name);
        return isMemberModifiableRemovable(
                node,
                dynamic,
                name,
                isFrozenNode,
                objectLibrary,
                booleanCastNode,
                dynamicProfile);
    }

    private boolean isMemberModifiableRemovable(Node node, Object dynamic,
            String name,
            IsFrozenNode isFrozenNode,
            DynamicObjectLibrary objectLibrary,
            BooleanCastNode booleanCastNode,
            ConditionProfile dynamicProfile) {
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            if (isFrozenNode.execute(this)) {
                return false;
            } else {
                return objectLibrary.containsKey(this, name);
            }
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean isMemberInsertable(String name,
            @Cached @Shared IsFrozenNode isFrozenNode,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_insertable?", name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            if (isFrozenNode.execute(this) || !isIVar(name)) {
                return false;
            } else {
                return !objectLibrary.containsKey(this, name);
            }
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean isMemberInvocable(String name,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared ConditionProfile ivarFoundProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_invocable?", name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            Object iVar = objectLibrary.getOrDefault(this, name, null);
            if (ivarFoundProfile.profile(iVar != null)) {
                return false;
            } else {
                return definedNode.execute(null, this, name);
            }
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean isMemberInternal(String name,
            @CachedLibrary("this") DynamicObjectLibrary objectLibrary,
            @Cached @Shared InternalRespondToNode definedNode,
            @Cached(parameters = "PUBLIC") @Exclusive InternalRespondToNode definedPublicNode,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Shared ConditionProfile ivarFoundProfile,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_member_internal?", name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            Object result = objectLibrary.getOrDefault(this, name, null);
            if (ivarFoundProfile.profile(result != null)) {
                return true;
            } else {
                // defined but not publicly
                return definedNode.execute(null, this, name) &&
                        !definedPublicNode.execute(null, this, name);
            }
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String name,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_has_member_read_side_effects?",
                name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            return false;
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String name,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Shared ConditionProfile dynamicProfile,
            @Cached @Exclusive BooleanCastNode booleanCastNode,
            @Bind Node node) {
        Object dynamic = dispatchNode.call(PRIVATE_RETURN_MISSING, this, "polyglot_has_member_write_side_effects?",
                name);
        if (dynamicProfile.profile(dynamic == DispatchNode.MISSING)) {
            return false;
        } else {
            return booleanCastNode.execute(node, dynamic);
        }
    }
    // endregion

    // region Instantiable
    @ExportMessage
    public boolean isInstantiable(
            @Cached(parameters = "PUBLIC") @Exclusive InternalRespondToNode doesRespond) {
        return doesRespond.execute(null, this, "new");
    }

    @ExportMessage
    public Object instantiate(Object[] arguments,
            @Cached @Shared BranchProfile errorProfile,
            @Cached @Exclusive DispatchNode dispatchNode,
            @Cached @Exclusive ForeignToRubyArgumentsNode foreignToRubyArgumentsNode,
            @Bind Node node)
            throws UnsupportedMessageException {
        Object instance = dispatchNode.call(PUBLIC_RETURN_MISSING, this, "new",
                foreignToRubyArgumentsNode.executeConvert(node, arguments));

        // TODO (pitr-ch 28-Jan-2020): we should translate argument-error caused by bad arity to ArityException
        if (instance == DispatchNode.MISSING) {
            errorProfile.enter();
            throw UnsupportedMessageException.create();
        }
        return instance;
    }
    // endregion
    // endregion
}
