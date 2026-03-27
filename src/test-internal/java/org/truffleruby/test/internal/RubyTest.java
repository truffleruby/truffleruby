/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2016-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.function.Consumer;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.shared.TruffleRuby;
import org.truffleruby.shared.options.OptionsCatalog;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;

public abstract class RubyTest {

    public static void assertThrows(Runnable test, Consumer<PolyglotException> exceptionVerifier) {
        PolyglotException e = assertThrows(test, PolyglotException.class);
        assertTrue(e.isGuestException());
        exceptionVerifier.accept(e);
    }

    public static <E> E assertThrows(Runnable test, Class<E> exceptionClass) {
        try {
            test.run();
        } catch (Throwable e) {
            if (!exceptionClass.isInstance(e)) {
                throw e;
            }
            return exceptionClass.cast(e);
        }

        fail("should have thrown");
        throw new Error("unreachable");
    }

    public static <T extends Node> T adopt(T node) {
        RootNode root = new RootNode(null) {
            @Child Node childNode;
            {
                childNode = insert(node);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        root.adoptChildren();
        return node;
    }

    protected <T extends Node> void testWithNode(String text, Class<T> nodeClass, Consumer<T> test) {
        testWithAST(text, (root) -> {
            final List<T> instances = NodeUtil.findAllNodeInstances(root, nodeClass);
            assertEquals(1, instances.size());
            final T node = instances.get(0);
            test.accept(node);
        });
    }

    protected void testWithAST(String text, Consumer<RubyRootNode> test) {
        final Source source = Source.newBuilder(TruffleRuby.LANGUAGE_ID, text, "test.rb").build();

        testInContext(() -> {
            final RootCallTarget callTarget = RubyLanguage.getCurrentContext().getCodeLoader().parse(
                    new RubySource(source, source.getName()),
                    ParserContext.TOP_LEVEL,
                    null,
                    RubyLanguage.getCurrentContext().getRootLexicalScope(),
                    null);
            test.accept(RubyRootNode.of(callTarget));
        });
    }

    protected void testInContext(Runnable test) {
        try (Context context = createContext()) {
            context.eval(org.graalvm.polyglot.Source.create(TruffleRuby.LANGUAGE_ID, "-> test { test.call }")).execute(
                    test);
        }
    }

    public static Context.Builder setupContext(Context.Builder builder) {
        return builder
                .allowAllAccess(true)
                .option(OptionsCatalog.BASICOPS_INLINE.getName(), "false");
    }

    public static Context createContext() {
        return setupContext(Context.newBuilder()).build();
    }

}
