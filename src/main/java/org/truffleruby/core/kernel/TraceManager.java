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
package org.truffleruby.core.kernel;

import com.oracle.truffle.api.instrumentation.Tag;

/** The instrumentation tags used for TracePoint events, see {@link org.truffleruby.core.tracepoint.TracePointEvent} */
public abstract class TraceManager {
    public static final class LineTag extends Tag {
    }

    public static final class CallTag extends Tag {
    }

    public static final class CCallTag extends Tag {
    }

    /** Applies to the same nodes as {@link CallTag}, as the :return event is for the return of methods defined in
     * Ruby */
    public static final class ReturnTag extends Tag {
    }
}
