/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.IdentifierType;
import org.truffleruby.parser.Identifiers;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@ImportStatic(Identifiers.class)
@GenerateInline
@GenerateCached(false)
@ReportPolymorphism // inline cache
public abstract class CheckIVarNameNode extends RubyBaseNode {

    /** Pass both the j.l.String name and the original name, the original name can be faster to check and the j.l.String
     * name is needed by all callers so it is better for footprint that callers convert to j.l.String */
    public abstract void execute(Node node, Object object, String name, Object originalName);

    @Specialization
    static void checkSymbol(Node node, Object object, String name, RubySymbol originalName,
            @Cached @Shared InlinedBranchProfile errorProfile) {
        if (originalName.getType() != IdentifierType.INSTANCE) {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node),
                    getContext(node).getCoreExceptions().nameErrorInstanceNameNotAllowable(name, object, node));
        }
    }

    @Specialization(
            guards = { "name == cachedName", "isValidInstanceVariableName(cachedName)", "!isRubySymbol(originalName)" },
            limit = "getDynamicObjectCacheLimit()")
    static void cached(Object object, String name, Object originalName,
            @Cached("name") String cachedName) {
    }

    @Specialization(replaces = "cached", guards = "!isRubySymbol(originalName)")
    static void uncached(Node node, Object object, String name, Object originalName,
            @Cached @Shared InlinedBranchProfile errorProfile) {
        if (!Identifiers.isValidInstanceVariableName(name)) {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).nameErrorInstanceNameNotAllowable(
                    name,
                    object,
                    node));
        }
    }
}
