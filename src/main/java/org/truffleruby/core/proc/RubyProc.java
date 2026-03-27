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
package org.truffleruby.core.proc;

import java.util.Set;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.interop.ForeignToRubyArgumentsNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.FrameOnStackMarker;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.AllocationTracing;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ObjectGraphNode;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;
import org.truffleruby.language.yield.CallBlockNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.parser.ArgumentDescriptor;

@ExportLibrary(InteropLibrary.class)
public final class RubyProc extends RubyDynamicObject implements ObjectGraphNode {

    // Accessed for calling a RubyProc
    public final RootCallTarget callTarget;
    public final MaterializedFrame declarationFrame;
    public final InternalMethod declaringMethod;
    public final FrameOnStackMarker frameOnStackMarker;
    public final DeclarationContext declarationContext;
    // Not accessed for calling a RubyProc
    public final ProcType type;
    public final ProcCallTargets callTargets;
    /** Can differ from {@link SharedMethodInfo#getArity()} */
    public final Arity arity;
    /** Can differ from {@link SharedMethodInfo#getRawArgumentDescriptors()} */
    public final ArgumentDescriptor[] argumentDescriptors;
    public final SpecialVariableStorage declarationVariables;

    public RubyProc(
            RubyClass rubyClass,
            Shape shape,
            ProcType type,
            Arity arity,
            ArgumentDescriptor[] argumentDescriptors,
            ProcCallTargets callTargets,
            RootCallTarget callTarget,
            MaterializedFrame declarationFrame,
            SpecialVariableStorage declarationVariables,
            InternalMethod declaringMethod,
            FrameOnStackMarker frameOnStackMarker,
            DeclarationContext declarationContext) {
        super(rubyClass, shape);
        assert (type == ProcType.PROC) == (frameOnStackMarker != null);
        this.type = type;
        this.arity = arity;
        this.argumentDescriptors = argumentDescriptors;
        this.callTargets = callTargets;
        this.callTarget = callTarget;
        this.declarationFrame = declarationFrame;
        this.declarationVariables = declarationVariables;
        this.declaringMethod = declaringMethod;
        this.frameOnStackMarker = frameOnStackMarker;
        this.declarationContext = declarationContext;
    }

    public RubyProc withArity(Arity newArity, ArgumentDescriptor[] newArgumentDescriptors, RubyClass newRubyClass,
            Shape newShape) {
        return new RubyProc(
                newRubyClass,
                newShape,
                type,
                newArity,
                newArgumentDescriptors,
                callTargets,
                callTarget,
                declarationFrame,
                declarationVariables,
                declaringMethod,
                frameOnStackMarker,
                declarationContext);
    }

    public RubyProc duplicate(RubyClass procClass, Shape shape, Node node) {
        final RubyProc copy = new RubyProc(
                procClass,
                shape,
                this.type,
                this.arity,
                this.argumentDescriptors,
                this.callTargets,
                this.callTarget,
                this.declarationFrame,
                this.declarationVariables,
                this.declaringMethod,
                this.frameOnStackMarker,
                this.declarationContext);
        AllocationTracing.trace(copy, node);
        return copy;
    }

    public RubyProc duplicate(Shape shape, Node node) {
        return duplicate(this.getLogicalClass(), shape, node);
    }

    @Override
    public void getAdjacentObjects(Set<Object> reachable) {
        ObjectGraph.getObjectsInFrame(declarationFrame, reachable);
        ObjectGraph.addProperty(reachable, declaringMethod);
    }

    public boolean isLambda() {
        // The field is public, but the function is needed for use in guards.
        return type == ProcType.LAMBDA;
    }

    public boolean isProc() {
        // The field is public, but the function is needed for use in guards.
        return type == ProcType.PROC;
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return RubyRootNode.of(callTarget).getSharedMethodInfo();
    }

    public int getArityNumber() {
        return arity.getProcArityNumber(type);
    }

    public ArgumentDescriptor[] getArgumentDescriptors() {
        // parameters can be unnamed in a Proc created using Symbol#to_proc
        return argumentDescriptors == null ? arity.toUnnamedArgumentDescriptors() : argumentDescriptors;
    }

    // region SourceLocation
    @ExportMessage
    public boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage
    public SourceSection getSourceLocation() {
        return getSharedMethodInfo().getSourceSection();
    }
    // endregion

    // region Executable
    @ExportMessage
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(Object[] arguments,
            @Cached CallBlockNode yieldNode,
            @Cached ForeignToRubyArgumentsNode foreignToRubyArgumentsNode,
            @Bind Node node) {
        return yieldNode.yield(node, this, foreignToRubyArgumentsNode.executeConvert(node, arguments));
    }
    // endregion
}
