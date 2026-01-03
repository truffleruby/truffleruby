/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

public enum OpenModule {
    MODULE("<module:"),
    CLASS("<class:"),
    SINGLETON_CLASS("<singleton class");

    private final String prefix;

    OpenModule(String prefix) {
        this.prefix = prefix;
    }

    public String format(String moduleBasename) {
        if (this == SINGLETON_CLASS) {
            return "<singleton class>";
        } else {
            return prefix + moduleBasename + ">";
        }
    }

    public String getPrefix() {
        return prefix;
    }
}
