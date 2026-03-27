/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class FreezeHashKeyIfNeededNode extends RubyBaseNode {

    public abstract Object executeFreezeIfNeeded(Node node, Object key, boolean compareByIdentity);

    @Specialization
    static Object immutable(ImmutableRubyString string, boolean compareByIdentity) {
        return string;
    }

    @Specialization(guards = "string.isFrozen()")
    static Object alreadyFrozen(RubyString string, boolean compareByIdentity) {
        return string;
    }

    @Specialization(guards = { "!string.isFrozen()", "!compareByIdentity" })
    static Object dupAndFreeze(RubyString string, boolean compareByIdentity,
            @Cached(inline = false) DispatchNode dupNode) {
        final RubyString copy = (RubyString) dupNode.call(string, "dup");
        copy.freeze();
        return copy;
    }

    @Specialization(guards = { "!string.isFrozen()", "compareByIdentity" })
    static Object compareByIdentity(RubyString string, boolean compareByIdentity) {
        return string;
    }

    @Specialization(guards = "isNotRubyString(value)")
    static Object passThrough(Object value, boolean compareByIdentity) {
        return value;
    }
}
