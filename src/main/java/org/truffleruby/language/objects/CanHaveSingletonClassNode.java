/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2013-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.RubyBaseNode;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class CanHaveSingletonClassNode extends RubyBaseNode {

    public static boolean executeUncached(Object value) {
        return CanHaveSingletonClassNodeGen.getUncached().execute(null, value);
    }

    public abstract boolean execute(Node node, Object value);

    @Specialization
    static boolean canHaveSingletonClass(int value) {
        return false;
    }

    @Specialization
    static boolean canHaveSingletonClass(long value) {
        return false;
    }

    @Specialization
    static boolean canHaveSingletonClass(double value) {
        return false;
    }

    @Specialization(guards = "!isNil(value)")
    static boolean canHaveSingletonClass(ImmutableRubyObject value) {
        return false;
    }

    @Fallback
    static boolean fallback(Object value) {
        return true;
    }

}

