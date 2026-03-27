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
package org.truffleruby.core.format.exceptions;

@SuppressWarnings("serial")
public final class NoImplicitConversionException extends FormatException {

    private final Object object;
    private final String target;

    public NoImplicitConversionException(Object object, String target) {
        this.object = object;
        this.target = target;
    }

    public Object getObject() {
        return object;
    }

    public String getTarget() {
        return target;
    }

}
