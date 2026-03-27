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
package org.truffleruby.core.array;

import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;


/** Copies a portion of an array to another array, whose store is known to have sufficient capacity, and to be
 * compatible with the source array's store.
 * <p>
 * This never checks the array's size, which may therefore be adjusted afterwards.
 * <p>
 * Also propagates sharing from the source array to destination array.
 * <p>
 * Typically only called after {@link ArrayPrepareForCopyNode} has been invoked on the destination. */
@ImportStatic(ArrayGuards.class)
@ReportPolymorphism // for ArrayStoreLibrary
public abstract class ArrayCopyCompatibleRangeNode extends RubyBaseNode {

    public static ArrayCopyCompatibleRangeNode create() {
        return ArrayCopyCompatibleRangeNodeGen.create();
    }

    public abstract void execute(Object dstStore, Object srcStore, int dstStart, int srcStart, int length);

    protected boolean noopGuard(Object dstStore, Object srcStore, int dstStart, int srcStart, int length) {
        return length == 0 || dstStore == srcStore && dstStart == srcStart;
    }

    @Specialization(guards = "noopGuard(dstStore, srcStore, dstStart, srcStart, length)")
    void noop(Object dstStore, Object srcStore, int dstStart, int srcStart, int length) {
    }

    @Specialization(
            guards = "!noopGuard(dstStore, srcStore, dstStart, srcStart, length)",
            limit = "storageStrategyLimit()")
    void copy(Object dstStore, Object srcStore, int dstStart, int srcStart, int length,
            @CachedLibrary("srcStore") ArrayStoreLibrary stores) {
        stores.copyContents(srcStore, srcStart, dstStore, dstStart, length);
    }
}
