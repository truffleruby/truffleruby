/*
 * Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.InternalRootNode;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.parser.YARPTranslatorDriver;
import org.truffleruby.shared.Metrics;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.Source;

public final class RubyParsingRequestNode extends RubyBaseRootNode implements InternalRootNode {

    private final RootCallTarget callTarget;

    @Child private DirectCallNode callNode;

    public RubyParsingRequestNode(RubyLanguage language, RubyContext context, Source source, String[] argumentNames) {
        super(language, null, null);

        final RubySource rubySource = new RubySource(source, language.getSourcePath(source));

        final YARPTranslatorDriver translator = new YARPTranslatorDriver(context);
        callTarget = translator.parse(
                rubySource,
                ParserContext.TOP_LEVEL,
                argumentNames,
                null,
                context.getRootLexicalScope(), // This is a top-level parse, so the lexical scope is always the root one
                null);

        callNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
        callNode.forceInlining();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyLanguage language = getLanguage();
        final RubyContext context = getContext();

        final SharedMethodInfo sharedMethodInfo = RubyRootNode.of(callTarget).getSharedMethodInfo();
        assert sharedMethodInfo.getStaticLexicalScopeOrNull() == context.getRootLexicalScope() ||
                sharedMethodInfo.getStaticLexicalScopeOrNull() == null;

        final InternalMethod method = new InternalMethod(
                context,
                sharedMethodInfo,
                context.getRootLexicalScope(), // This is a top-level parse, so the lexical scope is always the root one
                DeclarationContext.topLevel(context),
                sharedMethodInfo.getMethodName(),
                context.getCoreLibrary().objectClass,
                Visibility.PUBLIC,
                callTarget);

        printTimeMetric("before-script");
        try {
            final Object value = callNode.call(RubyArguments.pack(
                    null,
                    null,
                    method,
                    null,
                    context.getCoreLibrary().mainObject,
                    Nil.INSTANCE,
                    NoKeywordArgumentsDescriptor.INSTANCE,
                    frame.getArguments()));

            // The return value will be leaked to Java, so share it if the Context API is used.
            // We share conditionally on EMBEDDED to avoid sharing return values used in RubyLauncher.
            if (language.options.SHARED_OBJECTS_ENABLED && context.getOptions().EMBEDDED) {
                SharedObjects.writeBarrier(language, value);
            }

            return value;
        } finally {
            printTimeMetric("after-script");
        }
    }

    @Override
    public String getName() {
        return "parsing-request";
    }

    @TruffleBoundary
    private void printTimeMetric(String id) {
        Metrics.printTime(id);
    }

}
