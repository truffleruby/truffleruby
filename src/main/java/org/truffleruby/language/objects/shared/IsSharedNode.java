/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.Shape;

// Splitting: not worth splitting given the generic specialization is fast enough
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class IsSharedNode extends RubyBaseNode {

    public abstract boolean execute(Node node, RubyDynamicObject object);

    @Specialization(guards = "object.getShape() == cachedShape", limit = "1")
    static boolean isShareCached(RubyDynamicObject object,
            @Cached("object.getShape()") Shape cachedShape,
            @Cached("cachedShape.isShared()") boolean shared) {
        return shared;
    }

    @Specialization(replaces = "isShareCached")
    static boolean isSharedUncached(Node node, RubyDynamicObject object,
            @Cached InlinedConditionProfile profile) {
        return getLanguage(node).options.SHARED_OBJECTS_ENABLED &&
                profile.profile(node, SharedObjects.isShared(object));
    }

}
