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
package org.truffleruby.core.string;

import org.truffleruby.RubyLanguage;

public final class CoreStrings {

    public final CoreString ARGUMENT_OUT_OF_RANGE;
    public final CoreString ASSIGNMENT;
    public final CoreString CALL;
    public final CoreString CANT_COMPRESS_NEGATIVE;
    public final CoreString CLASS;
    public final CoreString EVAL_FILENAME_STRING;
    public final CoreString FAILED_TO_ALLOCATE_MEMORY;
    public final CoreString LINE;
    public final CoreString NEGATIVE_ARRAY_SIZE;
    public final CoreString NEGATIVE_STRING_SIZE;
    public final CoreString ONE_HASH_REQUIRED;
    public final CoreString PROC_WITHOUT_BLOCK;
    public final CoreString REPLACEMENT_CHARACTER_SETUP_FAILED;
    public final CoreString STACK_LEVEL_TOO_DEEP;
    public final CoreString TIME_INTERVAL_MUST_BE_POS;
    public final CoreString TOO_FEW_ARGUMENTS;
    public final CoreString UNKNOWN;
    public final CoreString UTC;
    public final CoreString WRONG_ARGS_ZERO_PLUS_ONE;
    public final CoreString X_OUTSIDE_OF_STRING;

    public CoreStrings(RubyLanguage language) {
        ARGUMENT_OUT_OF_RANGE = new CoreString(language, "argument out of range");
        ASSIGNMENT = new CoreString(language, "assignment");
        CALL = new CoreString(language, "call");
        CANT_COMPRESS_NEGATIVE = new CoreString(language, "can't compress negative numbers");
        CLASS = new CoreString(language, "class");
        EVAL_FILENAME_STRING = new CoreString(language, "(eval)");
        FAILED_TO_ALLOCATE_MEMORY = new CoreString(language, "failed to allocate memory");
        LINE = new CoreString(language, "line");
        NEGATIVE_ARRAY_SIZE = new CoreString(language, "negative array size");
        NEGATIVE_STRING_SIZE = new CoreString(language, "negative string size (or size too big)");
        ONE_HASH_REQUIRED = new CoreString(language, "one hash required");
        PROC_WITHOUT_BLOCK = new CoreString(language, "tried to create Proc object without a block");
        REPLACEMENT_CHARACTER_SETUP_FAILED = new CoreString(language, "replacement character setup failed");
        STACK_LEVEL_TOO_DEEP = new CoreString(language, "stack level too deep");
        TIME_INTERVAL_MUST_BE_POS = new CoreString(language, "time interval must be positive");
        TOO_FEW_ARGUMENTS = new CoreString(language, "too few arguments");
        UNKNOWN = new CoreString(language, "(unknown)");
        UTC = new CoreString(language, "UTC");
        WRONG_ARGS_ZERO_PLUS_ONE = new CoreString(language, "wrong number of arguments (0 for 1+)");
        X_OUTSIDE_OF_STRING = new CoreString(language, "X outside of string");
    }

}
