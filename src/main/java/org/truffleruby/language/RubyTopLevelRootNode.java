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
package org.truffleruby.language;

import org.truffleruby.RubyLanguage;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public final class RubyTopLevelRootNode extends RubyMethodRootNode {

    @CompilationFinal private boolean nextProfile;

    public RubyTopLevelRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            Arity arityForCheck) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID, arityForCheck);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return super.execute(frame);
        } catch (NextException e) {
            if (!nextProfile) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nextProfile = true;
            }

            return e.getResult();
        }
    }

    @Override
    protected RubyRootNode cloneUninitializedRootNode() {
        return new RubyTopLevelRootNode(
                getLanguage(),
                getSourceSection(),
                getFrameDescriptor(),
                getSharedMethodInfo(),
                body.cloneUninitialized(),
                getSplit(),
                returnID,
                arityForCheck);
    }

}
