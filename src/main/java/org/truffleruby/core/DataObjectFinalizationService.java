/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core;

import java.lang.ref.ReferenceQueue;

import com.oracle.truffle.api.interop.InteropException;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.backtrace.InternalRootNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.frame.VirtualFrame;

/** C-ext data finalizers are implemented with phantom references and reference queues, and are run in a dedicated Ruby
 * thread. */
public final class DataObjectFinalizationService
        extends
        ReferenceProcessingService<DataObjectFinalizerReference, Object> {

    // We need a base node here, it should extend ruby base root node and implement internal root node.
    public static final class DataObjectFinalizerRootNode extends RubyBaseRootNode implements InternalRootNode {

        @Child private InteropLibrary callNode;

        public DataObjectFinalizerRootNode(RubyLanguage language) {
            super(language, RubyLanguage.newEmptyDeclarationFrameDescriptor(), null);

            callNode = InteropLibrary.getFactory().createDispatched(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return execute((DataObjectFinalizerReference) frame.getArguments()[0]);
        }

        public Object execute(DataObjectFinalizerReference ref) {
            if (!getContext().isFinalizing()) {
                try {
                    callNode.invokeMember(getContext().getCoreLibrary().truffleCExtModule, "run_data_finalizer",
                            ref.finalizerCFunction, ref.dataStruct, ref.useCExtLock);
                } catch (InteropException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return Nil.INSTANCE;
        }

    }

    private final CallTarget callTarget;

    public DataObjectFinalizationService(RubyLanguage language, ReferenceQueue<Object> processingQueue) {
        super(processingQueue);
        callTarget = new DataObjectFinalizerRootNode(language).getCallTarget();
    }

    public DataObjectFinalizationService(RubyLanguage language, ReferenceProcessor referenceProcessor) {
        this(language, referenceProcessor.processingQueue);
    }

    @TruffleBoundary
    public DataObjectFinalizerReference addFinalizer(RubyContext context, Object object, Object finalizerCFunction,
            Object dataStruct, boolean useCExtLock) {
        final DataObjectFinalizerReference newRef = createRef(object, finalizerCFunction, dataStruct, useCExtLock);

        add(newRef);
        context.getReferenceProcessor().processReferenceQueue(this);

        return newRef;
    }

    public DataObjectFinalizerReference createRef(Object object, Object finalizerCFunction, Object dataStruct,
            boolean useCExtLock) {
        return new DataObjectFinalizerReference(object, processingQueue, this, finalizerCFunction, dataStruct,
                useCExtLock);
    }

    @Override
    protected void processReference(RubyContext context, RubyLanguage language,
            PhantomProcessingReference<?, ?> finalizerReference) {
        super.processReference(context, language, finalizerReference);

        runCatchingErrors(context, language, this::processReferenceInternal,
                (DataObjectFinalizerReference) finalizerReference);
    }

    @TruffleBoundary
    void processReferenceInternal(RubyContext context, RubyLanguage language, DataObjectFinalizerReference ref) {
        callTarget.call(ref);
    }
}
