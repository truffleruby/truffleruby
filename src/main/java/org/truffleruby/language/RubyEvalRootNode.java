/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.LocalReturnException;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RetryException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.methods.TranslateExceptionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

/** A RootNode for an eval. Similar to a block (and not a method) once parsed since it can access the surrounding
 * variables and it is as well in another CallTarget. */
public final class RubyEvalRootNode extends RubyRootNode {

    @Child private TranslateExceptionNode translateExceptionNode;

    @CompilationFinal private boolean nextProfile, retryProfile, returnProfile;

    public RubyEvalRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        TruffleSafepoint.poll(this);

        try {
            return body.execute(frame);
        } catch (RetryException e) {
            if (!retryProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                retryProfile = true;
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().syntaxErrorInvalidRetry(this));
        } catch (NextException e) {
            if (!nextProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextProfile = true;
            }

            return e.getResult();
        } catch (LocalReturnException | DynamicReturnException e) {
            if (!returnProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                returnProfile = true;
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().unexpectedReturn(this));
        } catch (Throwable t) {
            if (translateExceptionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                translateExceptionNode = insert(TranslateExceptionNode.create());
            }
            throw translateExceptionNode.executeCached(t);
        }
    }

    @Override
    protected RubyRootNode cloneUninitializedRootNode() {
        return new RubyEvalRootNode(
                getLanguage(),
                getSourceSection(),
                getFrameDescriptor(),
                getSharedMethodInfo(),
                body.cloneUninitialized(),
                getSplit(),
                returnID);
    }
}
