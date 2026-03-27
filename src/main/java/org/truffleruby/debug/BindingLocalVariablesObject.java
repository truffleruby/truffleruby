/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.Identifiers;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class BindingLocalVariablesObject implements TruffleObject {

    @CompilationFinal private RubyBinding binding;

    public BindingLocalVariablesObject() {
    }

    public void setBinding(RubyBinding binding) {
        this.binding = binding;
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    protected Object getMembers(boolean includeInternal) {
        if (binding == null) { // Not set yet
            return new VariableNamesObject(StringUtils.EMPTY_STRING_ARRAY);
        }

        String[] variables = BindingNodes.LocalVariablesNode
                // There should be no duplicates since there is no scope above
                .listLocalVariablesWithDuplicates(binding.getFrame(), null)
                .toArray(StringUtils.EMPTY_STRING_ARRAY);
        return new VariableNamesObject(variables);
    }

    @ExportMessage
    protected Object readMember(String member,
            @Cached @Exclusive BindingNodes.LocalVariableGetNode localVariableGetNode,
            @Bind Node node)
            throws UnknownIdentifierException {
        try {
            return localVariableGetNode.execute(node, binding, member);
        } catch (RaiseException e) {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
            @Cached BindingNodes.LocalVariableSetNode localVariableSetNode,
            @Bind Node node) throws UnknownIdentifierException {
        if (isValidLocalVariableName(member)) {
            localVariableSetNode.execute(node, binding, member, value);
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    protected boolean memberExists(String member,
            @Cached @Exclusive BindingNodes.HasLocalVariableNode hasLocalVariableNode,
            @Bind Node node) {
        return hasLocalVariableNode.execute(node, binding, member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member,
            @CachedLibrary("this") InteropLibrary interopLibrary) {
        return isValidLocalVariableName(member) && !interopLibrary.isMemberModifiable(this, member);
    }

    private static boolean isValidLocalVariableName(String name) {
        return Identifiers.isValidLocalVariableName(name);
    }

}
