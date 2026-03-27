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

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.NodeFactory;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodNodeManager;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.annotations.Split;
import org.truffleruby.language.methods.TranslateExceptionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public final class RubyCoreMethodRootNode extends RubyCheckArityRootNode {

    private final NodeFactory<? extends RubyBaseNode> nodeFactory;
    private final CoreMethod coreMethod;

    @Child private TranslateExceptionNode translateExceptionNode;

    public RubyCoreMethodRootNode(
            RubyLanguage language,
            SourceSection sourceSection,
            FrameDescriptor frameDescriptor,
            SharedMethodInfo sharedMethodInfo,
            RubyNode body,
            Split split,
            ReturnID returnID,
            Arity arityForCheck,
            NodeFactory<? extends RubyBaseNode> nodeFactory,
            CoreMethod coreMethod) {
        super(language, sourceSection, frameDescriptor, sharedMethodInfo, body, split, returnID, arityForCheck);
        this.nodeFactory = nodeFactory;
        this.coreMethod = coreMethod;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        TruffleSafepoint.poll(this);

        checkArity(frame);

        try {
            return body.execute(frame);
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
        return CoreMethodNodeManager.createCoreMethodRootNode(nodeFactory, getLanguage(), getSharedMethodInfo(),
                getSplit(), coreMethod);
    }

}
