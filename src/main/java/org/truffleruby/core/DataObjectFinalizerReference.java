/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import java.lang.ref.ReferenceQueue;

import org.truffleruby.core.ReferenceProcessingService.PhantomProcessingReference;

public final class DataObjectFinalizerReference
        extends
        PhantomProcessingReference<DataObjectFinalizerReference, Object> {

    /** The address of the C function freeing the struct */
    public final long finalizerCFunction;
    /** The address of the struct RData/RTypedData */
    public final long dataStruct;
    /** Run a finalizer with or without c-extension lock. It depends on a context where a finalizer was registered. */
    public final boolean useCExtLock;

    DataObjectFinalizerReference(
            Object object,
            ReferenceQueue<? super Object> queue,
            DataObjectFinalizationService service,
            long finalizerCFunction,
            long dataStruct,
            boolean useCExtLock) {
        super(object, queue, service);
        this.finalizerCFunction = finalizerCFunction;
        this.dataStruct = dataStruct;
        this.useCExtLock = useCExtLock;
    }
}
