/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.cext.ValueWrapper;
import org.truffleruby.core.klass.RubyClass;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.truffleruby.language.objects.ObjectIDOperations;

import static org.truffleruby.cext.ValueWrapperManager.NIL_HANDLE;

/** The Ruby {@code nil}, the single instance of NilClass. */
@ExportLibrary(InteropLibrary.class)
public final class Nil extends ImmutableRubyObjectNotCopyable implements TruffleObject {

    public static final Nil INSTANCE = new Nil();

    private Nil() {
        this.valueWrapper = new ValueWrapper(this, NIL_HANDLE, null);
        this.objectId = ObjectIDOperations.NIL;
    }

    @Override
    public String toString() {
        return "nil";
    }

    // region InteropLibrary messages
    @Override
    @ExportMessage
    public String toDisplayString(boolean allowSideEffects) {
        return "nil";
    }

    @ExportMessage
    public boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    public RubyClass getMetaObject(
            @Bind Node node) {
        return RubyContext.get(node).getCoreLibrary().nilClass;
    }

    @ExportMessage
    public boolean isNull() {
        return true;
    }
    // endregion

}
