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
package org.truffleruby.language.control;

public final class ReturnID {

    /** Returning to or through class/module bodies is a LocalJumpError. However, the surrounding block might be turned
     * into a lambda, in which case it becomes valid and returns from the lambda. */
    public static final ReturnID MODULE_BODY = new ReturnID();

    public static final ReturnID INVALID = new ReturnID();

}
