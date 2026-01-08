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
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.parser.YARPTranslatorDriver;

import static org.truffleruby.language.RubyBaseNode.nil;

public final class RubyInlineParsingRequestNode extends ExecutableNode {

    private final RubyContext context;
    private final InternalMethod method;

    @Child private DirectCallNode callNode;

    public RubyInlineParsingRequestNode(
            RubyLanguage language,
            RubyContext context,
            Source source,
            MaterializedFrame parentFrame) {
        super(language);
        this.context = context;
        var parentMethod = RubyArguments.getMethod(parentFrame);

        final RubySource rubySource = new RubySource(source, language.getSourcePath(source), null, true, 0);

        // We use the current frame as the lexical scope to parse, but then we may run with a new frame in the future
        final YARPTranslatorDriver translator = new YARPTranslatorDriver(context);
        final RootCallTarget callTarget = translator.parse(
                rubySource,
                ParserContext.INLINE,
                null,
                parentFrame.getFrameDescriptor(),
                parentMethod.getLexicalScope(),
                null);

        callNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
        callNode.forceInlining();

        Object[] frameArguments = context.getCodeLoader().prepareArgs(callTarget, ParserContext.INLINE,
                DeclarationContext.topLevel(context), parentFrame, nil, parentMethod.getLexicalScope(),
                RubyBaseNode.EMPTY_ARGUMENTS);
        this.method = RubyArguments.getMethod(frameArguments);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert RubyLanguage.getCurrentContext() == context;

        final Object[] arguments = RubyArguments.pack(
                frame.materialize(),
                null,
                this.method,
                null,
                RubyArguments.getSelf(frame),
                RubyArguments.getBlock(frame),
                NoKeywordArgumentsDescriptor.INSTANCE,
                RubyBaseNode.EMPTY_ARGUMENTS);

        // No need to share the returned value here, InlineParsingRequest is not exposed to the Context API
        // and is only used by instruments (e.g., the debugger) or the RubyLanguage itself.
        return callNode.call(arguments);
    }

}
