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

import org.truffleruby.cext.ValueWrapperManager.HandleBlock;
import org.truffleruby.core.ReferenceProcessingService.PhantomProcessingReference;

public final class DataObjectFinalizerReference
        extends
        PhantomProcessingReference<DataObjectFinalizerReference, Object> {

    public final Object finalizerCFunction;
    public final Object dataStruct;
    /** Run a finalizer with or without c-extension lock. It depends on a context where a finalizer was registered. */
    public final boolean useCExtLock;
    /** Whether the data type has a mark function, in which case the object must be marked on call exit when C code
     * accesses the struct, as it can store VALUEs in it. */
    public final boolean hasMarkFunction;
    /** The block of the VALUE handle of the data object, if it has one. It is kept alive until the finalizer ran, so
     * the handle is not reused and can still be resolved to {@link #dataStruct} while the object is dead but not yet
     * finalized. The free function of another data object may access that struct, like on CRuby where the slot of a
     * dead object stays accessible until it is swept (e.g., markly clears RTYPEDDATA_DATA() of the cached wrappers of
     * all nodes when the node tree is freed). See
     * {@link org.truffleruby.cext.CExtUpcallRootNode.Carrier#DATA_VALUE}.
     * <p>
     * While the object is alive, this reference is also reachable from its {@code ValueWrapper#dataFinalizerRef}, so
     * RDATA()/RTYPEDDATA() can find the struct without reading hidden variables of the object. */
    @SuppressWarnings("unused") private HandleBlock handleBlock;
    private volatile boolean finalized = false;

    DataObjectFinalizerReference(
            Object object,
            ReferenceQueue<? super Object> queue,
            DataObjectFinalizationService service,
            Object finalizerCFunction,
            Object dataStruct,
            boolean useCExtLock,
            boolean hasMarkFunction) {
        super(object, queue, service);
        this.finalizerCFunction = finalizerCFunction;
        this.dataStruct = dataStruct;
        this.useCExtLock = useCExtLock;
        this.hasMarkFunction = hasMarkFunction;
    }

    public void setHandleBlock(HandleBlock handleBlock) {
        assert this.handleBlock == null;
        this.handleBlock = handleBlock;
    }

    /** Whether the finalizer ran, after which {@link #dataStruct} is freed */
    public boolean isFinalized() {
        return finalized;
    }

    public void finalized() {
        finalized = true;
        handleBlock = null;
    }
}
