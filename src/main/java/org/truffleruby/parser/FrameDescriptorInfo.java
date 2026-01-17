/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.threadlocal.SpecialVariableStorage;

/** This is the {@link FrameDescriptor#getInfo() descriptor info} for both methods and blocks. */
public final class FrameDescriptorInfo {

    public static FrameDescriptorInfo of(FrameDescriptor frameDescriptor) {
        return (FrameDescriptorInfo) frameDescriptor.getInfo();
    }

    @ExplodeLoop
    public static FrameDescriptor getDeclarationFrameDescriptor(FrameDescriptor topDescriptor, int depth) {
        assert depth > 0;
        FrameDescriptor descriptor = topDescriptor;
        for (int i = 0; i < depth; i++) {
            descriptor = FrameDescriptorInfo.of(descriptor).getParentDescriptor();
        }
        return descriptor;
    }

    /** null for methods, non-null for blocks */
    @CompilationFinal private FrameDescriptor parentDescriptor;
    /** We need to access this Assumption from the FrameDescriptor, and there is no way to get a RootNode from a
     * FrameDescriptor, so we store it in the descriptor info. We do not store it as "slot info" for footprint, to avoid
     * needing an info array per FrameDescriptor. */
    private final Assumption specialVariableAssumption;
    private final SharedMethodInfo sharedMethodInfo;

    public FrameDescriptorInfo(Assumption specialVariableAssumption, SharedMethodInfo sharedMethodInfo) {
        assert SpecialVariableStorage.isSpecialVariableAssumption(specialVariableAssumption);
        assert sharedMethodInfo.isMethod();
        this.parentDescriptor = null; // null for methods
        this.specialVariableAssumption = specialVariableAssumption;
        this.sharedMethodInfo = sharedMethodInfo;
    }

    public FrameDescriptorInfo(FrameDescriptor parentDescriptor, SharedMethodInfo sharedMethodInfo) {
        var parentDescriptorInfo = of(parentDescriptor);
        assert sharedMethodInfo.isBlock();
        assert sharedMethodInfo.getBlockDepth() == parentDescriptorInfo.sharedMethodInfo.getBlockDepth() + 1;
        this.parentDescriptor = parentDescriptor;
        this.specialVariableAssumption = parentDescriptorInfo.specialVariableAssumption;
        this.sharedMethodInfo = sharedMethodInfo;
    }

    public FrameDescriptorInfo(FrameDescriptorInfo parentDescriptorInfo, SharedMethodInfo sharedMethodInfo) {
        assert sharedMethodInfo.isBlock();
        assert sharedMethodInfo.getBlockDepth() == parentDescriptorInfo.sharedMethodInfo.getBlockDepth() + 1;
        this.parentDescriptor = null; // set later
        this.specialVariableAssumption = parentDescriptorInfo.specialVariableAssumption;
        this.sharedMethodInfo = sharedMethodInfo;
    }

    public FrameDescriptor getParentDescriptor() {
        return parentDescriptor;
    }

    void setParentDescriptor(FrameDescriptor parentDescriptor) {
        assert this.parentDescriptor == null;
        this.parentDescriptor = parentDescriptor;
    }

    public Assumption getSpecialVariableAssumption() {
        assert specialVariableAssumption != null;
        return specialVariableAssumption;
    }

    public SharedMethodInfo getSharedMethodInfo() {
        return sharedMethodInfo;
    }
}
