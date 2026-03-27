/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.builtins;

import org.truffleruby.annotations.Primitive;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.numeric.FixnumLowerNodeGen.FixnumLowerASTNodeGen;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.NodeFactory;

public final class PrimitiveNodeConstructor {

    private final Primitive annotation;
    private final NodeFactory<? extends RubyBaseNode> factory;

    public PrimitiveNodeConstructor(Primitive annotation, NodeFactory<? extends RubyBaseNode> factory) {
        this.annotation = annotation;
        this.factory = factory;
    }

    public boolean isPublic() {
        return annotation.isPublic();
    }

    public int getPrimitiveArity() {
        return factory.getExecutionSignature().size();
    }

    public NodeFactory<? extends RubyBaseNode> getFactory() {
        return factory;
    }

    public RubyNode createInvokePrimitiveNode(RubyNode[] arguments) {
        for (int n = 0; n < arguments.length; n++) {
            if (ArrayUtils.contains(annotation.lowerFixnum(), n)) {
                arguments[n] = FixnumLowerASTNodeGen.create(arguments[n]);
            }
            if (ArrayUtils.contains(annotation.raiseIfFrozen(), n)) {
                arguments[n] = TypeNodes.TypeCheckFrozenNode.create(arguments[n]);
            }
            if (ArrayUtils.contains(annotation.raiseIfNotMutable(), n)) {
                arguments[n] = TypeNodes.CheckMutableStringNode.create(arguments[n]);
            }
        }

        return (RubyNode) CoreMethodNodeManager.createNodeFromFactory(factory, arguments);
    }

}
