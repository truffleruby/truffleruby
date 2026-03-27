/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.exception;

import org.truffleruby.annotations.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.annotations.CoreModule;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.annotations.Visibility;
import org.truffleruby.language.objects.AllocationTracing;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;

@CoreModule(value = "SyntaxError", isClass = true)
public abstract class SyntaxErrorNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        RubySyntaxError allocateSyntaxError(RubyClass rubyClass) {
            final Shape shape = getLanguage().syntaxErrorShape;
            final RubySyntaxError instance = new RubySyntaxError(rubyClass, shape, nil, null, nil, null);
            AllocationTracing.trace(instance, this);
            return instance;
        }

    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        Object path(RubySyntaxError syntaxError) {
            if (!syntaxError.hasSourceLocation()) {
                return nil;
            }

            Source source;
            try {
                source = syntaxError.getSourceLocation().getSource();
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }

            var path = getLanguage().getPathToTStringCache().getCachedPath(source);
            return createString(path, Encodings.UTF_8);
        }

    }

}
