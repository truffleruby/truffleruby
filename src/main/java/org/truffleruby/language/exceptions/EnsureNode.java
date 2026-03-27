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
package org.truffleruby.language.exceptions;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.fiber.RubyFiber;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyContextSourceNodeCustomExecuteVoid;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.language.control.KillException;

public abstract class EnsureNode extends RubyContextSourceNodeCustomExecuteVoid {

    @Child private RubyNode tryPart;
    @Child private RubyNode ensurePart;

    public EnsureNode(RubyNode tryPart, RubyNode ensurePart) {
        this.tryPart = tryPart;
        this.ensurePart = ensurePart;
    }


    @Override
    public final Object execute(VirtualFrame frame) {
        return executeCommon(frame, false);
    }

    @Override
    public final Nil executeVoid(VirtualFrame frame) {
        executeCommon(frame, true);
        return nil;
    }

    protected abstract Object executeCommon(VirtualFrame frame, boolean executeVoid);


    /** Based on {@link InteropLibrary#throwException(Object)}'s {@code TryCatchNode}. It only runs code in ensure for
     * guest exceptions (AbstractTruffleException), ControlFlowException or no exception. */
    @Specialization
    Object ensure(VirtualFrame frame, boolean executeVoid,
            @Cached InlinedBranchProfile killExceptionProfile,
            @Cached InlinedBranchProfile guestExceptionProfile,
            @Cached InlinedBranchProfile controlFlowExceptionProfile,
            @Cached InlinedConditionProfile raiseExceptionProfile) {
        Object value = nil;
        RuntimeException rethrowException = null;
        AbstractTruffleException guestException = null;

        try {
            if (executeVoid) {
                tryPart.executeVoid(frame);
            } else {
                value = tryPart.execute(frame);
            }
        } catch (KillException e) { // an AbstractTruffleException but must not set $!
            killExceptionProfile.enter(this);
            rethrowException = e;
        } catch (AbstractTruffleException e) {
            guestExceptionProfile.enter(this);
            guestException = e;
            rethrowException = e;
        } catch (ControlFlowException e) {
            controlFlowExceptionProfile.enter(this);
            rethrowException = e;
        }

        RubyFiber currentFiber = null;
        Object previousException = null;

        if (guestException != null) {
            var exceptionObject = ExceptionOperations.getExceptionObject(this, guestException, raiseExceptionProfile);
            currentFiber = getLanguage().getCurrentFiber();
            previousException = currentFiber.getLastException();
            currentFiber.setLastException(exceptionObject);
        }
        try {
            ensurePart.executeVoid(frame);
        } finally {
            if (guestException != null) {
                currentFiber.setLastException(previousException);
            }
        }

        if (rethrowException != null) {
            throw rethrowException;
        } else {
            return value;
        }
    }


    @Override
    public RubyNode cloneUninitialized() {
        var copy = EnsureNodeGen.create(
                tryPart.cloneUninitialized(),
                ensurePart.cloneUninitialized());
        return copy.copyFlags(this);
    }

}
