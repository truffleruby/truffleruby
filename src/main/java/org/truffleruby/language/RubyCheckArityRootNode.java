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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.arguments.ArgumentsDescriptor;
import org.truffleruby.language.arguments.CheckKeywordArityNode;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public abstract class RubyCheckArityRootNode extends RubyRootNode {

    @Child private CheckKeywordArityNode checkKeywordArityNode;

    public final Arity arityForCheck;
    private final boolean keywordArguments;

    @CompilationFinal private boolean checkArityProfile;

    public RubyCheckArityRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            Arity arityForCheck) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID);

        this.arityForCheck = arityForCheck;
        this.keywordArguments = arityForCheck.acceptsKeywords();
        this.checkKeywordArityNode = arityForCheck.mustCheckKeywords()
                ? new CheckKeywordArityNode(arityForCheck)
                : null;
    }

    protected void checkArity(VirtualFrame frame) {
        int given = RubyArguments.getPositionalArgumentsCount(frame, keywordArguments);

        if (!arityForCheck.checkPositionalArguments(given)) {
            if (!checkArityProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkArityProfile = true;
            }

            throw checkArityErrorAndCheckRejectsKeywords(arityForCheck, given, RubyArguments.getDescriptor(frame));
        }

        if (checkKeywordArityNode != null) {
            checkKeywordArityNode.checkArity(frame);
        }
    }

    @TruffleBoundary
    private RaiseException checkArityErrorAndCheckRejectsKeywords(Arity arity, int given,
            ArgumentsDescriptor descriptor) {
        // We consider `def m(**nil)` as not accepting keywords (since it ultimately does not and raises ArgumentError).
        // That means `m(a: 1)` will give given=1 > max positional arguments (=0)
        // So we handle the reject keywords case specially here to give the correct error message.
        // Other cases are handled by CheckKeywordArityNode.
        if (arity.rejectsKeywords && descriptor instanceof KeywordArgumentsDescriptor) {
            return CheckKeywordArityNode.noKeywordsAccepted(getContext(), this);
        }

        return checkArityError(arity, given, this);
    }

    @TruffleBoundary
    public static RaiseException checkArityError(Arity arity, int given, Node currentNode) {
        final RubyContext context = RubyContext.get(currentNode);
        if (arity.hasRest()) {
            return new RaiseException(
                    context,
                    context.getCoreExceptions().argumentErrorPlus(given, arity.getRequired(), currentNode));
        } else if (arity.getOptional() > 0) {
            return new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError(
                            given,
                            arity.getRequired(),
                            arity.getOptional(),
                            currentNode));
        } else {
            return new RaiseException(
                    context,
                    context.getCoreExceptions().argumentError(given, arity.getRequired(), currentNode));
        }
    }

}
