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

#ifdef __cplusplus
extern "C" {
#endif

static VALUE unimplemented_spec_not_implemented(VALUE self, VALUE str) {
  // One of the functions not implemented
  rb_str_shared_replace(str, str);
  return Qnil;
}

void Init_unimplemented_spec(void) {
  VALUE cls;
  cls = rb_define_class("CApiRbTrErrorSpecs", rb_cObject);
  rb_define_method(cls, "not_implemented_function", unimplemented_spec_not_implemented, 1);
}

#ifdef __cplusplus
}
#endif
