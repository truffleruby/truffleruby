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
package org.truffleruby.core.module;

/** A reference to an included RubyModule. */
public final class IncludedModule extends ModuleChain {

    private final RubyModule includedModule;

    public IncludedModule(RubyModule includedModule, ModuleChain parentModule) {
        super(parentModule);
        this.includedModule = includedModule;
    }

    @Override
    public RubyModule getActualModule() {
        return includedModule;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + includedModule + ")";
    }

}
