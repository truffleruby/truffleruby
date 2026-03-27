/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include "ruby.h"
#include "rubyspec.h"

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

static VALUE truffleruby_spec_truffleruby(VALUE self) {
  #ifdef TRUFFLERUBY
    return Qtrue;
  #else
    return Qfalse;
  #endif
}

void Init_truffleruby_spec(void) {
  VALUE cls;
  cls = rb_define_class("CApiTruffleRubySpecs", rb_cObject);
  rb_define_method(cls, "truffleruby", truffleruby_spec_truffleruby, 0);
}

#ifdef __cplusplus
}
#endif
