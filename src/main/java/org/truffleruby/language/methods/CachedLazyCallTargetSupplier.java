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
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;

import java.util.function.Supplier;

public final class CachedLazyCallTargetSupplier {

    // Volatile, so that writes from another thread will finish publishing the RootCallTarget first
    private volatile RootCallTarget callTarget = null;
    private Supplier<RootCallTarget> supplier;

    public CachedLazyCallTargetSupplier(Supplier<RootCallTarget> supplier) {
        this.supplier = supplier;
    }

    public RootCallTarget get() {
        CompilerAsserts.neverPartOfCompilation("Only behind a transfer, must not PE the Supplier");

        RootCallTarget alreadySet = this.callTarget;
        if (alreadySet != null) {
            return alreadySet;
        }

        synchronized (this) {
            if (callTarget == null) {
                callTarget = supplier.get();
                supplier = null;
            }
            return callTarget;
        }
    }

    public RootCallTarget getWhenAvailable() {
        return callTarget;
    }

}
