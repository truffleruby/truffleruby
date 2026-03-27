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
package org.truffleruby.extra;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.Split;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.mutex.MutexOperations;
import org.truffleruby.core.proc.RubyProc;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.yield.CallBlockNode;

import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.ReentrantLock;

@CoreModule("TruffleRuby")
public abstract class TruffleRubyNodes {

    @CoreMethod(names = "graalvm_home", onSingleton = true)
    public abstract static class GraalvmHomeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        Object graalvmHome() {
            return nil;
        }
    }

    @CoreMethod(names = "jit?", onSingleton = true)
    public abstract static class GraalNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        static boolean isGraal() {
            return Truffle.getRuntime().getName().contains("Graal");
        }

    }

    @CoreMethod(names = "native?", onSingleton = true)
    public abstract static class NativeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isNative() {
            return TruffleOptions.AOT;
        }

    }

    @CoreMethod(names = "cexts?", onSingleton = true)
    public abstract static class SulongNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        boolean isSulong() {
            return isSulongAvailable(getContext());
        }

        @TruffleBoundary
        public static boolean isSulongAvailable(RubyContext context) {
            return context.getEnv().isMimeTypeSupported(RubyLanguage.LLVM_BITCODE_MIME_TYPE);
        }

    }

    @CoreMethod(names = "full_memory_barrier", onSingleton = true)
    public abstract static class FullMemoryBarrierPrimitiveNode extends CoreMethodNode {
        @Specialization
        Object fullMemoryBarrier() {
            VarHandle.fullFence();
            return nil;
        }
    }

    @CoreMethod(names = "synchronized", onSingleton = true, required = 1, needsBlock = true, split = Split.ALWAYS)
    public abstract static class SynchronizedNode extends CoreMethodArrayArgumentsNode {

        /** We must not allow to synchronize on boxed primitives as that would be misleading. We use a ReentrantLock and
         * not simply Java's {@code synchronized} here as we need to be able to interrupt for guest safepoints and it is
         * not possible to interrupt Java's {@code synchronized (object) {}}. */
        @Specialization
        static Object synchronize(RubyDynamicObject object, RubyProc block,
                @Cached GetLockNode getLockNode,
                @Cached CallBlockNode yieldNode,
                @Bind Node node) {
            final ReentrantLock lock = getLockNode.execute(node, object);

            MutexOperations.lockInternal(getContext(node), lock, node);
            try {
                return yieldNode.yield(node, block);
            } finally {
                MutexOperations.unlockInternal(lock);
            }
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetLockNode extends RubyBaseNode {

        public abstract ReentrantLock execute(Node node, RubyDynamicObject object);

        @Specialization(limit = "getDynamicObjectCacheLimit()")
        static ReentrantLock getLock(Node node, RubyDynamicObject object,
                @CachedLibrary("object") DynamicObjectLibrary objectLibrary,
                @Cached InlinedBranchProfile initializeLockProfile) {
            ReentrantLock lock = (ReentrantLock) objectLibrary.getOrDefault(object, Layouts.OBJECT_LOCK, null);
            if (lock != null) {
                return lock;
            }

            initializeLockProfile.enter(node);
            synchronized (object) {
                lock = (ReentrantLock) objectLibrary.getOrDefault(object, Layouts.OBJECT_LOCK, null);
                if (lock != null) {
                    return lock;
                } else {
                    lock = new ReentrantLock();
                    objectLibrary.put(object, Layouts.OBJECT_LOCK, lock);
                    return lock;
                }
            }
        }
    }

}
