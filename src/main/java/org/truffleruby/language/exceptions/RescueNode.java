/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.exceptions;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateWrapper
public abstract class RescueNode extends RubyContextSourceNode {

    @Child private RubyNode rescueBody;

    @Child private DispatchNode callTripleEqualsNode;
    @Child private InteropLibrary interopLibrary;
    public final boolean canOmitBacktrace;

    private final BranchProfile errorProfile = BranchProfile.create();

    public RescueNode(RubyNode rescueBody, boolean canOmitBacktrace) {
        this.rescueBody = rescueBody;
        this.canOmitBacktrace = canOmitBacktrace;
    }

    // Constructor for instrumentation
    protected RescueNode() {
        this.rescueBody = null;
        this.canOmitBacktrace = false;
    }

    public abstract boolean canHandle(Node inliningContext, VirtualFrame frame, Object exceptionObject,
            BooleanCastNode booleanCastNode);

    @Override
    public Object execute(VirtualFrame frame) {
        return rescueBody.execute(frame);
    }

    protected boolean matches(Node inliningContext, Object exceptionObject, Object handlingClass,
            BooleanCastNode booleanCastNode) {
        if (!(handlingClass instanceof RubyModule)) {
            if (interopLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                interopLibrary = insert(InteropLibrary.getFactory().createDispatched(getInteropCacheLimit()));
            }

            if (!interopLibrary.isMetaObject(handlingClass)) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().typeErrorRescueInvalidClause(this));
            }
        }

        if (callTripleEqualsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callTripleEqualsNode = insert(DispatchNode.create());
        }

        final Object matches = callTripleEqualsNode.call(handlingClass, "===", exceptionObject);
        return booleanCastNode.execute(inliningContext, matches);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new RescueNodeWrapper(this, probe);
    }

    // Declared abstract here so the instrumentation wrapper delegates it
    @Override
    public abstract Object isDefined(VirtualFrame frame, RubyLanguage language, RubyContext context);

    protected RubyNode getRescueBody() {
        return rescueBody;
    }
}
