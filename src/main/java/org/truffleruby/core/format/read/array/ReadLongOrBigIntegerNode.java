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
package org.truffleruby.core.format.read.array;

import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.convert.ToLongNode;
import org.truffleruby.core.format.convert.ToLongNodeGen;
import org.truffleruby.core.format.read.SourceNode;
import org.truffleruby.core.numeric.RubyBignum;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "source", type = SourceNode.class)
@ImportStatic(ArrayGuards.class)
public abstract class ReadLongOrBigIntegerNode extends FormatNode {

    @Child private ToLongNode toLongNode;

    private final ConditionProfile bignumProfile = ConditionProfile.create();

    @Specialization(limit = "storageStrategyLimit()")
    Object read(VirtualFrame frame, Object source,
            @CachedLibrary("source") ArrayStoreLibrary sources) {
        final Object value = sources.read(source, advanceSourcePosition(frame));

        if (bignumProfile.profile(value instanceof RubyBignum)) {
            return value;
        } else {
            if (toLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toLongNode = insert(ToLongNodeGen.create(false, null));
            }

            return toLongNode.executeToLong(frame, value);
        }
    }

}
