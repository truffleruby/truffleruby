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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleSafepoint;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.control.BreakException;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.LocalReturnException;
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

public final class RubyLambdaRootNode extends RubyCheckArityRootNode {

    public static RubyLambdaRootNode of(RootCallTarget callTarget) {
        return (RubyLambdaRootNode) callTarget.getRootNode();
    }

    public final BreakID breakID;

    @Child private TranslateExceptionNode translateExceptionNode;

    @CompilationFinal private boolean localReturnProfile, retryProfile, matchingReturnProfile, nonMatchingReturnProfile,
            matchingBreakProfile, nonMatchingBreakProfile, redoProfile, nextProfile;

    public RubyLambdaRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            BreakID breakID,
            Arity arityForCheck) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID, arityForCheck);
        this.breakID = breakID;
    }

    public RubyLambdaRootNode copyRootNode(SharedMethodInfo newSharedMethodInfo, RubyNode newBody) {
        return new RubyLambdaRootNode(
                getLanguage(),
                getSourceSection(),
                getFrameDescriptor(),
                newSharedMethodInfo,
                newBody,
                getSplit(),
                returnID,
                breakID,
                arityForCheck);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        TruffleSafepoint.poll(this);

        checkArity(frame);

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
        } catch (LocalReturnException e) {
            if (!localReturnProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                localReturnProfile = true;
            }

            return e.getValue();
        } catch (DynamicReturnException e) {
            if (returnID != ReturnID.INVALID && e.getReturnID() == returnID) {
                if (!matchingReturnProfile) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    matchingReturnProfile = true;
                }
                return e.getValue();
            } else {
                if (!nonMatchingReturnProfile) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    nonMatchingReturnProfile = true;
                }
                throw e;
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
        } catch (BreakException e) {
            if (breakID != BreakID.INVALID && e.getBreakID() == breakID) {
                if (!matchingBreakProfile) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    matchingBreakProfile = true;
                }
                return e.getResult();
            } else {
                if (!nonMatchingBreakProfile) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    nonMatchingBreakProfile = true;
                }
                throw e;
            }
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
        return new RubyLambdaRootNode(
                getLanguage(),
                getSourceSection(),
                getFrameDescriptor(),
                getSharedMethodInfo(),
                body.cloneUninitialized(),
                getSplit(),
                returnID,
                breakID,
                arityForCheck);
    }

}
