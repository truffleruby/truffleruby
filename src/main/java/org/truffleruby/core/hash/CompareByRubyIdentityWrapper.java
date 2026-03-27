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
package org.truffleruby.core.hash;

import org.truffleruby.core.basicobject.ReferenceEqualNode;
import org.truffleruby.core.hash.HashingNodes.ToHashByIdentity;

/** Wraps a key so that it will be hashed and compared according to Ruby identity semantics. These semantics differ from
 * Java semantics notably for primitives (e.g. all the NaN are different according to Ruby, but Java compares them
 * equal). */
public final class CompareByRubyIdentityWrapper {

    public final Object key;

    public CompareByRubyIdentityWrapper(Object key) {
        this.key = key;
    }

    @Override
    public int hashCode() {
        return ToHashByIdentity.getUncached().execute(key);
    }

    @Override
    public boolean equals(Object obj) {
        // We use an uncached ReferenceEqualNode here since we are not in PE-able code
        return obj instanceof CompareByRubyIdentityWrapper &&
                ReferenceEqualNode.executeUncached(key, ((CompareByRubyIdentityWrapper) obj).key);
    }
}
