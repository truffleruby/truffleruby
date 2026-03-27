/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2017-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;

public abstract class UsingNode extends AlwaysInlinedMethodNode {

    protected void using(Frame callerFrame, Object refinementModule, InlinedBranchProfile errorProfile) {
        if (refinementModule.getClass() != RubyModule.class) {
            errorProfile.enter(this);
            throw new RaiseException(
                    getContext(),
                    coreExceptions().typeErrorWrongArgumentType(refinementModule, "Module", this));
        }
        final RubyModule module = (RubyModule) refinementModule;
        final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(callerFrame);
        final Map<RubyModule, RubyModule[]> newRefinements = usingModule(declarationContext, module);
        if (newRefinements != null) {
            DeclarationContext.setRefinements(callerFrame, declarationContext, newRefinements);
        }
    }

    @TruffleBoundary
    private static Map<RubyModule, RubyModule[]> usingModule(DeclarationContext declarationContext, RubyModule module) {
        final Map<RubyModule, RubyModule[]> newRefinements = new HashMap<>(declarationContext.getRefinements());

        // Iterate ancestors in reverse order so refinements upper in the chain have precedence
        final Deque<RubyModule> reverseAncestors = new ArrayDeque<>();
        for (RubyModule ancestor : module.fields.ancestors()) {
            reverseAncestors.addFirst(ancestor);
        }

        for (RubyModule ancestor : reverseAncestors) {
            final ConcurrentMap<RubyModule, RubyModule> refinements = ancestor.fields
                    .getRefinements();
            for (Map.Entry<RubyModule, RubyModule> entry : refinements.entrySet()) {
                applyRefinements(entry.getKey(), entry.getValue(), newRefinements);
            }
        }

        return newRefinements.isEmpty() ? null : newRefinements;
    }

    private static void applyRefinements(RubyModule refinedModule, RubyModule refinementModule,
            Map<RubyModule, RubyModule[]> newRefinements) {
        final RubyModule[] refinements = newRefinements.get(refinedModule);
        if (refinements == null) {
            newRefinements.put(refinedModule, new RubyModule[]{ refinementModule });
        } else {
            if (ArrayUtils.contains(refinements, refinementModule)) {
                // Already using this refinement
            } else {
                // Add new refinement in front
                newRefinements.put(refinedModule, unshift(refinements, refinementModule));
            }
        }
    }

    private static RubyModule[] unshift(RubyModule[] array, RubyModule element) {
        final RubyModule[] newArray = new RubyModule[1 + array.length];
        newArray[0] = element;
        System.arraycopy(array, 0, newArray, 1, array.length);
        return newArray;
    }

}
