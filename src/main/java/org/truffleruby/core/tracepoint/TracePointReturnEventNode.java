/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core.tracepoint;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.LocalReturnException;
import org.truffleruby.language.control.ReturnID;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;

/** The :return event, triggered when a method defined in Ruby returns, either normally or by an exception (then the
 * return value is nil, like CRuby). */
final class TracePointReturnEventNode extends TracePointEventNode {

    private final BranchProfile exceptionalReturnProfile = BranchProfile.create();

    /** The ReturnID of the method, to know if a DynamicReturnException is a non-local return from this method */
    private final ReturnID returnID;
    /** Whether this is the :c_return event, for methods defined in Java, otherwise :return */
    private final boolean cReturn;

    public TracePointReturnEventNode(
            RubyContext context,
            RubyLanguage language,
            EventContext eventContext,
            RubyTracePoint tracePoint,
            RubySymbol event) {
        super(context, language, eventContext, tracePoint, event);
        final RootNode rootNode = eventContext.getInstrumentedNode().getRootNode();
        this.returnID = rootNode instanceof RubyRootNode rubyRootNode ? rubyRootNode.returnID : ReturnID.INVALID;
        this.cReturn = event == language.coreSymbols.C_RETURN;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        // Nothing to do on enter, the :call event is a different TracePointEvent
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        if (cReturn) {
            fireReturn(frame, result, 0);
        } else {
            // Like CRuby, the line of an implicit return is the line of the `end` of the method
            fireReturn(frame, result, getEndLine());
        }
    }

    @Override
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (cReturn) {
            // Like CRuby, no :c_return event when a method defined in Java exits by an exception
            return;
        }

        exceptionalReturnProfile.enter();
        if (exception instanceof LocalReturnException localReturn) {
            fireReturn(frame, localReturn.getValue(), getLine(localReturn.getReturnNode()));
        } else if (exception instanceof DynamicReturnException dynamicReturn &&
                returnID != ReturnID.INVALID && dynamicReturn.getReturnID() == returnID) {
            fireReturn(frame, dynamicReturn.getValue(), getLine(dynamicReturn.getReturnNode()));
        } else {
            // An exception, throw, break, etc. propagating out of the method
            fireReturn(frame, Nil.INSTANCE, getEndLine());
        }
    }

    private void fireReturn(VirtualFrame frame, Object returnValue, int line) {
        final RubyThread rubyThread = RubyLanguage.get(this).getCurrentThread();
        if (inTracePointProfile.profile(rubyThread.insideTracePointProc)) {
            return;
        }

        final RubyTracePointEvent tracePointEvent;
        if (cReturn) {
            // Like CRuby, the location of a :c_return event is the call site and there is no binding
            tracePointEvent = new RubyTracePointEvent(tracePoint, language.tracePointEventShape, event, Nil.INSTANCE,
                    0, null, null, RubyArguments.getSelf(frame), RubyArguments.getMethod(frame));
            setCallerLocation(tracePointEvent);
        } else {
            tracePointEvent = new RubyTracePointEvent(tracePoint, language.tracePointEventShape, event, getFile(),
                    line, frame.materialize(), eventContext.getInstrumentedSourceSection(),
                    RubyArguments.getSelf(frame), RubyArguments.getMethod(frame));
        }
        tracePointEvent.returnValue = returnValue;
        callTracePointProc(rubyThread, tracePointEvent);
    }

    @TruffleBoundary
    private int getLine(Node returnNode) {
        return language.getStartLineAdjusted(returnNode.getEncapsulatingSourceSection());
    }

}
