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

import java.util.Iterator;

public final class IncludedModulesIterator implements Iterator<RubyModule> {

    private final ModuleFields currentModule;
    ModuleChain nextModule;

    public IncludedModulesIterator(PrependMarker start, ModuleFields currentModule) {
        this.currentModule = currentModule;
        this.nextModule = computeNext(start);
    }

    @Override
    public boolean hasNext() {
        return nextModule != null;
    }

    @Override
    public RubyModule next() {
        assert hasNext();

        final ModuleChain returned = nextModule;
        nextModule = computeNext(nextModule);
        return returned.getActualModule();
    }

    private ModuleChain computeNext(ModuleChain current) {
        ModuleChain mod = current.getParentModule();

        if (mod == currentModule) {
            mod = mod.getParentModule(); // skip self
        }

        if (mod instanceof IncludedModule) {
            return mod;
        } else {
            return null;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }

}
