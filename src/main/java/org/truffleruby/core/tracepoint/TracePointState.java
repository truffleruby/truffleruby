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
package org.truffleruby.core.tracepoint;

import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.Nil;
import org.truffleruby.language.methods.InternalMethod;

public final class TracePointState {

    boolean insideProc = false;
    RubySymbol event = null;
    /** The RubyString path, or nil for events without a location like :thread_begin */
    Object path = Nil.INSTANCE;
    int line = 0;
    /** The RubyBinding, or nil for events without a frame like :thread_begin */
    Object binding = Nil.INSTANCE;
    Object self = Nil.INSTANCE;
    /** The InternalMethod of the frame, or null for events without a frame like :thread_begin */
    InternalMethod method = null;

}
