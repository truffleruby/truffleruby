/*
 * Copyright (c) 2013, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.debug;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.Nil;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.NoKeywordArgumentsDescriptor;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.parser.FrameDescriptorInfo;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.source.Source;

public abstract class DebugHelpers {

    public static Object eval(String code, Object... arguments) {
        return eval(RubyLanguage.getCurrentContext(), code, arguments);
    }

    @TruffleBoundary
    public static Object eval(RubyContext context, String code, Object... arguments) {
        final Frame currentFrame = context.getCallStack().getCurrentFrame(FrameAccess.MATERIALIZE);
        final FrameDescriptor currentFrameDescriptor = currentFrame.getFrameDescriptor();
        assert CallStackManager.isRubyFrame(currentFrame);

        if (arguments.length % 2 == 1) {
            throw CompilerDirectives.shouldNotReachHere("odd number of name-value pairs for arguments");
        }
        final int nArgs = arguments.length / 2;

        final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(currentFrame);
        var currentMethod = RubyArguments.getMethod(currentFrame);

        final LexicalScope lexicalScope = currentMethod.getLexicalScope();
        final Object[] packedArguments = RubyArguments.pack(
                null,
                null,
                currentMethod,
                declarationContext,
                null,
                RubyArguments.getSelf(currentFrame),
                Nil.INSTANCE,
                NoKeywordArgumentsDescriptor.INSTANCE,
                RubyNode.EMPTY_ARGUMENTS);


        var builder = TranslatorEnvironment.newFrameDescriptorBuilderForBlock(
                new FrameDescriptorInfo(currentFrameDescriptor, currentMethod.getSharedMethodInfo()));

        for (int i = 0; i < nArgs; i++) {
            final Object identifier = arguments[i * 2];
            assert !(identifier == null || (identifier instanceof String && ((String) identifier).isEmpty()));
            int slot = builder.addSlot(FrameSlotKind.Object, identifier, null);
            assert slot == i;
        }

        final FrameDescriptor frameDescriptor = builder.build();

        var evalFrame = Truffle.getRuntime().createMaterializedFrame(packedArguments, frameDescriptor);
        for (int i = 0; i < nArgs; i++) {
            evalFrame.setObject(i, arguments[i * 2 + 1]);
        }

        final Source source = Source.newBuilder(TruffleRuby.LANGUAGE_ID, code, "debug-eval").build();

        final RootCallTarget callTarget = context
                .getCodeLoader()
                .parse(new RubySource(source, "debug-eval"), ParserContext.INLINE, evalFrame, lexicalScope, null);

        final CodeLoader.DeferredCall deferredCall = context.getCodeLoader().prepareExecute(
                callTarget,
                ParserContext.INLINE,
                declarationContext,
                evalFrame,
                RubyArguments.getSelf(evalFrame),
                lexicalScope);

        return deferredCall.callWithoutCallNode();
    }

}
