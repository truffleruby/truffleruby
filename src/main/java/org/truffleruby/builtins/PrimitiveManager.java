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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.truffleruby.annotations.Primitive;
import org.truffleruby.collections.ConcurrentOperations;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.NodeFactory;
import org.truffleruby.options.LanguageOptions;

/** Manages the available primitive calls. */
public final class PrimitiveManager {

    private final Map<String, String> lazyPrimitiveClasses = new ConcurrentHashMap<>();

    private final Map<String, PrimitiveNodeConstructor> primitives = new ConcurrentHashMap<>();

    public PrimitiveNodeConstructor getPrimitive(String name) {
        final PrimitiveNodeConstructor constructor = primitives.get(name);
        if (constructor != null) {
            return constructor;
        }

        if (!TruffleOptions.AOT) {
            final String lazyPrimitive = lazyPrimitiveClasses.get(name);
            if (lazyPrimitive != null) {
                return loadLazyPrimitive(lazyPrimitive);
            }
        }

        throw new Error("Primitive." + name + " not found");
    }

    public void addLazyPrimitive(String primitive, String nodeFactoryClass) {
        lazyPrimitiveClasses.put(primitive, nodeFactoryClass);
    }

    private PrimitiveNodeConstructor loadLazyPrimitive(String lazyPrimitive) {
        final NodeFactory<? extends RubyBaseNode> nodeFactory = CoreMethodNodeManager.loadNodeFactory(lazyPrimitive);
        final Primitive annotation = nodeFactory.getNodeClass().getAnnotation(Primitive.class);
        return addPrimitive(nodeFactory, annotation);
    }

    private PrimitiveNodeConstructor addPrimitive(NodeFactory<? extends RubyBaseNode> nodeFactory,
            Primitive annotation) {
        return ConcurrentOperations.getOrCompute(
                primitives,
                annotation.name(),
                k -> new PrimitiveNodeConstructor(annotation, nodeFactory));
    }

    public void loadCoreMethodNodes(LanguageOptions languageOptions) {
        if (!TruffleOptions.AOT && languageOptions.LAZY_BUILTINS) {
            BuiltinsClasses.setupBuiltinsLazyPrimitives(this);
        } else {
            for (List<? extends NodeFactory<? extends RubyBaseNode>> factory : BuiltinsClasses.getCoreNodeFactories()) {
                registerPrimitives(factory);
            }
        }
    }

    private void registerPrimitives(List<? extends NodeFactory<? extends RubyBaseNode>> nodeFactories) {
        for (NodeFactory<? extends RubyBaseNode> nodeFactory : nodeFactories) {
            final Class<?> nodeClass = nodeFactory.getNodeClass();
            final Primitive primitiveAnnotation = nodeClass.getAnnotation(Primitive.class);
            if (primitiveAnnotation != null) {
                addPrimitive(nodeFactory, primitiveAnnotation);
            }
        }
    }

    public Set<String> getPrimitiveNames() {
        var allPrimitives = new HashSet<>(primitives.keySet());
        allPrimitives.addAll(lazyPrimitiveClasses.keySet());
        return allPrimitives;
    }

}
