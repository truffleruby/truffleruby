/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2014-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.module;

/** Either an IncludedModule, a RubyClass or a RubyModule. Private interface, do not use outside RubyModule. */
public abstract class ModuleChain {

    protected ModuleChain parentModule;

    public ModuleChain(ModuleChain parentModule) {
        this.parentModule = parentModule;
    }

    public final ModuleChain getParentModule() {
        return parentModule;
    }

    public final void insertAfter(RubyModule module) {
        parentModule = new IncludedModule(module, parentModule);
    }

    public abstract RubyModule getActualModule();

}
