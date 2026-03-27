/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2015-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Contains code modified from JRuby's org.jruby.runtime.Helpers and org.jruby.runtime.ArgumentType.
 */
package org.truffleruby.language.arguments;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.parser.ArgumentDescriptor;
import org.truffleruby.parser.ArgumentType;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ArgumentDescriptorUtils {

    @TruffleBoundary
    public static RubyArray argumentDescriptorsToParameters(RubyLanguage language, RubyContext context,
            ArgumentDescriptor[] argsDesc, boolean isLambda) {
        final Object[] params = new Object[argsDesc.length];
        for (int i = 0; i < argsDesc.length; i++) {
            params[i] = toArray(language, context, argsDesc[i], isLambda);
        }
        return ArrayHelpers.createArray(context, language, params);
    }

    private static RubyArray toArray(RubyLanguage language, RubyContext context, ArgumentDescriptor argDesc,
            boolean isLambda) {
        if ((argDesc.type == ArgumentType.req) && !isLambda) {
            return toArray(language, context, ArgumentType.opt, argDesc.name);
        }

        return toArray(language, context, argDesc.type, argDesc.name);
    }

    private static RubyArray toArray(RubyLanguage language, RubyContext context, ArgumentType argType, String name) {
        final Object[] store;
        final RubySymbol typeSymbol = language.getSymbol(argType.symbolicName);

        if (argType == ArgumentType.anonrest) {
            store = new Object[]{ typeSymbol, language.coreSymbols.MULTIPLY };
        } else if (argType == ArgumentType.anonkeyrest) {
            store = new Object[]{ typeSymbol, language.coreSymbols.POW };
        } else if (argType == ArgumentType.anonblock) {
            store = new Object[]{ typeSymbol, language.coreSymbols.AMPERSAND };
        } else if (argType.anonymous || name == null) {
            store = new Object[]{ typeSymbol };
        } else {
            store = new Object[]{ typeSymbol, language.getSymbol(name) };
        }

        return ArrayHelpers.createArray(context, language, store);
    }
}
