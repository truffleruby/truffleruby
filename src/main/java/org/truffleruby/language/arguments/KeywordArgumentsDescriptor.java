/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.truffleruby.core.string.StringUtils;


/** An arguments descriptor that says that keyword arguments were passed (either foo(a: 1) or foo(**kw)). Note that
 * currently, if kw is empty, then this descriptor is used even though it is semantically the same as if no keyword
 * arguments were passed. The callee must handle that for now. */
public final class KeywordArgumentsDescriptor extends ArgumentsDescriptor {

    /** Used to mean there are keyword arguments, but they are unknown (e.g. foo(**kw)). */
    public static final KeywordArgumentsDescriptor EMPTY = new KeywordArgumentsDescriptor(
            StringUtils.EMPTY_STRING_ARRAY);

    @CompilationFinal(dimensions = 1) private final String[] keywords;

    /** Use {@link KeywordArgumentsDescriptorManager} to get an instance. */
    KeywordArgumentsDescriptor(String[] keywords) {
        this.keywords = keywords;
    }

    public String[] getKeywords() {
        return keywords;
    }

    @Override
    public String toString() {
        return StringUtils.format("KeywordArgumentsDescriptor(keywords = [%s])", StringUtils.join(keywords, ", "));
    }
}
