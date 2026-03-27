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
package org.truffleruby.annotations;

public enum Split {
    /** Special value for the CoreMethod annotation, resolved to either HEURISTIC or ALWAYS, based on needsBlock */
    DEFAULT,
    ALWAYS,
    HEURISTIC,
    /** Disallow splitting for this CallTarget, which avoids making an eager uninitialized copy of the AST. Useful
     * notably for methods not specializing on their arguments and just calling a TruffleBoundary. */
    NEVER
}
