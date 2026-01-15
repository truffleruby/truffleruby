/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.refinement;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.annotations.Primitive;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.DeclarationContext.FixedDefaultDefinee;
import org.truffleruby.language.methods.InternalMethod;

import java.util.HashMap;
import java.util.Map;

@CoreModule(value = "Refinement", isClass = true)
public abstract class RefinementNodes {

    @Primitive(name = "refinement_import_methods")
    public abstract static class ImportMethodsNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        RubyModule importMethods(RubyModule refinement, RubyModule moduleToImportFrom) {
            var firstNonRubyMethod = getFirstNonRubyMethodOrNull(moduleToImportFrom, getLanguage());
            if (firstNonRubyMethod != null) {
                throw new RaiseException(getContext(),
                        coreExceptions().argumentError(createErrorMessage(firstNonRubyMethod, moduleToImportFrom),
                                this));
            }

            importMethodsFromModuleToRefinement(moduleToImportFrom, refinement);

            return refinement;
        }

        private String createErrorMessage(InternalMethod method, RubyModule module) {
            return StringUtils.format("Can't import method which is not defined with Ruby code: %s#%s",
                    module.getName(), method.getName());
        }

        private void importMethodsFromModuleToRefinement(RubyModule module, RubyModule refinement) {
            var declarationContext = createDeclarationContextWithRefinement(refinement);
            for (InternalMethod methodToCopy : module.fields.getMethods()) {
                var clonedMethod = cloneMethod(methodToCopy, declarationContext, refinement);
                refinement.fields.addMethod(getContext(), this, clonedMethod);
            }
        }

        private InternalMethod getFirstNonRubyMethodOrNull(RubyModule module, RubyLanguage language) {
            for (InternalMethod method : module.fields.getMethods()) {
                if (!method.isDefinedInRuby(language)) {
                    return method;
                }
            }

            return null;
        }

        // Creates a declaration context which contains the refined methods from the given refinement
        private DeclarationContext createDeclarationContextWithRefinement(RubyModule refinement) {
            final Map<RubyModule, RubyModule[]> refinements = new HashMap<>();
            refinements.put(refinement.fields.getRefinedModule(), new RubyModule[]{ refinement });
            return new DeclarationContext(
                    Visibility.PUBLIC,
                    new FixedDefaultDefinee(refinement),
                    refinements);
        }

        private InternalMethod cloneMethod(InternalMethod method, DeclarationContext declarationContext,
                RubyModule refinement) {
            var clonedCallTarget = cloneCallTarget(method);
            return method.withCallTargetAndDeclarationContextAndDeclarationModule(clonedCallTarget, declarationContext,
                    refinement);
        }

        private RootCallTarget cloneCallTarget(InternalMethod method) {
            var rubyRootNode = (RubyRootNode) method.getCallTarget().getRootNode();
            var clonedRootNode = rubyRootNode.cloneUninitialized();

            return clonedRootNode.getCallTarget();
        }
    }

    @CoreMethod(names = "target")
    public abstract static class TargetNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubyModule target(RubyModule refinement) {
            return refinement.fields.getRefinedModule();
        }
    }
}
