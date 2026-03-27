/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

/** An object denoting if the lexical frame is still on stack, so that e.g., we know whether we can break from the
 * current frame to that lexical frame. */
public final class FrameOnStackMarker {

    private boolean onStack = true;

    public boolean isOnStack() {
        return onStack;
    }

    public void setNoLongerOnStack() {
        onStack = false;
    }

}
