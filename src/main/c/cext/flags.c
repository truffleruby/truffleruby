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
  return rb_tr_up_rb_tr_flags(object);
}

void rb_tr_set_flags(VALUE object, unsigned long flags) {
  rb_tr_up_rb_tr_set_flags(object, flags);
}
