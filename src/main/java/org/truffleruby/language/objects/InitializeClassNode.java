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
package org.truffleruby.language.objects;

import org.truffleruby.core.klass.ClassNodes;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.ModuleNodes;
import org.truffleruby.core.module.ModuleNodesFactory;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.dispatch.DispatchNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class InitializeClassNode extends RubyBaseNode {

    @Child private ModuleNodes.InitializeNode moduleInitializeNode;
    @Child private DispatchNode inheritedNode;

    public abstract RubyClass executeInitialize(RubyClass rubyClass, RubyClass superclass, boolean callInherited,
            Object block);

    @Specialization
    RubyClass initialize(RubyClass rubyClass, RubyClass superclass, boolean callInherited, Nil block) {
        return initializeGeneralWithoutBlock(rubyClass, superclass, callInherited);
    }

    @Specialization
    RubyClass initialize(RubyClass rubyClass, RubyClass superclass, boolean callInherited, RubyProc block) {
        return initializeGeneralWithBlock(rubyClass, superclass, block, callInherited);
    }

    private RubyClass initializeGeneralWithoutBlock(RubyClass rubyClass, RubyClass superclass, boolean callInherited) {
        initializeCommon(rubyClass);
        if (callInherited) {
            triggerInheritedHook(rubyClass, superclass);
        }

        return rubyClass;
    }

    private RubyClass initializeGeneralWithBlock(RubyClass rubyClass, RubyClass superclass, RubyProc block,
            boolean callInherited) {
        initializeCommon(rubyClass);
        if (callInherited) {
            triggerInheritedHook(rubyClass, superclass);
        }

        moduleInitialize(rubyClass, block);

        return rubyClass;
    }

    private void initializeCommon(RubyClass rubyClass) {
        ClassNodes.initialize(getContext(), rubyClass);
    }

    private void triggerInheritedHook(RubyClass subClass, RubyClass superClass) {
        if (inheritedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            inheritedNode = insert(DispatchNode.create());
        }
        inheritedNode.call(superClass, "inherited", subClass);
    }

    private void moduleInitialize(RubyClass rubyClass, RubyProc block) {
        if (moduleInitializeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            moduleInitializeNode = insert(ModuleNodesFactory.InitializeNodeFactory.create(null));
        }
        moduleInitializeNode.executeInitialize(rubyClass, block);
    }

}
