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
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.truffleruby.core.hash.HashNodes.CopyHashAndSetRuby2KeywordsNode;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.language.RubyContextSourceNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.language.RubyNode;

public final class ReadRestArgumentNode extends RubyContextSourceNode {

    private final int startIndex;
    private final int postArgumentsCount;
    private final boolean keywordArguments;
    @CompilationFinal boolean markKeywordHashWithFlag = false;

    private final BranchProfile noArgumentsLeftProfile = BranchProfile.create();
    private final BranchProfile subsetOfArgumentsProfile = BranchProfile.create();

    @Child CopyHashAndSetRuby2KeywordsNode copyHashAndSetRuby2KeywordsNode;

    public ReadRestArgumentNode(
            int startIndex,
            int postArgumentsCount,
            boolean keywordArguments) {
        this.startIndex = startIndex;
        this.postArgumentsCount = postArgumentsCount;
        this.keywordArguments = keywordArguments;
    }

    private ReadRestArgumentNode(
            int startIndex,
            int postArgumentsCount,
            boolean keywordArguments,
            boolean markKeywordHashWithFlag) {
        this.startIndex = startIndex;
        this.postArgumentsCount = postArgumentsCount;
        this.keywordArguments = keywordArguments;
        this.markKeywordHashWithFlag = markKeywordHashWithFlag;
    }

    public void markKeywordHashWithFlag() {
        this.markKeywordHashWithFlag = true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (markKeywordHashWithFlag) {
            final ArgumentsDescriptor descriptor = RubyArguments.getDescriptor(frame);
            if (descriptor instanceof KeywordArgumentsDescriptor) {
                if (copyHashAndSetRuby2KeywordsNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    copyHashAndSetRuby2KeywordsNode = insert(CopyHashAndSetRuby2KeywordsNode.create());
                }

                RubyHash keywordArguments = (RubyHash) RubyArguments.getLastArgument(frame);
                RubyHash marked = copyHashAndSetRuby2KeywordsNode.execute(keywordArguments, true);
                RubyArguments.setLastArgument(frame, marked);
            }
        }

        final int positionalArgumentsCount = RubyArguments.getPositionalArgumentsCount(frame, keywordArguments);
        int endIndex = positionalArgumentsCount - postArgumentsCount;

        final int length = endIndex - startIndex;

        if (startIndex >= endIndex) {
            noArgumentsLeftProfile.enter();
            return createEmptyArray();
        } else {
            subsetOfArgumentsProfile.enter();
            return createArray(RubyArguments.getRawArguments(frame, startIndex, length), length);
        }
    }

    @Override
    public RubyNode cloneUninitialized() {
        var copy = new ReadRestArgumentNode(startIndex, postArgumentsCount, keywordArguments, markKeywordHashWithFlag);
        return copy.copyFlags(this);
    }

}
