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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleSafepoint;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.arguments.CheckKeywordArityNode;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoException;
import org.truffleruby.language.control.RetryException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.methods.TranslateExceptionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public final class RubyProcRootNode extends RubyRootNode {

    @Child private TranslateExceptionNode translateExceptionNode;
    @Child private CheckKeywordArityNode checkKeywordArityNode;

    @CompilationFinal private boolean redoProfile, nextProfile, retryProfile;

    public RubyProcRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            Arity arityForCheck) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID);
        this.checkKeywordArityNode = arityForCheck.mustCheckKeywords()
                ? new CheckKeywordArityNode(arityForCheck)
                : null;
    }

    private RubyProcRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            CheckKeywordArityNode checkKeywordArityNode) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID);
        this.checkKeywordArityNode = checkKeywordArityNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        TruffleSafepoint.poll(this);

        if (checkKeywordArityNode != null) {
            checkKeywordArityNode.checkArity(frame);
        }

        try {
            while (true) {
                try {
                    return body.execute(frame);
                } catch (RedoException e) {
                    if (!redoProfile) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        redoProfile = true;
                    }

                    TruffleSafepoint.poll(this);
                    continue;
                }
            }
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
        // CheckKeywordArityNode uses branch profiling, so it should be copied without profiling data
        var checkKeywordArityNodeCopy = (checkKeywordArityNode == null)
                ? null
                : checkKeywordArityNode.cloneUninitialized();
        return new RubyProcRootNode(
                getLanguage(),
                getSourceSection(),
                getFrameDescriptor(),
                getSharedMethodInfo(),
                body.cloneUninitialized(),
                getSplit(),
                returnID,
                checkKeywordArityNodeCopy);
    }
}
