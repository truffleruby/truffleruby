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
package org.truffleruby.language;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.encoding.TStringUtils;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.backtrace.InternalRootNode;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.language.dispatch.DispatchNode;

public final class RubyEvalInteractiveRootNode extends RubyBaseRootNode implements InternalRootNode {

    private final TruffleString sourceString;
    @Child DispatchNode callEvalNode = DispatchNode.create();

    public RubyEvalInteractiveRootNode(RubyLanguage language, Source source) {
        super(language, null, null);
        this.sourceString = TStringUtils.utf8TString(source.getCharacters().toString());
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyContext context = getContext();

        // Just do Truffle::Boot::INTERACTIVE_BINDING.eval(code) for interactive sources.
        // It's the semantics we want and takes care of caching correctly based on the Binding's FrameDescriptor.
        final RubyBinding interactiveBinding = context.getCoreLibrary().interactiveBinding;
        return callEvalNode.call(
                interactiveBinding,
                "eval",
                StringOperations.createUTF8String(context, getLanguage(), sourceString));
    }

    @Override
    public String getName() {
        return "Context#eval(interactiveRubySource)";
    }
}
