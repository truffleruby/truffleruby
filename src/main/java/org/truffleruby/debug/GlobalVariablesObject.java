/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.globals.GlobalVariables;
import org.truffleruby.parser.Identifiers;

/** Ruby does not provide a way to set global variables with metaprogramming, except via eval(). Since the logic to
 * access global variables is far from trivial, we use eval() here too, after validating that it's a valid global
 * variable name. */
@ExportLibrary(InteropLibrary.class)
public final class GlobalVariablesObject implements TruffleObject {

    private final GlobalVariables globalVariables;

    public GlobalVariablesObject(GlobalVariables globalVariables) {
        this.globalVariables = globalVariables;
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    protected Object getMembers(boolean includeInternal) {
        return new VariableNamesObject(globalVariables.keys());
    }

    @ExportMessage
    @TruffleBoundary
    protected Object readMember(String member,
            @Bind Node node,
            @Cached @Exclusive DispatchNode evalNode) throws UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        } else {
            final RubyLanguage language = RubyLanguage.get(node);
            final RubyContext context = RubyContext.get(node);
            final RubyString string = StringOperations.createUTF8String(context, language, member);

            return evalNode.call(context.getCoreLibrary().topLevelBinding, "eval", string);
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected void writeMember(String member, Object value,
            @Bind Node node,
            @Cached @Exclusive DispatchNode evalNode,
            @Cached @Exclusive DispatchNode callNode) throws UnknownIdentifierException {
        if (!isValidGlobalVariableName(member)) {
            throw UnknownIdentifierException.create(member);
        } else {
            final RubyLanguage language = RubyLanguage.get(node);
            final RubyContext context = RubyContext.get(node);
            final String code = "-> value { " + member + " = value }";
            final RubyString string = StringOperations.createUTF8String(context, language, code);
            final Object lambda = evalNode.call(context.getCoreLibrary().topLevelBinding, "eval", string);

            callNode.call(lambda, "call", value);
        }
    }

    @ExportMessage(name = "hasMemberReadSideEffects")
    @ExportMessage(name = "hasMemberWriteSideEffects")
    @TruffleBoundary
    protected boolean hasMemberSideEffects(String member,
            @Bind Node node) {
        if (isMemberReadable(member)) {
            final RubyLanguage language = RubyLanguage.get(node);
            final RubyContext context = RubyContext.get(node);

            int index = language.getGlobalVariableIndex(member);
            var storage = context.getGlobalVariableStorage(index);
            return storage.hasHooks();
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMemberReadable(String member) {
        return isValidGlobalVariableName(member) && globalVariables.contains(member);
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMemberModifiable(String member) {
        return isValidGlobalVariableName(member) && globalVariables.contains(member);
    }

    @ExportMessage
    @TruffleBoundary
    protected boolean isMemberInsertable(String member) {
        return isValidGlobalVariableName(member) && !globalVariables.contains(member);
    }

    private static boolean isValidGlobalVariableName(String name) {
        return Identifiers.isValidGlobalVariableName(name);
    }

}
