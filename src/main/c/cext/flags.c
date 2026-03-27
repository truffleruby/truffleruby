/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2023-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>

// struct RBasic flags, RB_FL_*

unsigned long rb_tr_flags(VALUE object) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_tr_flags", object));
}

void rb_tr_set_flags(VALUE object, unsigned long flags) {
  polyglot_invoke(RUBY_CEXT, "rb_tr_set_flags", rb_tr_unwrap(object), flags);
}
