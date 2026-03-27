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
package org.truffleruby.core.inlined;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.language.methods.InternalMethod;

public final class CoreMethods {

    public final InternalMethod EXCEPTION_BACKTRACE;
    public final InternalMethod LAMBDA;
    public final InternalMethod NOT;
    public final InternalMethod KERNEL_IS_NIL;
    public final InternalMethod KERNEL_IS_A;
    public final InternalMethod KERNEL_KIND_OF;
    public final InternalMethod STRING_BYTESIZE;
    public final InternalMethod MODULE_CASE_EQUAL;
    public final InternalMethod SYMBOL_TO_PROC;
    public final InternalMethod ARRAY_AT;
    public final InternalMethod ARRAY_INDEX_GET;
    public final InternalMethod ARRAY_INDEX_SET;

    public CoreMethods(RubyLanguage language, RubyContext context) {
        final RubyClass basicObjectClass = context.getCoreLibrary().basicObjectClass;
        final RubyClass exceptionClass = context.getCoreLibrary().exceptionClass;
        final RubyModule kernelModule = context.getCoreLibrary().kernelModule;
        final RubyClass moduleClass = context.getCoreLibrary().moduleClass;
        final RubyClass stringClass = context.getCoreLibrary().stringClass;
        final RubyClass symbolClass = context.getCoreLibrary().symbolClass;
        final RubyClass arrayClass = context.getCoreLibrary().arrayClass;

        LAMBDA = getMethod(kernelModule, "lambda");
        NOT = getMethod(basicObjectClass, "!");
        EXCEPTION_BACKTRACE = getMethod(exceptionClass, "backtrace");
        KERNEL_IS_NIL = getMethod(kernelModule, "nil?");
        STRING_BYTESIZE = getMethod(stringClass, "bytesize");
        KERNEL_IS_A = getMethod(kernelModule, "is_a?");
        KERNEL_KIND_OF = getMethod(kernelModule, "kind_of?");
        MODULE_CASE_EQUAL = getMethod(moduleClass, "===");
        SYMBOL_TO_PROC = getMethod(symbolClass, "to_proc");
        ARRAY_AT = getMethod(arrayClass, "at");
        ARRAY_INDEX_GET = getMethod(arrayClass, "[]");
        ARRAY_INDEX_SET = getMethod(arrayClass, "[]=");
    }

    private InternalMethod getMethod(RubyModule module, String name) {
        final InternalMethod method = module.fields.getMethod(name);
        if (method == null || method.isUndefined()) {
            throw new AssertionError();
        }
        return method;
    }

}
