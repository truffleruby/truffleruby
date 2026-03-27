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
package org.truffleruby.core.objectspace;

import com.oracle.truffle.api.object.DynamicObjectLibrary;
import org.truffleruby.Layouts;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.DataObjectFinalizerReference;
import org.truffleruby.core.FinalizerReference;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.ImmutableRubyObject;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.InternalRespondToNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectIDOperations;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.objects.shared.WriteBarrierNode;
import org.truffleruby.language.yield.CallBlockNode;

@CoreModule("ObjectSpace")
public abstract class ObjectSpaceNodes {

    @CoreMethod(names = "_id2ref", isModuleFunction = true, required = 1)
    @ImportStatic(ObjectIDOperations.class)
    public abstract static class ID2RefNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "id == NIL")
        Object id2RefNil(long id) {
            return nil;
        }

        @Specialization(guards = "id == TRUE")
        boolean id2RefTrue(long id) {
            return true;
        }

        @Specialization(guards = "id == FALSE")
        boolean id2RefFalse(long id) {
            return false;
        }

        @Specialization(guards = "isSmallFixnumID(id)")
        long id2RefSmallInt(long id) {
            return ObjectIDOperations.toFixnum(id);
        }

        @TruffleBoundary
        @Specialization(guards = "isBasicObjectID(id)")
        Object id2Ref(long id) {
            final DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();

            for (Object object : ObjectGraph.stopAndGetAllObjects("ObjectSpace._id2ref", getContext(), this)) {
                assert ObjectGraph.isRubyObject(object);

                long objectID = 0L;
                if (object instanceof RubyDynamicObject) {
                    objectID = ObjectSpaceManager.readObjectID((RubyDynamicObject) object, objectLibrary);
                } else if (object instanceof ImmutableRubyObject) {
                    objectID = ((ImmutableRubyObject) object).getObjectId();
                }

                if (objectID == id) {
                    return object;
                }
            }

            throw new RaiseException(
                    getContext(),
                    coreExceptions().rangeError(StringUtils.format("0x%016x is not id value", id), this));
        }

        @Specialization(guards = { "isLargeFixnumID(id)" })
        Object id2RefLargeFixnum(RubyBignum id) {
            return BigIntegerOps.longValue(id);
        }

        @Specialization(guards = { "isFloatID(id)" })
        double id2RefFloat(RubyBignum id) {
            return Double.longBitsToDouble(BigIntegerOps.longValue(id));
        }

        protected boolean isLargeFixnumID(RubyBignum id) {
            return ObjectIDOperations.isLargeFixnumID(id.value);
        }

        protected boolean isFloatID(RubyBignum id) {
            return ObjectIDOperations.isFloatID(id.value);
        }

    }

    @CoreMethod(
            names = "each_object",
            isModuleFunction = true,
            needsBlock = true,
            optional = 1,
            returnsEnumeratorIfNoBlock = true)
    public abstract static class EachObjectNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary // for the iterator
        @Specialization
        int eachObject(NotProvided ofClass, RubyProc block,
                @Cached @Shared CallBlockNode yieldNode) {
            int count = 0;

            for (Object object : ObjectGraph.stopAndGetAllObjects("ObjectSpace.each_object", getContext(), this)) {
                if (include(object)) {
                    yieldNode.yield(this, block, object);
                    count++;
                }
            }

            return count;
        }

        @TruffleBoundary // for the iterator
        @Specialization
        int eachObject(RubyModule ofClass, RubyProc block,
                @Cached IsANode isANode,
                @Cached @Shared CallBlockNode yieldNode) {
            int count = 0;

            final String reason = "ObjectSpace.each_object(" + ofClass + ")";
            for (Object object : ObjectGraph.stopAndGetAllObjects(reason, getContext(), this)) {
                if (include(object) && isANode.executeIsA(object, ofClass)) {
                    yieldNode.yield(this, block, object);
                    count++;
                }
            }

            return count;
        }

        private boolean include(Object object) {
            if (object instanceof RubySymbol) {
                return true;
            } else if (object instanceof RubyDynamicObject) {
                if (object instanceof RubyClass) {
                    return !RubyGuards.isSingletonClass(object);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

    }

    private static final class CallableFinalizer implements Runnable {

        private final Object callable;

        public CallableFinalizer(Object callable) {
            this.callable = callable;
        }

        public void run() {
            DispatchNode.getUncached().call(callable, "call");
        }

        @Override
        public String toString() {
            return callable.toString();
        }

    }

    @CoreMethod(names = "define_finalizer", isModuleFunction = true, required = 2)
    public abstract static class DefineFinalizerNode extends CoreMethodArrayArgumentsNode {

        // MRI would do a dynamic call to #respond_to? but it seems better to warn the user earlier.
        // Wanting #method_missing(:call) to be called for a finalizer seems highly unlikely.
        @Child private InternalRespondToNode respondToCallNode = InternalRespondToNode.create();

        @Specialization
        RubyArray defineFinalizer(VirtualFrame frame, RubyDynamicObject object, Object finalizer,
                @Cached BranchProfile errorProfile,
                @Cached WriteBarrierNode writeBarrierNode) {
            if (respondToCallNode.execute(frame, finalizer, "call")) {
                if (!getContext().getReferenceProcessor().processOnMainThread()) {
                    // Share the finalizer, as it might run on a different Thread
                    if (!getContext().getSharedObjects().isSharing()) {
                        startSharing();
                    }
                    writeBarrierNode.execute(this, finalizer);
                }

                defineFinalizer(object, finalizer);
                return createArray(new Object[]{ 0, finalizer });
            } else {
                errorProfile.enter();
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentErrorWrongArgumentType(finalizer, "callable", this));
            }
        }

        @TruffleBoundary
        private void startSharing() {
            getContext().getSharedObjects().startSharing(getLanguage(), "creating finalizer");
        }

        @TruffleBoundary
        private void defineFinalizer(RubyDynamicObject object, Object finalizer) {
            final RubyDynamicObject root = (finalizer instanceof RubyDynamicObject)
                    ? (RubyDynamicObject) finalizer
                    : null;
            final CallableFinalizer action = new CallableFinalizer(finalizer);
            final DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();

            synchronized (object) {
                final FinalizerReference ref = (FinalizerReference) objectLibrary
                        .getOrDefault(object, Layouts.FINALIZER_REF_IDENTIFIER, null);
                if (ref == null) {
                    final FinalizerReference newRef = getContext()
                            .getFinalizationService()
                            .addFinalizer(getContext(), object, ObjectSpaceManager.class, action, root);
                    objectLibrary.put(object, Layouts.FINALIZER_REF_IDENTIFIER, newRef);
                } else {
                    getContext()
                            .getFinalizationService()
                            .addAdditionalFinalizer(getContext(), ref, object, ObjectSpaceManager.class, action, root);
                }
            }
        }

    }

    @Primitive(name = "objectspace_define_data_finalizer")
    public abstract static class DefineDataObjectFinalizerNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        Object defineFinalizer(
                RubyDynamicObject object, Object finalizerCFunction, Object dataStruct, boolean useCExtLock,
                @CachedLibrary(limit = "1") DynamicObjectLibrary objectLibrary) {
            DataObjectFinalizerReference newRef = getContext()
                    .getDataObjectFinalizationService()
                    .addFinalizer(getContext(), object, finalizerCFunction, dataStruct, useCExtLock);

            objectLibrary.put(object, Layouts.DATA_OBJECT_FINALIZER_REF_IDENTIFIER, newRef);

            return nil;
        }
    }

    @CoreMethod(names = "undefine_finalizer", isModuleFunction = true, required = 1)
    public abstract static class UndefineFinalizerNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object undefineFinalizer(RubyDynamicObject object,
                @Cached TypeNodes.CheckFrozenNode raiseIfFrozenNode) {
            final DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();
            synchronized (object) {
                raiseIfFrozenNode.execute(this, object);

                FinalizerReference ref = (FinalizerReference) objectLibrary
                        .getOrDefault(object, Layouts.FINALIZER_REF_IDENTIFIER, null);
                if (ref != null) {
                    FinalizerReference newRef = getContext()
                            .getFinalizationService()
                            .removeFinalizers(object, ref, ObjectSpaceManager.class);
                    if (ref != newRef) {
                        objectLibrary.put(object, Layouts.FINALIZER_REF_IDENTIFIER, newRef);
                    }
                }
            }
            return object;
        }

    }

}
