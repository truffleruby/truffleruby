/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2024-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;

public final class ArrayStaticLiteralNode extends RubyContextSourceNode {

    @CompilationFinal(dimensions = 1) private final Object[] values;

    public ArrayStaticLiteralNode(Object[] values) {
        assert allValuesArePrimitiveOrImmutable(values);
        this.values = values;
    }

    private static boolean allValuesArePrimitiveOrImmutable(Object[] values) {
        for (Object value : values) {
            assert RubyGuards.isPrimitiveOrImmutable(value);
        }
        return true;
    }

    @Override
    public RubyArray execute(VirtualFrame frame) {
        // Copying here is the simplest since we need to return a mutable Array.
        // An alternative would be to use COW via DelegatedArrayStorage.
        return createArray(ArrayUtils.copy(values));
    }

    @Override
    public RubyNode cloneUninitialized() {
        return new ArrayStaticLiteralNode(values);
    }

}
