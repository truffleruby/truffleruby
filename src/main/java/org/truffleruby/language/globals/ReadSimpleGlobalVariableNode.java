/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.globals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.WarningNode;

public abstract class ReadSimpleGlobalVariableNode extends RubyBaseNode {

    public final String name;
    @Child LookupGlobalVariableStorageNode lookupGlobalVariableStorageNode;

    @NeverDefault
    public static ReadSimpleGlobalVariableNode create(String name) {
        return ReadSimpleGlobalVariableNodeGen.create(name);
    }

    public ReadSimpleGlobalVariableNode(String name) {
        this.name = name;
    }

    public abstract Object execute();

    @Specialization(
            guards = "isSingleContext()",
            assumptions = {
                    "storage.getUnchangedAssumption()",
                    "getLanguage().getGlobalVariableNeverAliasedAssumption(index)" })
    Object readConstant(
            @Cached(value = "getLanguage().getGlobalVariableIndex(name)", neverDefault = false) int index,
            @Cached("getContext().getGlobalVariableStorage(index)") GlobalVariableStorage storage,
            @Cached("storage.getValue()") Object value,
            @Cached("new()") @Shared WarningNode warningNode,
            @Cached(value = "storage.isDefined()", neverDefault = false) boolean isDefined) {
        if (!isDefined && warningNode.shouldWarn()) {
            SourceSection sourceSection = getEncapsulatingSourceSection();
            String message = globalVariableNotInitializedMessageFor(name);

            warningNode.warningMessage(sourceSection, message);
        }

        return value;
    }

    @Specialization
    Object read(
            @Cached("new()") @Shared WarningNode warningNode,
            @Cached InlinedConditionProfile isDefinedProfile) {
        if (lookupGlobalVariableStorageNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupGlobalVariableStorageNode = insert(LookupGlobalVariableStorageNode.create(name));
        }

        final GlobalVariableStorage storage = lookupGlobalVariableStorageNode.execute(null);

        // don't use storage.getValue() and storage.isDefined() to avoid
        // accessing volatile storage.value several times
        final Object rawValue = storage.getRawValue();

        if (isDefinedProfile.profile(this, rawValue != GlobalVariableStorage.UNSET_VALUE)) {
            return rawValue;
        } else {
            if (warningNode.shouldWarn()) {
                SourceSection sourceSection = getEncapsulatingSourceSection();
                String message = globalVariableNotInitializedMessageFor(name);

                warningNode.warningMessage(sourceSection, message);
            }

            return Nil.INSTANCE;
        }
    }

    private String globalVariableNotInitializedMessageFor(String name) {
        return StringUtils.format("global variable '%s' not initialized", name);
    }

}
