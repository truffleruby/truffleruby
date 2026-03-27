/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.core.kernel.TruffleKernelNodes.GetSpecialVariableStorage;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreModule("Truffle::ThreadOperations")
public abstract class TruffleThreadNodes {

    @CoreMethod(names = "ruby_caller_special_variables", onSingleton = true, required = 1)
    @ImportStatic(ArrayGuards.class)
    public abstract static class FindRubyCallerSpecialStorage extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(limit = "storageStrategyLimit()")
        static Object findRubyCaller(RubyArray modules,
                @Bind("modules.getStore()") Object store,
                @CachedLibrary("store") ArrayStoreLibrary stores,
                @Cached GetSpecialVariableStorage storageNode,
                @Bind Node node) {
            final int modulesSize = modules.size;
            Object[] moduleArray = stores.boxedCopyOfRange(store, 0, modulesSize);
            MaterializedFrame frame = getContext(node)
                    .getCallStack()
                    .iterateFrameNotInModules(moduleArray, f -> f.getFrame(FrameAccess.MATERIALIZE).materialize());
            if (frame == null) {
                return nil;
            } else {
                Object variables = storageNode.execute(frame.materialize(), node);
                getLanguage(node).getCurrentFiber().extensionCallStack.setSpecialVariables(variables);
                return variables;
            }
        }

    }
}
