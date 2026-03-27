/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.hash.HashNodes.CopyHashAndSetRuby2KeywordsNode;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.arguments.ArgumentsDescriptor;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.KeywordArgumentsDescriptor;

/** A literal call site in Ruby code: one of foo(), super or yield. */
public abstract class LiteralCallNode extends RubyContextSourceNode {

    protected final ArgumentsDescriptor descriptor;
    @Child private CopyHashAndSetRuby2KeywordsNode copyHashAndSetRuby2KeywordsNode;

    protected final boolean isSplatted;
    @CompilationFinal private boolean lastArgIsNotHashProfile, notRuby2KeywordsHashProfile, emptyKeywordsProfile,
            notEmptyKeywordsProfile;

    protected LiteralCallNode(boolean isSplatted, ArgumentsDescriptor descriptor) {
        this.isSplatted = isSplatted;
        this.descriptor = descriptor;
    }

    // NOTE: args is either frame args or user args
    protected ArgumentsDescriptor getArgumentsDescriptorAndCheckRuby2KeywordsHash(Object[] args, int userArgsCount) {
        assert isSplatted : "this is only needed if isSplatted";

        if (descriptor == NoKeywordArgumentsDescriptor.INSTANCE) { // *rest and no kwargs passed explicitly (k: v/k => v/**kw)
            if (userArgsCount > 0) {
                final Object lastArgument = ArrayUtils.getLast(args);
                assert lastArgument != null;

                if (!(lastArgument instanceof RubyHash)) {
                    if (!lastArgIsNotHashProfile) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        lastArgIsNotHashProfile = true;
                    }

                    return descriptor;
                }

                RubyHash hash = (RubyHash) lastArgument;
                if (hash.ruby2_keywords) { // both branches profiled
                    copyRuby2KeywordsHashBoundary(args, hash);
                    return KeywordArgumentsDescriptor.EMPTY;
                } else {
                    if (!notRuby2KeywordsHashProfile) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        notRuby2KeywordsHashProfile = true;
                    }
                }
            }
        }

        return descriptor;
    }

    // NOTE: args is either frame args or user args
    protected boolean emptyKeywordArguments(Object[] args) {
        assert isSplatted || descriptor instanceof KeywordArgumentsDescriptor;

        if (((RubyHash) ArrayUtils.getLast(args)).empty()) {
            if (!emptyKeywordsProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                emptyKeywordsProfile = true;
            }
            return true;
        } else {
            if (!notEmptyKeywordsProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                notEmptyKeywordsProfile = true;
            }
            return false;
        }
    }

    // NOTE: args is either frame args or user args
    public static Object[] removeEmptyKeywordArguments(Object[] args) {
        return ArrayUtils.extractRange(args, 0, args.length - 1);
    }

    @InliningCutoff
    private void copyRuby2KeywordsHashBoundary(Object[] args, RubyHash hash) {
        assert ArrayUtils.getLast(args) == hash && hash.ruby2_keywords;

        if (copyHashAndSetRuby2KeywordsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            copyHashAndSetRuby2KeywordsNode = insert(CopyHashAndSetRuby2KeywordsNode.create());
        }

        ArrayUtils.setLast(args, copyHashAndSetRuby2KeywordsNode.execute(hash, false));
    }

}
