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
package org.truffleruby.core;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.module.ModuleNodes;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.UsingNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;

@CoreModule(value = "main", isClass = true)
public abstract class MainNodes {

    @GenerateUncached
    @CoreMethod(names = "public", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class PublicNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object forward(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached ModuleNodes.PublicNode publicNode) {
            return publicNode.execute(callerFrame, coreLibrary().objectClass,
                    RubyArguments.repack(rubyArgs, coreLibrary().objectClass), null);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "private", rest = true, visibility = Visibility.PRIVATE, alwaysInlined = true)
    public abstract static class PrivateNode extends AlwaysInlinedMethodNode {
        @Specialization
        Object forward(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached ModuleNodes.PrivateNode privateNode) {
            return privateNode.execute(callerFrame, coreLibrary().objectClass,
                    RubyArguments.repack(rubyArgs, coreLibrary().objectClass), null);
        }
    }

    @GenerateUncached
    @CoreMethod(names = "using", required = 1, alwaysInlined = true)
    public abstract static class MainUsingNode extends UsingNode {
        @Specialization
        Object mainUsing(Frame callerFrame, Object self, Object[] rubyArgs, RootCallTarget target,
                @Cached InlinedBranchProfile errorProfile) {
            needCallerFrame(callerFrame, target);
            final Object refinementModule = RubyArguments.getArgument(rubyArgs, 0);
            final InternalMethod callerMethod = RubyArguments.getMethod(callerFrame);
            if (!isCalledFromTopLevel(callerMethod)) {
                errorProfile.enter(this);
                throw new RaiseException(
                        getContext(),
                        coreExceptions().runtimeError("main.using is permitted only at toplevel", this));
            }
            using(callerFrame, refinementModule, errorProfile);
            return nil;
        }

        @TruffleBoundary
        private boolean isCalledFromTopLevel(InternalMethod callerMethod) {
            final String name = callerMethod.getOriginalName();
            return name.equals("<main>") || name.startsWith("<top ");
        }
    }

}
