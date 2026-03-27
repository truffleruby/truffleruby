/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.language.RubyBaseNode;

@GenerateInline
@GenerateCached(false)
public abstract class LazySingletonClassNode extends RubyBaseNode {

    public final SingletonClassNode get(Node node) {
        return execute(node);
    }

    protected abstract SingletonClassNode execute(Node node);

    @Specialization
    static SingletonClassNode doLazy(
            @Cached(inline = false) SingletonClassNode singletonClassNode) {
        return singletonClassNode;
    }
}
