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
package org.truffleruby.core.proc;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.FrameOnStackMarker;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;

import static org.truffleruby.language.RubyBaseNode.nil;

public abstract class ProcOperations {

    private static Object[] packArguments(RubyProc proc, ArgumentsDescriptor descriptor, Object... args) {
        return RubyArguments.pack(
                proc.declarationFrame,
                null,
                proc.declaringMethod,
                proc.frameOnStackMarker,
                getSelf(proc),
                nil,
                descriptor,
                args);
    }

    /** Only use for Proc called with no Truffle frame above, i.e. a root call. */
    public static Object rootCall(RubyProc proc, ArgumentsDescriptor descriptor, Object... args) {
        // We cannot break out of a block without a frame above. This is particularly important for
        // Thread.new as otherwise the flag could be set too late (after returning from Thread.new
        // and not when the new Thread starts executing).
        final FrameOnStackMarker frameOnStackMarker = proc.frameOnStackMarker;
        if (frameOnStackMarker != null) {
            frameOnStackMarker.setNoLongerOnStack();
        }

        return proc.callTarget.call(packArguments(proc, descriptor, args));
    }

    public static RubyProc createRubyProc(
            RubyClass rubyClass,
            Shape procShape,
            ProcType type,
            SharedMethodInfo sharedMethodInfo,
            ProcCallTargets holder,
            MaterializedFrame declarationFrame,
            SpecialVariableStorage variables,
            InternalMethod method,
            FrameOnStackMarker frameOnStackMarker,
            DeclarationContext declarationContext) {

        final RootCallTarget callTargetForType;

        switch (type) {
            case PROC:
                callTargetForType = holder.getCallTargetForProc();
                break;
            case LAMBDA:
                callTargetForType = holder.getCallTargetForLambda();
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }

        return new RubyProc(
                rubyClass,
                procShape,
                type,
                sharedMethodInfo.getArity(),
                sharedMethodInfo.getRawArgumentDescriptors(),
                holder,
                callTargetForType,
                declarationFrame,
                variables,
                method,
                frameOnStackMarker,
                declarationContext);

        // TODO(norswap, 04 Aug 2020): do allocation tracing (normally via AllocateHelper)?
    }

    private static RubyProc convertBlock(RubyContext context, RubyLanguage language, RubyProc block, ProcType type,
            FrameOnStackMarker frameOnStackMarker) {
        return ProcOperations
                .createRubyProc(
                        context.getCoreLibrary().procClass,
                        language.procShape,
                        type,
                        block.getSharedMethodInfo(),
                        block.callTargets,
                        block.declarationFrame,
                        block.declarationVariables,
                        block.declaringMethod,
                        frameOnStackMarker,
                        block.declarationContext);
    }

    public static RubyProc createLambdaFromBlock(RubyContext context, RubyLanguage language, RubyProc block) {
        // Inefficient otherwise, check upstream, in a guard if possible.
        assert block.type == ProcType.PROC;
        return convertBlock(context, language, block, ProcType.LAMBDA, null);
    }

    public static RubyProc createProcFromBlock(RubyContext context, RubyLanguage language, RubyProc block,
            FrameOnStackMarker frameOnStackMarker) {
        // Inefficient otherwise, check upstream, in a guard if possible.
        assert block.type == ProcType.LAMBDA;
        return convertBlock(context, language, block, ProcType.PROC, frameOnStackMarker);
    }

    public static Object getSelf(RubyProc proc) {
        return RubyArguments.getSelf(proc.declarationFrame);
    }
}
