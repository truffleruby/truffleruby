/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.loader;

import com.oracle.truffle.api.source.Source;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.ParsingParameters;
import org.truffleruby.parser.RubySource;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import org.truffleruby.parser.YARPTranslatorDriver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CodeLoader {

    private final RubyLanguage language;
    private final RubyContext context;

    private final Set<String> alreadyLoadedInContext = ConcurrentHashMap.newKeySet();

    public CodeLoader(RubyLanguage language, RubyContext context) {
        this.language = language;
        this.context = context;
    }

    @TruffleBoundary
    public RootCallTarget parseTopLevelWithCache(RubySource rubySource, Node currentNode) {
        final Source source = rubySource.getSource();
        final String path = RubyLanguage.getPath(source);

        if (language.singleContext && !alreadyLoadedInContext.add(language.getPathRelativeToHome(path))) {
            /* Duplicate load of the same file in the same context, we cannot use the cache because it would re-assign
             * the live modules of static LexicalScopes and we cannot/do not want to invalidate static LexicalScopes, so
             * there the static lexical scope and its module are constants and need no checks in single context (e.g.,
             * in LookupConstantWithLexicalScopeNode). */
            return parse(rubySource, ParserContext.TOP_LEVEL, null, context.getRootLexicalScope(), currentNode);
        }

        language.parsingRequestParams.set(new ParsingParameters(currentNode, rubySource));
        try {
            return (RootCallTarget) context.getEnv().parseInternal(source);
        } finally {
            language.parsingRequestParams.set(null);
        }
    }

    @TruffleBoundary
    public RootCallTarget parse(RubySource source,
            ParserContext parserContext,
            MaterializedFrame parentFrame,
            LexicalScope lexicalScope,
            Node currentNode) {
        final YARPTranslatorDriver translator = new YARPTranslatorDriver(context);
        return translator.parse(source, parserContext, null, parentFrame, lexicalScope, currentNode);
    }

    @TruffleBoundary
    public DeferredCall prepareExecute(RootCallTarget callTarget,
            ParserContext parserContext,
            DeclarationContext declarationContext,
            MaterializedFrame parentFrame,
            Object self,
            LexicalScope lexicalScope) {
        return prepareExecute(callTarget, parserContext, declarationContext, parentFrame, self, lexicalScope,
                RubyNode.EMPTY_ARGUMENTS);
    }

    @TruffleBoundary
    public DeferredCall prepareExecute(RootCallTarget callTarget,
            ParserContext parserContext,
            DeclarationContext declarationContext,
            MaterializedFrame parentFrame,
            Object self,
            LexicalScope lexicalScope,
            Object[] arguments) {
        Object[] frameArguments = prepareArgs(callTarget, parserContext, declarationContext, parentFrame, self,
                lexicalScope, arguments);
        return new DeferredCall(callTarget, frameArguments);
    }

    public Object[] prepareArgs(RootCallTarget callTarget, ParserContext parserContext,
            DeclarationContext declarationContext, MaterializedFrame parentFrame, Object self,
            LexicalScope lexicalScope, Object[] arguments) {
        final RubyRootNode rootNode = RubyRootNode.of(callTarget);
        final InternalMethod parentMethod = parentFrame == null ? null : RubyArguments.getMethod(parentFrame);

        final RubyModule declaringModule;
        if ((parserContext == ParserContext.EVAL || parserContext == ParserContext.INSTANCE_EVAL) &&
                parentFrame != null) {
            declaringModule = parentMethod.getDeclaringModule();
        } else if (parserContext == ParserContext.MODULE) {
            declaringModule = (RubyModule) self;
        } else {
            declaringModule = context.getCoreLibrary().objectClass;
        }

        final SharedMethodInfo sharedMethodInfo = rootNode.getSharedMethodInfo();

        final InternalMethod method = new InternalMethod(
                context,
                sharedMethodInfo,
                lexicalScope,
                declarationContext,
                sharedMethodInfo.getMethodName(),
                declaringModule,
                Visibility.PUBLIC,
                callTarget);

        return RubyArguments.pack(
                parentFrame,
                null,
                method,
                null,
                self,
                Nil.INSTANCE,
                NoKeywordArgumentsDescriptor.INSTANCE,
                arguments);
    }

    public static final class DeferredCall {

        private final RootCallTarget callTarget;
        private final Object[] arguments;

        public DeferredCall(RootCallTarget callTarget, Object[] arguments) {
            this.callTarget = callTarget;
            this.arguments = arguments;
        }

        public Object call(IndirectCallNode callNode) {
            return callNode.call(callTarget, arguments);
        }

        public Object callWithoutCallNode() {
            return callTarget.call(arguments);
        }

    }

}
