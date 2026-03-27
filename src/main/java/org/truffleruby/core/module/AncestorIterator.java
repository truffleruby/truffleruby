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

public final class AncestorIterator implements Iterator<RubyModule> {

    ModuleChain module;

    public AncestorIterator(ModuleChain top) {
        module = top;
    }

    @Override
    public boolean hasNext() {
        return module != null;
    }

    @Override
    public RubyModule next() {
        assert hasNext();

        ModuleChain mod = module;
        if (mod instanceof PrependMarker) {
            mod = mod.getParentModule();
        }

        module = mod.getParentModule();

        return mod.getActualModule();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }

}
