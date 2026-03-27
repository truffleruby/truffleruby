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
package org.truffleruby.language.objects.classvariables;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.parser.Identifiers;

@ImportStatic(Identifiers.class)
@GenerateInline
@GenerateCached(false)
@ReportPolymorphism // inline cache
public abstract class CheckClassVariableNameNode extends RubyBaseNode {

    public abstract void execute(Node node, RubyDynamicObject object, String name);

    @Specialization(guards = { "name == cachedName", "isValidClassVariableName(cachedName)" },
            limit = "getDefaultCacheLimit()")
    static void cached(RubyDynamicObject object, String name,
            @Cached("name") String cachedName) {
    }

    @Specialization(replaces = "cached")
    static void uncached(Node node, RubyDynamicObject object, String name,
            @Cached InlinedBranchProfile errorProfile) {
        if (!Identifiers.isValidClassVariableName(name)) {
            errorProfile.enter(node);
            throw new RaiseException(getContext(node), coreExceptions(node).nameErrorInstanceNameNotAllowable(
                    name,
                    object,
                    node));
        }
    }

}
